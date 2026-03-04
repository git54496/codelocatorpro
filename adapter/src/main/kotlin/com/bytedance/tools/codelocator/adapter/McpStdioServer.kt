package com.bytedance.tools.codelocator.adapter

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class McpStdioServer(private val service: AdapterService) {

    fun run() {
        val input = BufferedInputStream(System.`in`)
        while (true) {
            val payload = readMessage(input) ?: break
            val response = handle(payload)
            if (response != null) {
                writeMessage(response)
            }
        }
    }

    private fun handle(payload: String): String? {
        return try {
            val req = Jsons.parseObject(payload)
            val method = req.get("method")?.asString
            val id = req.get("id")
            val params = req.getAsJsonObject("params")

            when (method) {
                "initialize" -> response(id, mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to mapOf("tools" to mapOf("listChanged" to false)),
                    "serverInfo" to mapOf("name" to "codelocator-adapter", "version" to "0.1.0")
                ))

                "notifications/initialized" -> null

                "tools/list" -> response(id, mapOf("tools" to tools()))

                "tools/call" -> {
                    val name = params?.get("name")?.asString ?: ""
                    val args = params?.getAsJsonObject("arguments")
                    response(id, callTool(name, args))
                }

                "ping" -> response(id, emptyMap<String, Any>())

                else -> if (method?.startsWith("notifications/") == true) {
                    null
                } else {
                    error(id, -32601, "Method not found: $method")
                }
            }
        } catch (t: Throwable) {
            error(null, -32000, t.message ?: "internal error")
        }
    }

    private fun callTool(name: String, args: JsonObject?): Map<String, Any> {
        fun wrap(result: Any, isError: Boolean): Map<String, Any> {
            return mapOf(
                "content" to listOf(mapOf("type" to "text", "text" to Jsons.toJson(result))),
                "isError" to isError
            )
        }

        return try {
            val result = when (name) {
                Constants.TOOL_GRAB_UI_STATE -> service.grabLive(args?.optString("deviceSerial"))
                Constants.TOOL_LOAD_LOCAL_GRAB -> service.grabFromFile(args?.optString("path"))
                Constants.TOOL_LIST_GRABS -> service.listGrabs()
                Constants.TOOL_OPEN_WEB_VIEWER -> service.openViewer(args?.optString("grab_id"))
                Constants.TOOL_GET_VIEW_DATA -> {
                    val grabId = args.requireString("grab_id")
                    val mem = args.requireString("mem_addr")
                    service.getViewData(grabId, mem)
                }
                Constants.TOOL_GET_VIEW_CLASS_INFO -> {
                    val grabId = args.requireString("grab_id")
                    val mem = args.requireString("mem_addr")
                    service.getViewClassInfo(grabId, mem)
                }
                Constants.TOOL_TRACE_TOUCH -> {
                    val grabId = args.requireString("grab_id")
                    service.traceTouch(grabId)
                }
                else -> ToolResult<Any>(
                    success = false,
                    error = McpError("INVALID_ARGUMENT", "unknown tool: $name")
                )
            }
            wrap(result, !((result as? ToolResult<*>)?.success ?: false))
        } catch (e: AdapterException) {
            wrap(ToolResult<Any>(success = false, error = McpError(e.code, e.message, e.details)), true)
        } catch (t: Throwable) {
            wrap(ToolResult<Any>(success = false, error = McpError("INTERNAL_ERROR", t.message ?: "error")), true)
        }
    }

    private fun tools(): List<Map<String, Any>> {
        return listOf(
            tool(Constants.TOOL_GRAB_UI_STATE, "Grab UI state from live device", mapOf(
                "type" to "object",
                "properties" to mapOf("deviceSerial" to mapOf("type" to "string"))
            )),
            tool(Constants.TOOL_LOAD_LOCAL_GRAB, "Load local .codeLocator file", mapOf(
                "type" to "object",
                "properties" to mapOf("path" to mapOf("type" to "string"))
            )),
            tool(Constants.TOOL_LIST_GRABS, "List grabbed snapshots", mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>()
            )),
            tool(Constants.TOOL_OPEN_WEB_VIEWER, "Open local web viewer", mapOf(
                "type" to "object",
                "properties" to mapOf("grab_id" to mapOf("type" to "string"))
            )),
            tool(Constants.TOOL_GET_VIEW_DATA, "Get view data by mem_addr", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "grab_id" to mapOf("type" to "string"),
                    "mem_addr" to mapOf("type" to "string")
                ),
                "required" to listOf("grab_id", "mem_addr")
            )),
            tool(Constants.TOOL_GET_VIEW_CLASS_INFO, "Get view class info by mem_addr", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "grab_id" to mapOf("type" to "string"),
                    "mem_addr" to mapOf("type" to "string")
                ),
                "required" to listOf("grab_id", "mem_addr")
            )),
            tool(Constants.TOOL_TRACE_TOUCH, "Trace touch chain", mapOf(
                "type" to "object",
                "properties" to mapOf("grab_id" to mapOf("type" to "string")),
                "required" to listOf("grab_id")
            ))
        )
    }

    private fun tool(name: String, description: String, schema: Map<String, Any>): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to schema
        )
    }

    private fun response(id: JsonElement?, result: Any): String {
        return Jsons.toJson(linkedMapOf(
            "jsonrpc" to "2.0",
            "id" to (id?.let { Jsons.elementToAny(it) } ?: 0),
            "result" to result
        ))
    }

    private fun error(id: JsonElement?, code: Int, message: String): String {
        return Jsons.toJson(linkedMapOf(
            "jsonrpc" to "2.0",
            "id" to (id?.let { Jsons.elementToAny(it) } ?: 0),
            "error" to linkedMapOf("code" to code, "message" to message)
        ))
    }

    private fun readMessage(input: BufferedInputStream): String? {
        val firstLine = readAsciiLine(input) ?: return null
        if (firstLine.isBlank()) return null

        if (!firstLine.startsWith("Content-Length:", ignoreCase = true)) {
            return firstLine
        }

        val contentLength = firstLine.substringAfter(':').trim().toInt()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isBlank()) break
        }

        val payload = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(payload, offset, contentLength - offset)
            if (read < 0) break
            offset += read
        }
        return String(payload, 0, offset, StandardCharsets.UTF_8)
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val ch = input.read()
            if (ch < 0) {
                if (buffer.size() == 0) return null
                break
            }
            if (ch == '\n'.code) break
            if (ch != '\r'.code) buffer.write(ch)
        }
        return buffer.toString(StandardCharsets.UTF_8)
    }

    private fun writeMessage(json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.UTF_8)
        synchronized(System.out) {
            System.out.write(header)
            System.out.write(bytes)
            System.out.flush()
        }
    }
}

private fun JsonObject.optString(key: String): String? {
    val ele = this.get(key) ?: return null
    if (ele.isJsonNull) return null
    return ele.asString
}

private fun JsonObject?.requireString(key: String): String {
    val value = this?.optString(key)
    if (value.isNullOrBlank()) {
        throw AdapterException("INVALID_ARGUMENT", "Missing required argument: $key")
    }
    return value
}
