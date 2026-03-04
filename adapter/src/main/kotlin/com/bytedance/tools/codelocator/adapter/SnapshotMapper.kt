package com.bytedance.tools.codelocator.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.max

object SnapshotMapper {

    fun map(meta: GrabMeta, appJsonRaw: String, screenshotRef: String?): GrabSnapshot {
        val app = Jsons.parseObject(appJsonRaw)
        val activity = getObj(app, "b7", "mActivity")
        val roots = getArray(activity, "cj", "mDecorViews")
            ?: getArray(app, "cj", "mDecorViews")
            ?: JsonArray()

        val tree = roots.mapNotNull { parseView(it.asJsonObject) }
        val index = linkedMapOf<String, ViewIndexItem>()
        tree.forEach { fillIndex(it, index) }

        return GrabSnapshot(
            meta = meta,
            uiTree = tree,
            screenshotRef = screenshotRef,
            indexes = index
        )
    }

    fun detectPackage(appJsonRaw: String): String? {
        return runCatching { getString(Jsons.parseObject(appJsonRaw), "bd", "mPackageName") }.getOrNull()
    }

    fun detectActivity(appJsonRaw: String): String? {
        return runCatching {
            val app = Jsons.parseObject(appJsonRaw)
            val activity = getObj(app, "b7", "mActivity")
            getString(activity, "ag", "mClassName")
        }.getOrNull()
    }

    private fun fillIndex(node: ViewNodeDto, out: MutableMap<String, ViewIndexItem>) {
        out[node.memAddr] = ViewIndexItem(
            memAddr = node.memAddr,
            className = node.className,
            idStr = node.idStr,
            text = node.text,
            left = node.left,
            top = node.top,
            width = node.width,
            height = node.height
        )
        node.children.forEach { fillIndex(it, out) }
    }

    private fun parseView(viewObj: JsonObject): ViewNodeDto? {
        val memAddr = getString(viewObj, "af", "mMemAddr") ?: return null
        val className = getString(viewObj, "ag", "mClassName") ?: "UnknownView"
        val idStr = getString(viewObj, "ac", "mIdStr")
        val text = getString(viewObj, "aq", "mText")

        val left = getInt(viewObj, "d", "mLeft") ?: 0
        val top = getInt(viewObj, "f", "mTop") ?: 0
        val right = getInt(viewObj, "e", "mRight") ?: left
        val bottom = getInt(viewObj, "g", "mBottom") ?: top
        val width = max(0, right - left)
        val height = max(0, bottom - top)

        val visibility = getString(viewObj, "ab", "mVisibility")
        val visible = visibility?.let { it != "8" } ?: true
        val alpha = getDouble(viewObj, "ae", "mAlpha") ?: 1.0

        val childrenArray = getArray(viewObj, "a", "mChildren") ?: JsonArray()
        val children = childrenArray.mapNotNull { child ->
            if (child.isJsonObject) parseView(child.asJsonObject) else null
        }

        return ViewNodeDto(
            memAddr = memAddr,
            className = className,
            idStr = idStr,
            text = text,
            left = left,
            top = top,
            width = width,
            height = height,
            visible = visible,
            alpha = alpha,
            children = children,
            raw = Jsons.elementToAny(viewObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun getObj(obj: JsonObject?, vararg keys: String): JsonObject? {
        if (obj == null) return null
        keys.forEach { key ->
            if (obj.has(key) && obj[key].isJsonObject) return obj[key].asJsonObject
        }
        return null
    }

    private fun getArray(obj: JsonObject?, vararg keys: String): JsonArray? {
        if (obj == null) return null
        keys.forEach { key ->
            if (obj.has(key) && obj[key].isJsonArray) return obj[key].asJsonArray
        }
        return null
    }

    private fun getString(obj: JsonObject?, vararg keys: String): String? {
        if (obj == null) return null
        keys.forEach { key ->
            val ele: JsonElement = obj.get(key) ?: return@forEach
            if (ele.isJsonNull) return@forEach
            if (ele.isJsonPrimitive) return ele.asJsonPrimitive.asString
        }
        return null
    }

    private fun getInt(obj: JsonObject?, vararg keys: String): Int? {
        if (obj == null) return null
        keys.forEach { key ->
            val ele = obj.get(key) ?: return@forEach
            if (ele.isJsonPrimitive) {
                return runCatching { ele.asJsonPrimitive.asInt }.getOrNull()
            }
        }
        return null
    }

    private fun getDouble(obj: JsonObject?, vararg keys: String): Double? {
        if (obj == null) return null
        keys.forEach { key ->
            val ele = obj.get(key) ?: return@forEach
            if (ele.isJsonPrimitive) {
                return runCatching { ele.asJsonPrimitive.asDouble }.getOrNull()
            }
        }
        return null
    }
}
