package com.bytedance.tools.codelocator.adapter

import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class GrabStore {
    init {
        Files.createDirectories(Constants.grabsRoot)
        Files.createDirectories(Constants.tempDir.toPath())
        Files.createDirectories(Constants.viewerRoot)
    }

    fun importFromCodeLocatorFile(path: String, deviceNotice: String? = null): ToolResult<GrabMeta> {
        val file = File(path)
        val parsed = CodeLocatorFileParser.parse(file)
        val grabId = generateGrabId()
        val packageName = SnapshotMapper.detectPackage(parsed.appJson)
        val activity = SnapshotMapper.detectActivity(parsed.appJson)
        val meta = GrabMeta(
            grabId = grabId,
            source = "file",
            deviceSerial = null,
            packageName = packageName,
            activity = activity,
            grabTime = System.currentTimeMillis(),
            deviceNotice = deviceNotice
        )
        saveSnapshot(meta, parsed.appJson, parsed.imageBytes)
        return ToolResult(success = true, data = meta, grabId = grabId)
    }

    fun importLive(appJson: String, screenshotBytes: ByteArray?, deviceSerial: String, deviceNotice: String?): ToolResult<GrabMeta> {
        val grabId = generateGrabId()
        val meta = GrabMeta(
            grabId = grabId,
            source = "live",
            deviceSerial = deviceSerial,
            packageName = SnapshotMapper.detectPackage(appJson),
            activity = SnapshotMapper.detectActivity(appJson),
            grabTime = System.currentTimeMillis(),
            deviceNotice = deviceNotice
        )
        saveSnapshot(meta, appJson, screenshotBytes ?: ByteArray(0))
        return ToolResult(success = true, data = meta, grabId = grabId)
    }

    fun listGrabs(): List<GrabMeta> {
        val root = Constants.grabsRoot.toFile()
        if (!root.exists()) return emptyList()
        return root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            val metaFile = File(dir, "meta.json")
            if (!metaFile.exists()) return@mapNotNull null
            runCatching { Jsons.gson.fromJson(metaFile.readText(), GrabMeta::class.java) }.getOrNull()
        }?.sortedByDescending { it.grabTime } ?: emptyList()
    }

    fun latestGrabId(): String? = listGrabs().firstOrNull()?.grabId

    fun latestHistoryFile(): File? {
        val history = Constants.historyDir.toFile()
        if (!history.exists()) return null
        return history.listFiles { f -> f.isFile && f.name.endsWith(".codeLocator") }
            ?.maxByOrNull { it.lastModified() }
    }

    fun loadSnapshot(grabId: String): GrabSnapshot {
        val dir = Constants.grabsRoot.resolve(grabId).toFile()
        val file = File(dir, "snapshot.json")
        if (!file.exists()) {
            throw AdapterException("INVALID_ARGUMENT", "grab_id not found: $grabId")
        }
        return Jsons.gson.fromJson(file.readText(), GrabSnapshot::class.java)
    }

    fun loadScreenshot(grabId: String): ByteArray? {
        val file = Constants.grabsRoot.resolve(grabId).resolve("screenshot.png").toFile()
        if (!file.exists()) return null
        return file.readBytes()
    }

    fun getIndex(grabId: String): Map<String, ViewIndexItem> {
        val file = Constants.grabsRoot.resolve(grabId).resolve("index.json").toFile()
        if (!file.exists()) return emptyMap()
        val root = Jsons.readJsonObject(file)
        val out = linkedMapOf<String, ViewIndexItem>()
        root.entrySet().forEach { (k, v) ->
            if (v.isJsonObject) {
                out[k] = Jsons.gson.fromJson(v, ViewIndexItem::class.java)
            }
        }
        return out
    }

    fun getViewRaw(grabId: String, memAddr: String): Map<String, Any?>? {
        val snapshot = loadSnapshot(grabId)
        return findView(snapshot.uiTree, memAddr)?.raw
    }

    private fun findView(list: List<ViewNodeDto>, memAddr: String): ViewNodeDto? {
        list.forEach { node ->
            if (node.memAddr.equals(memAddr, ignoreCase = true)) return node
            val found = findView(node.children, memAddr)
            if (found != null) return found
        }
        return null
    }

    private fun saveSnapshot(meta: GrabMeta, appJson: String, imageBytes: ByteArray) {
        val dir = Constants.grabsRoot.resolve(meta.grabId)
        Files.createDirectories(dir)

        val screenshotRef = if (imageBytes.isNotEmpty()) "screenshot.png" else null
        if (imageBytes.isNotEmpty()) {
            Files.write(dir.resolve("screenshot.png"), imageBytes)
        }

        val snapshot = SnapshotMapper.map(meta, appJson, screenshotRef)

        Files.writeString(dir.resolve("meta.json"), Jsons.toJson(meta))
        Files.writeString(dir.resolve("snapshot.json"), Jsons.toJson(snapshot))
        Files.writeString(dir.resolve("index.json"), Jsons.toJson(snapshot.indexes))
    }

    private fun generateGrabId(): String {
        val t = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        return "grab_${t}_$suffix"
    }

    fun readState(): JsonObject {
        val file = Constants.stateFile.toFile()
        return if (file.exists()) Jsons.readJsonObject(file) else JsonObject()
    }

    fun updateState(modifier: (JsonObject) -> Unit) {
        val obj = readState()
        modifier(obj)
        Files.createDirectories(Constants.mcpRoot)
        Files.writeString(Constants.stateFile, Jsons.toJson(obj))
    }

    fun grabPath(grabId: String): Path = Constants.grabsRoot.resolve(grabId)
}
