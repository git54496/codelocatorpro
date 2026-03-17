package com.bytedance.tools.codelocator.adapter

import com.google.gson.JsonObject
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CountDownLatch

class ViewerManager(
    private val store: GrabStore,
    private val adb: AdbGateway
) {
    companion object {
        @Volatile
        private var embeddedPort: Int? = null
        private val lock = Any()
    }

    fun open(grabId: String?): ViewerOpenResult {
        val (port, pid) = ensureServerProcess()
        val url = if (grabId.isNullOrBlank()) {
            "http://127.0.0.1:$port/"
        } else {
            "http://127.0.0.1:$port/?grab_id=$grabId"
        }
        runCatching { ProcessBuilder("open", url).start() }
        return ViewerOpenResult(url = url, port = port, pid = pid, grabId = grabId)
    }

    fun serve(port: Int) {
        val server = ViewerHttpServer(store, adb, port)
        server.start()
    }

    private fun ensureServerProcess(): Pair<Int, Long?> {
        Files.createDirectories(Constants.viewerRoot)

        embeddedPort?.let { if (isUsable(it)) return it to ProcessHandle.current().pid() }

        val existing = readViewerState()
        if (existing != null) {
            if (isUsable(existing.first)) {
                embeddedPort = existing.first
                return existing.first to ProcessHandle.current().pid()
            }
            // Best effort cleanup for stale/broken viewer process recorded in state file.
            runCatching { ProcessHandle.of(existing.second).orElse(null)?.destroy() }
        }

        synchronized(lock) {
            embeddedPort?.let { if (isUsable(it)) return it to ProcessHandle.current().pid() }

            val port = freePort()
            val thread = Thread(
                {
                    ViewerHttpServer(store, adb, port).start()
                },
                "codelocator-viewer-$port"
            )
            thread.isDaemon = false
            thread.start()

            val ready = waitServerReady(port, Duration.ofSeconds(4))
            if (!ready) {
                throw AdapterException("TIMEOUT", "viewer server failed to start")
            }

            embeddedPort = port
            val pid = ProcessHandle.current().pid()
            writeViewerState(port, pid)
            return port to pid
        }
    }

    private fun readViewerState(): Pair<Int, Long>? {
        val file = Constants.viewerStateFile.toFile()
        if (!file.exists()) return null
        return runCatching {
            val obj = Jsons.readJsonObject(file)
            obj.get("port")?.asInt to obj.get("pid")?.asLong
        }.mapCatching { pair ->
            if (pair.first == null || pair.second == null) throw IllegalStateException("invalid")
            pair.first!! to pair.second!!
        }.getOrNull()
    }

    private fun writeViewerState(port: Int, pid: Long) {
        val obj = JsonObject().apply {
            addProperty("port", port)
            addProperty("pid", pid)
        }
        Files.writeString(Constants.viewerStateFile, Jsons.toJson(obj))
    }

    private fun isAlive(port: Int): Boolean {
        return runCatching {
            val conn = URL("http://127.0.0.1:$port/api/health").openConnection() as HttpURLConnection
            conn.connectTimeout = 600
            conn.readTimeout = 600
            conn.requestMethod = "GET"
            conn.responseCode == 200
        }.getOrDefault(false)
    }

    private fun isUsable(port: Int): Boolean {
        if (!isAlive(port)) return false
        return runCatching {
            val conn = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return@runCatching false
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            text.contains("<!doctype html>", ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun waitServerReady(port: Int, timeout: Duration): Boolean {
        val end = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < end) {
            if (isAlive(port)) return true
            Thread.sleep(120)
        }
        return false
    }

    private fun freePort(): Int {
        java.net.ServerSocket(0).use { return it.localPort }
    }

}

class ViewerHttpServer(
    private val store: GrabStore,
    private val adb: AdbGateway,
    private val port: Int
) {
    fun start() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { exchange -> handle(exchange) }
        server.executor = null
        server.start()
        println("Viewer started at http://127.0.0.1:$port")
        CountDownLatch(1).await()
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        try {
            when {
                path == "/api/health" -> respondJson(exchange, 200, "{\"ok\":true}")
                path == "/api/grabs" -> respondJson(exchange, 200, Jsons.toJson(store.listGrabs()))
                path == "/api/grab-live" -> {
                    if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                        respondText(exchange, 405, "method not allowed")
                        return
                    }
                    val reqBody = exchange.requestBody.bufferedReader().use { it.readText() }.trim()
                    val deviceSerial = if (reqBody.isBlank()) {
                        null
                    } else {
                        runCatching { Jsons.parseObject(reqBody).get("deviceSerial")?.asString }.getOrNull()
                    }
                    val result = runCatching { adb.grabUiState(deviceSerial) }
                        .getOrElse { t ->
                            val ex = t as? AdapterException
                            ToolResult<Any>(
                                success = false,
                                error = McpError(
                                    ex?.code ?: "INTERNAL_ERROR",
                                    ex?.message ?: (t.message ?: "grab failed"),
                                    ex?.details ?: emptyMap()
                                )
                            )
                        }
                    respondJson(exchange, 200, Jsons.toJson(result))
                }
                path == "/api/grab-file-latest" -> {
                    if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                        respondText(exchange, 405, "method not allowed")
                        return
                    }
                    val latest = store.latestHistoryFile()
                    val result = if (latest == null) {
                        ToolResult<Any>(
                            success = false,
                            error = McpError("INVALID_ARGUMENT", "No .codeLocator file found in ~/.codeLocator_main/historyFile")
                        )
                    } else {
                        runCatching { store.importFromCodeLocatorFile(latest.absolutePath) }
                            .getOrElse { t ->
                                val ex = t as? AdapterException
                                ToolResult<Any>(
                                    success = false,
                                    error = McpError(
                                        ex?.code ?: "INTERNAL_ERROR",
                                        ex?.message ?: (t.message ?: "grab file failed"),
                                        ex?.details ?: emptyMap()
                                    )
                                )
                            }
                    }
                    respondJson(exchange, 200, Jsons.toJson(result))
                }
                path.startsWith("/api/grab/") && path.endsWith("/snapshot") -> {
                    val grabId = path.removePrefix("/api/grab/").removeSuffix("/snapshot").trim('/').trim()
                    val snapshot = store.loadSnapshot(grabId)
                    respondJson(exchange, 200, Jsons.toJson(snapshot))
                }
                path.startsWith("/api/grab/") && path.endsWith("/paths") -> {
                    val grabId = path.removePrefix("/api/grab/").removeSuffix("/paths").trim('/').trim()
                    if (grabId.isBlank() || !grabId.matches(Regex("[A-Za-z0-9._-]+"))) {
                        respondText(exchange, 400, "invalid grab_id")
                        return
                    }
                    val grabDir = store.grabPath(grabId).toAbsolutePath().normalize()
                    if (!Files.isDirectory(grabDir)) {
                        respondText(exchange, 404, "grab_id not found")
                        return
                    }

                    val screenshotPath = grabDir.resolve("screenshot.png")
                    val snapshotPath = grabDir.resolve("snapshot.json")
                    val indexPath = grabDir.resolve("index.json")
                    val composeIndexPath = grabDir.resolve("compose_index.json")
                    val componentIndexPath = grabDir.resolve("component_index.json")
                    val renderIndexPath = grabDir.resolve("render_index.json")
                    val semanticsIndexPath = grabDir.resolve("semantics_index.json")
                    val linkIndexPath = grabDir.resolve("link_index.json")
                    respondJson(
                        exchange,
                        200,
                        Jsons.toJson(
                            mapOf(
                                "grabId" to grabId,
                                "grabDir" to grabDir.toString(),
                                "screenshotPath" to if (Files.exists(screenshotPath)) screenshotPath.toString() else null,
                                "snapshotPath" to if (Files.exists(snapshotPath)) snapshotPath.toString() else null,
                                "indexPath" to if (Files.exists(indexPath)) indexPath.toString() else null,
                                "composeIndexPath" to if (Files.exists(composeIndexPath)) composeIndexPath.toString() else null,
                                "componentIndexPath" to if (Files.exists(componentIndexPath)) componentIndexPath.toString() else null,
                                "renderIndexPath" to if (Files.exists(renderIndexPath)) renderIndexPath.toString() else null,
                                "semanticsIndexPath" to if (Files.exists(semanticsIndexPath)) semanticsIndexPath.toString() else null,
                                "linkIndexPath" to if (Files.exists(linkIndexPath)) linkIndexPath.toString() else null
                            )
                        )
                    )
                }
                path.startsWith("/api/grab/") && path.endsWith("/screenshot") -> {
                    val grabId = path.removePrefix("/api/grab/").removeSuffix("/screenshot").trim('/').trim()
                    val bytes = store.loadScreenshot(grabId)
                    if (bytes == null) {
                        respondText(exchange, 404, "no screenshot")
                    } else {
                        exchange.responseHeaders.add("Content-Type", "image/png")
                        exchange.responseHeaders.add("Cache-Control", "no-store")
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.use { it.write(bytes) }
                    }
                }
                path == "/" || path == "/index.html" -> {
                    val html = ResourceLoader.readText("index.html")
                    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                    val bytes = html.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                else -> respondText(exchange, 404, "not found")
            }
        } catch (t: Throwable) {
            respondJson(
                exchange,
                500,
                Jsons.toJson(mapOf("error" to (t.message ?: "unknown")))
            )
        }
    }

    private fun respondJson(exchange: HttpExchange, code: Int, json: String) {
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        val bytes = json.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respondText(exchange: HttpExchange, code: Int, text: String) {
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        val bytes = text.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

object ResourceLoader {
    fun readText(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: throw IllegalStateException("resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
