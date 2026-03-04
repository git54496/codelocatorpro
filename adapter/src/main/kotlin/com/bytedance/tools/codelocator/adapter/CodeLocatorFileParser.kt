package com.bytedance.tools.codelocator.adapter

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import kotlin.math.max

object CodeLocatorFileParser {
    private const val TAG = "CodeLocator"

    fun parse(file: File): ParsedCodeLocatorFile {
        if (!file.exists()) {
            throw AdapterException("INVALID_ARGUMENT", "File not found: ${file.absolutePath}")
        }
        val bytes = file.readBytes()
        val ins = DataInputStream(ByteArrayInputStream(bytes))

        val tagLen = ins.readInt()
        val tagBytes = ByteArray(tagLen)
        ins.readFully(tagBytes)
        val tag = tagBytes.toString(Charsets.UTF_8)
        if (tag != TAG) {
            throw AdapterException("DECODE_ERROR", "Invalid .codeLocator file tag")
        }

        val versionLen = ins.readInt()
        val versionBytes = ByteArray(versionLen)
        ins.readFully(versionBytes)
        val version = versionBytes.toString(Charsets.UTF_8)

        val appLen = ins.readInt()
        val appBytes = ByteArray(appLen)
        ins.readFully(appBytes)
        val appJson = appBytes.toString(Charsets.UTF_8)

        val consumed = 4 + tagLen + 4 + versionLen + 4 + appLen
        val imageLen = max(0, bytes.size - consumed)
        val imageBytes = ByteArray(imageLen)
        if (imageLen > 0) {
            ins.readFully(imageBytes)
        }

        return ParsedCodeLocatorFile(
            appJson = appJson,
            imageBytes = imageBytes,
            version = version
        )
    }
}
