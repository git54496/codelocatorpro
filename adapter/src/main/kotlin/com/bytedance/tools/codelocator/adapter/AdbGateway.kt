package com.bytedance.tools.codelocator.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class AdbGateway(
    private val store: GrabStore,
    private val commandRunner: (List<String>, Long) -> String = ::execProcess
) {

    data class DeviceChoice(
        val serial: String,
        val notice: String?
    )

    fun grabUiState(deviceSerialArg: String?, sourceRoot: String? = null): ToolResult<GrabMeta> {
        val choice = chooseDevice(deviceSerialArg)
        val targetPackage = resolveForegroundPackage(choice.serial)
        val args = linkedMapOf(
            Constants.KEY_NEED_COLOR to "false"
        )
        val decoded = sendBroadcast(choice.serial, Constants.ACTION_DEBUG_LAYOUT_INFO, args, targetPackage)
        val appJson = extractBaseData(decoded.json)
            ?: throw AdapterException("DECODE_ERROR", "Missing application data in broadcast response")
        val responsePackage = SnapshotMapper.detectPackage(appJson)
        if (!targetPackage.isNullOrBlank() && !responsePackage.isNullOrBlank() && targetPackage != responsePackage) {
            throw AdapterException(
                "TARGET_MISMATCH",
                "Foreground package does not match broadcast responder",
                mapOf(
                    "device_serial" to choice.serial,
                    "expected_package" to targetPackage,
                    "actual_package" to responsePackage
                )
            )
        }
        val screenshot = tryGrabScreenshot(choice.serial)

        val saved = store.importLive(appJson, screenshot, choice.serial, choice.notice, sourceRoot)
        store.updateState {
            it.addProperty("last_success_device", choice.serial)
            it.addProperty("last_grab_id", saved.grabId)
        }
        if (choice.notice != null) {
            MacNotifier.notify("CodeLocator MCP", choice.notice)
        }
        return saved
    }

    fun getViewData(grabId: String, memAddr: String): ToolResult<Map<String, Any?>> {
        val snapshot = store.loadSnapshot(grabId)
        val serial = snapshot.meta.deviceSerial
            ?: return ToolResult(
                success = false,
                error = McpError("INVALID_ARGUMENT", "grab_id is not from live capture"),
                grabId = grabId
            )
        val itemId = parseItemId(memAddr)
        val changeViewJson = buildEditCommand(itemId, Constants.EDIT_GET_VIEW_DATA)
        val decoded = sendBroadcast(
            serial,
            Constants.ACTION_CHANGE_VIEW_INFO,
            linkedMapOf(Constants.KEY_CHANGE_VIEW to changeViewJson),
            snapshot.meta.packageName
        )
        val responseObj = Jsons.parseObject(decoded.json)
        val resultMap = extractOperateResultMap(responseObj)
        val err = resultMap[Constants.RESULT_KEY_ERROR]
        if (!err.isNullOrBlank()) {
            return ToolResult(
                success = false,
                error = McpError("VIEW_NOT_FOUND", err, mapOf("stack" to resultMap[Constants.RESULT_KEY_STACK])),
                grabId = grabId
            )
        }

        val filePath = resultMap[Constants.RESULT_KEY_FILE_PATH]
        val targetClass = resultMap[Constants.RESULT_KEY_TARGET_CLASS]
        val dataText = if (!filePath.isNullOrBlank()) {
            pullAndReadText(serial, filePath)
        } else {
            resultMap[Constants.RESULT_KEY_DATA]
        }

        val structured = detectJson(dataText)
        return ToolResult(
            success = true,
            data = linkedMapOf(
                "target_class" to targetClass,
                "raw" to dataText,
                "structured" to structured
            ),
            grabId = grabId
        )
    }

    fun getViewClassInfo(grabId: String, memAddr: String): ToolResult<Map<String, Any?>> {
        val snapshot = store.loadSnapshot(grabId)
        val serial = snapshot.meta.deviceSerial
            ?: return ToolResult(
                success = false,
                error = McpError("INVALID_ARGUMENT", "grab_id is not from live capture"),
                grabId = grabId
            )
        val itemId = parseItemId(memAddr)
        val changeViewJson = buildEditCommand(itemId, Constants.EDIT_GET_VIEW_CLASS_INFO)
        val decoded = sendBroadcast(
            serial,
            Constants.ACTION_CHANGE_VIEW_INFO,
            linkedMapOf(Constants.KEY_CHANGE_VIEW to changeViewJson),
            snapshot.meta.packageName
        )
        val responseObj = Jsons.parseObject(decoded.json)
        val resultMap = extractOperateResultMap(responseObj)
        val err = resultMap[Constants.RESULT_KEY_ERROR]
        if (!err.isNullOrBlank()) {
            return ToolResult(
                success = false,
                error = McpError("VIEW_NOT_FOUND", err, mapOf("stack" to resultMap[Constants.RESULT_KEY_STACK])),
                grabId = grabId
            )
        }
        val rawData = resultMap[Constants.RESULT_KEY_DATA]
        return ToolResult(
            success = true,
            data = linkedMapOf(
                "raw" to rawData,
                "structured" to detectJson(rawData)
            ),
            grabId = grabId
        )
    }

    fun traceTouch(grabId: String): ToolResult<Map<String, Any?>> {
        val snapshot = store.loadSnapshot(grabId)
        val serial = snapshot.meta.deviceSerial
            ?: return ToolResult(
                success = false,
                error = McpError("INVALID_ARGUMENT", "grab_id is not from live capture"),
                grabId = grabId
            )

        val decoded = sendBroadcast(
            serial,
            Constants.ACTION_GET_TOUCH_VIEW,
            emptyMap(),
            snapshot.meta.packageName
        )
        val obj = Jsons.parseObject(decoded.json)
        val data = obj.get("data")
        val viewIds = if (data != null && data.isJsonArray) {
            data.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
        } else {
            emptyList()
        }

        return ToolResult(
            success = true,
            data = linkedMapOf(
                "view_ids" to viewIds,
                "count" to viewIds.size
            ),
            grabId = grabId
        )
    }

    fun chooseDevice(deviceSerialArg: String?): DeviceChoice {
        val adb = adbPath()
        val output = exec(listOf(adb, "devices"), 15_000)
        val devices = output.lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("\tdevice") }
            .map { it.substringBefore("\t") }
            .toList()
        if (devices.isEmpty()) {
            throw AdapterException("NO_DEVICE", "No online Android device found")
        }

        if (!deviceSerialArg.isNullOrBlank()) {
            if (!devices.contains(deviceSerialArg)) {
                throw AdapterException("NO_DEVICE", "Specified device not found: $deviceSerialArg")
            }
            return DeviceChoice(deviceSerialArg, null)
        }

        val last = store.readState().get("last_success_device")?.asString
        if (!last.isNullOrBlank() && devices.contains(last)) {
            return DeviceChoice(last, "Using last successful device: $last")
        }
        return DeviceChoice(devices.first(), null)
    }

    fun sendBroadcast(
        deviceSerial: String,
        action: String,
        args: Map<String, String>,
        targetPackage: String? = null
    ): BroadcastDecodedResult {
        val argsJson = Jsons.toJson(args)
        val encodedArgs = Base64.getUrlEncoder().withoutPadding().encodeToString(argsJson.toByteArray())
        val receiverModeArg = if (targetPackage.isNullOrBlank()) "" else " --receiver-registered-only"
        val packageArg = if (targetPackage.isNullOrBlank()) "" else " ${shellEscape(targetPackage)}"
        val cmd =
            "${adbPath()} -s ${shellEscape(deviceSerial)} shell am broadcast${receiverModeArg} -a ${shellEscape(action)} --es ${Constants.KEY_SHELL_ARGS} '${shellEscapeSingleQuote(encodedArgs)}'${packageArg}"
        val output = exec(listOf("/bin/sh", "-lc", cmd), 30_000)

        val encodedPayload = extractEncodedPayload(output, deviceSerial)
        val decodedJson = decodePayload(encodedPayload)
        return BroadcastDecodedResult(encodedPayload, decodedJson, output)
    }

    private fun resolveForegroundPackage(deviceSerial: String): String? {
        val output = exec(listOf(adbPath(), "-s", deviceSerial, "shell", "dumpsys", "window"), 15_000)
        return parseForegroundPackage(output)
    }

    internal fun parseForegroundPackage(output: String): String? {
        val currentFocus = Regex("""mCurrentFocus=Window\{[^\n]*?\su\d+\s([^/\s]+)/""")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
        if (!currentFocus.isNullOrBlank()) {
            return currentFocus
        }
        val focusedApp = Regex("""mFocusedApp=ActivityRecord\{[^\n]*?\su\d+\s([^/\s]+)/""")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
        return focusedApp?.takeIf { it.isNotBlank() }
    }

    private fun extractEncodedPayload(output: String, deviceSerial: String): String {
        val fpRegex = Regex("FP:([^\"\\s,]+)")
        val fp = fpRegex.find(output)?.groupValues?.get(1)
        if (!fp.isNullOrBlank()) {
            return pullAndReadText(deviceSerial, fp).trim()
        }
        val dataRegex = Regex("data=\"([^\"]+)\"")
        val data = dataRegex.find(output)?.groupValues?.get(1)
        if (!data.isNullOrBlank()) return data.trim()
        throw AdapterException("DECODE_ERROR", "No encoded payload found in broadcast result", mapOf("raw" to output))
    }

    private fun decodePayload(encoded: String): String {
        val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }
            .recoverCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse {
                throw AdapterException("DECODE_ERROR", "Base64 decode failed", cause = it)
            }

        return runCatching {
            GZIPInputStream(bytes.inputStream()).bufferedReader().readText()
        }.getOrElse {
            throw AdapterException("DECODE_ERROR", "GZIP decode failed", cause = it)
        }
    }

    private fun extractBaseData(decodedJson: String): String? {
        val root = Jsons.parseObject(decodedJson)
        val data = root.get("data")
        return if (data != null && data.isJsonObject) data.toString() else null
    }

    private fun extractOperateResultMap(obj: JsonObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val data = obj.getAsJsonObject("data") ?: return result
        val list = data.getAsJsonArray("d5") ?: return result
        list.forEach { item ->
            if (!item.isJsonObject) return@forEach
            val itemObj = item.asJsonObject
            val key = itemObj.get("d6")?.asString
            val value = itemObj.get("cg")?.asString
            if (!key.isNullOrBlank() && value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun parseItemId(memAddr: String): Int {
        val normalized = memAddr.removePrefix("0x").removePrefix("0X")
        return normalized.toLong(16).toInt()
    }

    private fun buildEditCommand(itemId: Int, editType: String): String {
        val edit = JsonObject().apply {
            addProperty("d7", editType)
            addProperty("d8", Constants.EDIT_IGNORE)
        }
        val list = JsonArray().apply { add(edit) }
        val operate = JsonObject().apply {
            addProperty("aa", Constants.TYPE_OPERATE_VIEW)
            addProperty("d4", itemId)
            add("d5", list)
        }
        return Jsons.toJson(operate)
    }

    private fun pullAndReadText(deviceSerial: String, remotePath: String): String {
        val tmp = File(Constants.tempDir, "${System.currentTimeMillis()}_${remotePath.substringAfterLast('/')}")
        tmp.parentFile.mkdirs()
        exec(listOf(adbPath(), "-s", deviceSerial, "pull", remotePath, tmp.absolutePath), 30_000)
        if (!tmp.exists()) {
            throw AdapterException("DECODE_ERROR", "pull file failed: $remotePath")
        }
        return tmp.readText().also { tmp.delete() }
    }

    private fun tryGrabScreenshot(deviceSerial: String): ByteArray? {
        return runCatching {
            val remote = "/sdcard/Download/codeLocator_image.png"
            val local = File(Constants.tempDir, "screen_${System.currentTimeMillis()}.png")
            exec(listOf(adbPath(), "-s", deviceSerial, "shell", "screencap", "-p", remote), 30_000)
            exec(listOf(adbPath(), "-s", deviceSerial, "pull", remote, local.absolutePath), 30_000)
            if (!local.exists()) return@runCatching null
            val bytes = local.readBytes()
            local.delete()
            bytes
        }.getOrNull()
    }

    private fun detectJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Jsons.elementToAny(Jsons.parseObject(raw)) }
            .recoverCatching { Jsons.elementToAny(Jsons.parseArray(raw)) }
            .getOrNull()
    }

    private fun adbPath(): String {
        return System.getenv("ADB_PATH")?.takeIf { it.isNotBlank() } ?: "adb"
    }

    private fun exec(command: List<String>, timeoutMs: Long): String {
        return commandRunner(command, timeoutMs)
    }

    private fun shellEscape(raw: String): String {
        return raw.replace(" ", "\\ ")
    }

    private fun shellEscapeSingleQuote(raw: String): String {
        return raw.replace("'", "'\\''")
    }

    companion object {
        private fun execProcess(command: List<String>, timeoutMs: Long): String {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val ok = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!ok) {
                process.destroyForcibly()
                throw AdapterException("TIMEOUT", "Command timeout: ${command.joinToString(" ")}")
            }
            val out = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) {
                throw AdapterException("DECODE_ERROR", "Command failed: ${command.joinToString(" ")}", mapOf("output" to out))
            }
            return out
        }
    }
}
