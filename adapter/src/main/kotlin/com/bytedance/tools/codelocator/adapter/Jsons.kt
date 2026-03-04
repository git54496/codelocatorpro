package com.bytedance.tools.codelocator.adapter

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

object Jsons {
    val gson: Gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    fun parseObject(raw: String): JsonObject = JsonParser.parseString(raw).asJsonObject

    fun parseArray(raw: String): JsonArray = JsonParser.parseString(raw).asJsonArray

    fun pretty(raw: String): String = gson.toJson(JsonParser.parseString(raw))

    fun toJson(element: Any?): String = gson.toJson(element)

    fun elementToAny(element: JsonElement?): Any? {
        if (element == null || element.isJsonNull) return null
        return when {
            element.isJsonObject -> {
                val map = linkedMapOf<String, Any?>()
                element.asJsonObject.entrySet().forEach { (k, v) -> map[k] = elementToAny(v) }
                map
            }
            element.isJsonArray -> element.asJsonArray.map { elementToAny(it) }
            else -> {
                val p = element.asJsonPrimitive
                when {
                    p.isBoolean -> p.asBoolean
                    p.isNumber -> p.asNumber
                    else -> p.asString
                }
            }
        }
    }

    fun readJsonObject(file: File): JsonObject = parseObject(file.readText())
}
