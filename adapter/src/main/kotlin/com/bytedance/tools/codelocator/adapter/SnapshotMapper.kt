package com.bytedance.tools.codelocator.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.nio.file.Path
import kotlin.math.max

object SnapshotMapper {

    fun map(meta: GrabMeta, appJsonRaw: String, screenshotRef: String?, sourceRoot: String? = null): GrabSnapshot {
        val app = Jsons.parseObject(appJsonRaw)
        val activity = getObj(app, "b7", "mActivity")
        val activityStack = parseActivityStack(app)
        val roots = getArray(activity, "cj", "mDecorViews")
            ?: getArray(app, "cj", "mDecorViews")
            ?: JsonArray()

        val tree = roots.mapNotNull { parseView(it.asJsonObject, sourceRoot) }
        val index = linkedMapOf<String, ViewIndexItem>()
        val composeIndex = linkedMapOf<String, ComposeIndexItem>()
        val componentIndex = linkedMapOf<String, ComposeComponentIndexItem>()
        val renderIndex = linkedMapOf<String, ComposeRenderIndexItem>()
        val semanticsIndex = linkedMapOf<String, ComposeSemanticsIndexItem>()
        val linkIndex = linkedMapOf<String, ComposeLinkIndexItem>()
        tree.forEach { fillIndex(it, index) }
        tree.forEach { fillComposeIndex(it, composeIndex) }
        tree.forEach { fillComposeCaptureIndexes(it, componentIndex, renderIndex, semanticsIndex, linkIndex) }

        return GrabSnapshot(
            meta = meta,
            uiTree = tree,
            screenshotRef = screenshotRef,
            indexes = index,
            composeIndexes = composeIndex,
            componentIndexes = componentIndex,
            renderIndexes = renderIndex,
            semanticsIndexes = semanticsIndex,
            linkIndexes = linkIndex,
            activityStack = activityStack
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

    private fun parseActivityStack(app: JsonObject): List<ActivityStackItemDto> {
        val stackArray = getArray(app, "c1", "mActivityStack")
        val parsed = stackArray?.mapNotNull { item ->
            if (item.isJsonObject) parseActivity(item.asJsonObject) else null
        } ?: emptyList()
        if (parsed.isNotEmpty()) return parsed

        val currentActivity = getObj(app, "b7", "mActivity") ?: return emptyList()
        val fallback = parseActivity(currentActivity) ?: return emptyList()
        return listOf(fallback.copy(current = true, covered = false))
    }

    private fun parseActivity(activityObj: JsonObject): ActivityStackItemDto? {
        val memAddr = getString(activityObj, "af", "mMemAddr") ?: return null
        val className = getString(activityObj, "ag", "mClassName") ?: "UnknownActivity"
        val fragments = getArray(activityObj, "ck", "mFragments")?.mapNotNull { child ->
            if (child.isJsonObject) parseFragment(child.asJsonObject) else null
        } ?: emptyList()
        return ActivityStackItemDto(
            memAddr = memAddr,
            className = className,
            startInfo = getString(activityObj, "cl", "mStartInfo"),
            current = getBoolean(activityObj, "cm", "mCurrent") ?: false,
            covered = getBoolean(activityObj, "cn", "mCovered") ?: false,
            paused = getBoolean(activityObj, "co", "mPaused") ?: false,
            stopped = getBoolean(activityObj, "cp", "mStopped") ?: false,
            fragments = fragments
        )
    }

    private fun parseFragment(fragmentObj: JsonObject): FragmentNodeDto? {
        val memAddr = getString(fragmentObj, "af", "mMemAddr") ?: return null
        val className = getString(fragmentObj, "ag", "mClassName") ?: "UnknownFragment"
        val coveredByTopActivity = getBoolean(fragmentObj, "ch", "mCoveredByTopActivity") ?: false
        val visible = getBoolean(fragmentObj, "cd", "mIsVisible") ?: false
        val added = getBoolean(fragmentObj, "ce", "mIsAdded") ?: false
        val userVisibleHint = getBoolean(fragmentObj, "cf", "mUserVisibleHint") ?: false
        val boundViewVisible = getBoolean(fragmentObj, "cg", "mBoundViewVisible") ?: false
        val effectiveVisible = getBoolean(fragmentObj, "ci", "mEffectiveVisible")
            ?: (!coveredByTopActivity && visible && added && userVisibleHint && boundViewVisible)
        val children = getArray(fragmentObj, "a", "mChildren")?.mapNotNull { child ->
            if (child.isJsonObject) parseFragment(child.asJsonObject) else null
        } ?: emptyList()
        return FragmentNodeDto(
            memAddr = memAddr,
            className = className,
            tag = getString(fragmentObj, "cc", "mTag"),
            fragmentId = getInt(fragmentObj, "ad", "mId"),
            viewMemAddr = getString(fragmentObj, "cb", "mViewMemAddr"),
            visible = visible,
            added = added,
            userVisibleHint = userVisibleHint,
            boundViewVisible = boundViewVisible,
            coveredByTopActivity = coveredByTopActivity,
            effectiveVisible = effectiveVisible,
            children = children
        )
    }

    internal fun componentKey(hostMemAddr: String, componentId: String): String = "$hostMemAddr:component:$componentId"

    internal fun renderKey(hostMemAddr: String, renderId: String): String = "$hostMemAddr:render:$renderId"

    internal fun semanticsKey(hostMemAddr: String, semanticsId: String): String = "$hostMemAddr:semantics:$semanticsId"

    internal fun linkKey(hostMemAddr: String, sourceNodeType: String, sourceId: String, targetNodeType: String, targetId: String): String {
        return "$hostMemAddr:link:$sourceNodeType:$sourceId->$targetNodeType:$targetId"
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

    private fun fillComposeIndex(node: ViewNodeDto, out: MutableMap<String, ComposeIndexItem>) {
        node.composeNodes.forEach { composeNode ->
            fillComposeNodeIndex(node.memAddr, composeNode, out)
        }
        node.children.forEach { fillComposeIndex(it, out) }
    }

    private fun fillComposeNodeIndex(hostMemAddr: String, node: ComposeNodeDto, out: MutableMap<String, ComposeIndexItem>) {
        val composeKey = "$hostMemAddr:${node.nodeId}"
        out[composeKey] = ComposeIndexItem(
            composeKey = composeKey,
            hostMemAddr = hostMemAddr,
            nodeId = node.nodeId,
            left = node.left,
            top = node.top,
            right = node.right,
            bottom = node.bottom,
            text = node.text,
            contentDescription = node.contentDescription,
            testTag = node.testTag,
            clickable = node.clickable,
            enabled = node.enabled,
            focused = node.focused,
            visibleToUser = node.visibleToUser,
            selected = node.selected,
            checkable = node.checkable,
            checked = node.checked,
            focusable = node.focusable,
            actions = node.actions
        )
        node.children.forEach { child -> fillComposeNodeIndex(hostMemAddr, child, out) }
    }

    private fun fillComposeCaptureIndexes(
        node: ViewNodeDto,
        componentOut: MutableMap<String, ComposeComponentIndexItem>,
        renderOut: MutableMap<String, ComposeRenderIndexItem>,
        semanticsOut: MutableMap<String, ComposeSemanticsIndexItem>,
        linkOut: MutableMap<String, ComposeLinkIndexItem>
    ) {
        val capture = node.composeCapture
        if (capture != null) {
            capture.componentTree.forEach { component ->
                fillComponentIndex(node.memAddr, component, null, componentOut)
            }
            capture.renderTree.forEach { render ->
                fillRenderIndex(node.memAddr, render, renderOut)
            }
            capture.semanticsTree.forEach { semantics ->
                fillSemanticsIndex(node.memAddr, semantics, semanticsOut)
            }
            capture.links.forEach { link ->
                val key = linkKey(node.memAddr, link.sourceNodeType, link.sourceId, link.targetNodeType, link.targetId)
                linkOut[key] = ComposeLinkIndexItem(
                    linkKey = key,
                    hostMemAddr = node.memAddr,
                    sourceNodeType = link.sourceNodeType,
                    targetNodeType = link.targetNodeType,
                    sourceId = link.sourceId,
                    targetId = link.targetId,
                    confidence = link.confidence,
                    linkStrategy = link.linkStrategy
                )
            }
        }
        node.children.forEach { fillComposeCaptureIndexes(it, componentOut, renderOut, semanticsOut, linkOut) }
    }

    private fun fillComponentIndex(
        hostMemAddr: String,
        node: ComposeComponentNodeDto,
        parentComponentId: String?,
        out: MutableMap<String, ComposeComponentIndexItem>
    ) {
        val key = componentKey(hostMemAddr, node.componentId)
        out[key] = ComposeComponentIndexItem(
            componentKey = key,
            hostMemAddr = hostMemAddr,
            componentId = node.componentId,
            parentComponentId = parentComponentId,
            displayName = node.displayName,
            sourcePathToken = node.sourcePathToken,
            sourcePath = node.sourcePath,
            sourceLine = node.sourceLine,
            sourceColumn = node.sourceColumn,
            confidence = node.confidence,
            frameworkNode = node.frameworkNode,
            pathResolution = node.pathResolution
        )
        node.children.forEach { child ->
            fillComponentIndex(hostMemAddr, child, node.componentId, out)
        }
    }

    private fun fillRenderIndex(
        hostMemAddr: String,
        node: ComposeRenderNodeDto,
        out: MutableMap<String, ComposeRenderIndexItem>
    ) {
        val key = renderKey(hostMemAddr, node.renderId)
        out[key] = ComposeRenderIndexItem(
            renderKey = key,
            hostMemAddr = hostMemAddr,
            renderId = node.renderId,
            parentRenderId = node.parentRenderId,
            componentId = node.componentId,
            left = node.left,
            top = node.top,
            right = node.right,
            bottom = node.bottom,
            visible = node.visible,
            alpha = node.alpha,
            zIndex = node.zIndex,
            modifierSummary = node.modifierSummary,
            styleSummary = node.styleSummary,
            typeName = node.typeName
        )
        node.children.forEach { child -> fillRenderIndex(hostMemAddr, child, out) }
    }

    private fun fillSemanticsIndex(
        hostMemAddr: String,
        node: ComposeSemanticsNodeDto,
        out: MutableMap<String, ComposeSemanticsIndexItem>
    ) {
        val key = semanticsKey(hostMemAddr, node.semanticsId)
        out[key] = ComposeSemanticsIndexItem(
            semanticsKey = key,
            hostMemAddr = hostMemAddr,
            semanticsId = node.semanticsId,
            renderId = node.renderId,
            componentId = node.componentId,
            legacyNodeId = node.legacyNodeId,
            left = node.left,
            top = node.top,
            right = node.right,
            bottom = node.bottom,
            text = node.text,
            contentDescription = node.contentDescription,
            testTag = node.testTag,
            clickable = node.clickable,
            enabled = node.enabled,
            focused = node.focused,
            visibleToUser = node.visibleToUser,
            selected = node.selected,
            checkable = node.checkable,
            checked = node.checked,
            focusable = node.focusable,
            role = node.role,
            className = node.className,
            actions = node.actions
        )
        node.children.forEach { child -> fillSemanticsIndex(hostMemAddr, child, out) }
    }

    private fun parseView(viewObj: JsonObject, sourceRoot: String?): ViewNodeDto? {
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

        val composeCaptureObj = getObj(viewObj, "b6", "mComposeCapture")
        val composeCapture = composeCaptureObj?.let { parseComposeCapture(it, sourceRoot) }

        val composeArray = getArray(viewObj, "b5", "mComposeNodes") ?: JsonArray()
        val composeNodes = composeArray.mapNotNull { compose ->
            if (compose.isJsonObject) parseComposeNode(compose.asJsonObject) else null
        }

        val childrenArray = getArray(viewObj, "a", "mChildren") ?: JsonArray()
        val children = childrenArray.mapNotNull { child ->
            if (child.isJsonObject) parseView(child.asJsonObject, sourceRoot) else null
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
            composeCapture = composeCapture,
            composeNodes = composeNodes,
            children = children,
            raw = Jsons.elementToAny(viewObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun parseComposeCapture(obj: JsonObject, sourceRoot: String?): ComposeCaptureDto {
        val componentTree = (getArray(obj, "b", "componentTree") ?: JsonArray()).mapNotNull {
            if (it.isJsonObject) parseComponentNode(it.asJsonObject, sourceRoot) else null
        }
        val renderTree = (getArray(obj, "c", "renderTree") ?: JsonArray()).mapNotNull {
            if (it.isJsonObject) parseRenderNode(it.asJsonObject) else null
        }
        val semanticsTree = (getArray(obj, "d", "semanticsTree") ?: JsonArray()).mapNotNull {
            if (it.isJsonObject) parseSemanticsNode(it.asJsonObject) else null
        }
        val links = (getArray(obj, "e", "links") ?: JsonArray()).mapNotNull {
            if (it.isJsonObject) parseLink(it.asJsonObject) else null
        }
        val errors = (getArray(obj, "f", "errors") ?: JsonArray()).mapNotNull {
            if (it.isJsonPrimitive) runCatching { it.asString }.getOrNull() else null
        }
        return ComposeCaptureDto(
            composeCaptureVersion = getString(obj, "a", "composeCaptureVersion"),
            componentTree = componentTree,
            renderTree = renderTree,
            semanticsTree = semanticsTree,
            links = links,
            errors = errors
        )
    }

    private fun parseComponentNode(nodeObj: JsonObject, sourceRoot: String?): ComposeComponentNodeDto? {
        val componentId = getString(nodeObj, "a", "componentId") ?: return null
        val token = getString(nodeObj, "c", "sourcePathToken")
        val rawPath = getString(nodeObj, "d", "sourcePath")
        val normalizedPath = normalizeSourcePath(sourceRoot, token, rawPath)
        return ComposeComponentNodeDto(
            componentId = componentId,
            displayName = getString(nodeObj, "b", "displayName"),
            sourcePathToken = token,
            sourcePath = normalizedPath.path,
            sourceLine = getInt(nodeObj, "e", "sourceLine") ?: 0,
            sourceColumn = getInt(nodeObj, "f", "sourceColumn") ?: 0,
            confidence = getDouble(nodeObj, "g", "confidence") ?: 0.0,
            frameworkNode = getBoolean(nodeObj, "h", "frameworkNode") ?: false,
            pathResolution = normalizedPath.resolution,
            children = (getArray(nodeObj, "i", "children") ?: JsonArray()).mapNotNull {
                if (it.isJsonObject) parseComponentNode(it.asJsonObject, sourceRoot) else null
            },
            raw = Jsons.elementToAny(nodeObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun parseRenderNode(nodeObj: JsonObject): ComposeRenderNodeDto? {
        val renderId = getString(nodeObj, "a", "renderId") ?: return null
        return ComposeRenderNodeDto(
            renderId = renderId,
            parentRenderId = getString(nodeObj, "b", "parentRenderId"),
            left = getInt(nodeObj, "c", "left") ?: 0,
            top = getInt(nodeObj, "d", "top") ?: 0,
            right = getInt(nodeObj, "e", "right") ?: 0,
            bottom = getInt(nodeObj, "f", "bottom") ?: 0,
            visible = getBoolean(nodeObj, "g", "visible") ?: true,
            alpha = getDouble(nodeObj, "h", "alpha") ?: 1.0,
            zIndex = getDouble(nodeObj, "i", "zIndex") ?: 0.0,
            modifierSummary = getString(nodeObj, "j", "modifierSummary"),
            styleSummary = getString(nodeObj, "k", "styleSummary"),
            componentId = getString(nodeObj, "l", "componentId"),
            typeName = getString(nodeObj, "n", "typeName"),
            children = (getArray(nodeObj, "m", "children") ?: JsonArray()).mapNotNull {
                if (it.isJsonObject) parseRenderNode(it.asJsonObject) else null
            },
            raw = Jsons.elementToAny(nodeObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun parseSemanticsNode(nodeObj: JsonObject): ComposeSemanticsNodeDto? {
        val semanticsId = getString(nodeObj, "a", "semanticsId") ?: return null
        val actionArray = getArray(nodeObj, "t", "actions") ?: JsonArray()
        val actions = actionArray.mapNotNull { action ->
            if (action.isJsonPrimitive) runCatching { action.asJsonPrimitive.asString }.getOrNull() else null
        }
        val children = (getArray(nodeObj, "u", "children") ?: JsonArray()).mapNotNull {
            if (it.isJsonObject) parseSemanticsNode(it.asJsonObject) else null
        }
        return ComposeSemanticsNodeDto(
            semanticsId = semanticsId,
            renderId = getString(nodeObj, "b", "renderId"),
            componentId = getString(nodeObj, "c", "componentId"),
            legacyNodeId = getString(nodeObj, "d", "legacyNodeId"),
            left = getInt(nodeObj, "e", "left") ?: 0,
            top = getInt(nodeObj, "f", "top") ?: 0,
            right = getInt(nodeObj, "g", "right") ?: 0,
            bottom = getInt(nodeObj, "h", "bottom") ?: 0,
            text = getString(nodeObj, "i", "text"),
            contentDescription = getString(nodeObj, "j", "contentDescription"),
            testTag = getString(nodeObj, "k", "testTag"),
            clickable = getBoolean(nodeObj, "l", "clickable") ?: false,
            enabled = getBoolean(nodeObj, "m", "enabled") ?: true,
            focused = getBoolean(nodeObj, "n", "focused") ?: false,
            visibleToUser = getBoolean(nodeObj, "o", "visibleToUser") ?: true,
            selected = getBoolean(nodeObj, "p", "selected") ?: false,
            checkable = getBoolean(nodeObj, "q", "checkable") ?: false,
            checked = getBoolean(nodeObj, "r", "checked") ?: false,
            focusable = getBoolean(nodeObj, "s", "focusable") ?: false,
            role = getString(nodeObj, "v", "role"),
            className = getString(nodeObj, "w", "className"),
            actions = actions,
            children = children,
            raw = Jsons.elementToAny(nodeObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun parseLink(nodeObj: JsonObject): ComposeLinkDto? {
        val sourceType = getString(nodeObj, "a", "sourceNodeType") ?: return null
        val targetType = getString(nodeObj, "b", "targetNodeType") ?: return null
        val sourceId = getString(nodeObj, "c", "sourceId") ?: return null
        val targetId = getString(nodeObj, "d", "targetId") ?: return null
        return ComposeLinkDto(
            sourceNodeType = sourceType,
            targetNodeType = targetType,
            sourceId = sourceId,
            targetId = targetId,
            confidence = getDouble(nodeObj, "e", "confidence") ?: 0.0,
            linkStrategy = getString(nodeObj, "f", "linkStrategy"),
            raw = Jsons.elementToAny(nodeObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun parseComposeNode(nodeObj: JsonObject): ComposeNodeDto? {
        val nodeId = getString(nodeObj, "a", "nodeId") ?: return null
        val left = getInt(nodeObj, "b", "left") ?: 0
        val top = getInt(nodeObj, "c", "top") ?: 0
        val right = getInt(nodeObj, "d", "right") ?: left
        val bottom = getInt(nodeObj, "e", "bottom") ?: top
        val text = getString(nodeObj, "f", "text")
        val contentDescription = getString(nodeObj, "g", "contentDescription")
        val testTag = getString(nodeObj, "h", "testTag")
        val clickable = getBoolean(nodeObj, "i", "clickable") ?: false
        val enabled = getBoolean(nodeObj, "j", "enabled") ?: true
        val focused = getBoolean(nodeObj, "k", "focused") ?: false
        val visibleToUser = getBoolean(nodeObj, "l", "visibleToUser") ?: true
        val selected = getBoolean(nodeObj, "m", "selected") ?: false
        val checkable = getBoolean(nodeObj, "n", "checkable") ?: false
        val checked = getBoolean(nodeObj, "o", "checked") ?: false
        val focusable = getBoolean(nodeObj, "p", "focusable") ?: false

        val actionArray = getArray(nodeObj, "q", "actions") ?: JsonArray()
        val actions = actionArray.mapNotNull { action ->
            if (action.isJsonPrimitive) runCatching { action.asJsonPrimitive.asString }.getOrNull() else null
        }

        val childArray = getArray(nodeObj, "r", "children") ?: JsonArray()
        val children = childArray.mapNotNull { child ->
            if (child.isJsonObject) parseComposeNode(child.asJsonObject) else null
        }

        return ComposeNodeDto(
            nodeId = nodeId,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            text = text,
            contentDescription = contentDescription,
            testTag = testTag,
            clickable = clickable,
            enabled = enabled,
            focused = focused,
            visibleToUser = visibleToUser,
            selected = selected,
            checkable = checkable,
            checked = checked,
            focusable = focusable,
            actions = actions,
            children = children,
            raw = Jsons.elementToAny(nodeObj) as? Map<String, Any?> ?: emptyMap()
        )
    }

    private fun normalizeSourcePath(sourceRoot: String?, token: String?, existingPath: String?): NormalizedSourcePath {
        val candidate = firstNonBlank(existingPath, extractPathLike(token))
        if (candidate.isNullOrBlank()) {
            return NormalizedSourcePath(null, if (!token.isNullOrBlank()) "raw" else "unknown")
        }
        if (sourceRoot.isNullOrBlank()) {
            return NormalizedSourcePath(cleanPath(candidate), "raw")
        }

        val normalized = relativizeToRoot(sourceRoot, candidate)
        if (normalized != null) {
            return NormalizedSourcePath(normalized, "normalized")
        }
        return NormalizedSourcePath(cleanPath(candidate), "raw")
    }

    private fun relativizeToRoot(sourceRoot: String, candidate: String): String? {
        val root = runCatching { Path.of(sourceRoot).toAbsolutePath().normalize() }.getOrNull() ?: return null
        val cleanedCandidate = cleanPath(candidate)
        val candidatePath = runCatching { Path.of(cleanedCandidate).toAbsolutePath().normalize() }.getOrNull()
        if (candidatePath != null && candidatePath.startsWith(root)) {
            return cleanPath(root.relativize(candidatePath).toString())
        }
        return if (!cleanedCandidate.startsWith("/") && !cleanedCandidate.contains(":") && (cleanedCandidate.contains(".kt") || cleanedCandidate.contains(".java"))) {
            cleanedCandidate
        } else {
            null
        }
    }

    private fun extractPathLike(token: String?): String? {
        if (token.isNullOrBlank()) return null
        val text = token.trim()
        val markerIndex = text.lastIndexOf(':')
        val tail = if (markerIndex >= 0) text.substring(markerIndex + 1) else text
        val hashIndex = tail.indexOf('#')
        val candidate = if (hashIndex >= 0) tail.substring(0, hashIndex) else tail
        return if (candidate.contains(".kt") || candidate.contains(".java")) cleanPath(candidate) else null
    }

    private fun cleanPath(path: String): String = path.replace('\\', '/').removePrefix("./")

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private data class NormalizedSourcePath(val path: String?, val resolution: String)

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

    private fun getBoolean(obj: JsonObject?, vararg keys: String): Boolean? {
        if (obj == null) return null
        keys.forEach { key ->
            val ele = obj.get(key) ?: return@forEach
            if (!ele.isJsonPrimitive) return@forEach
            val p = ele.asJsonPrimitive
            if (p.isBoolean) return p.asBoolean
            if (p.isNumber) return runCatching { p.asInt != 0 }.getOrNull()
            if (p.isString) {
                return when (p.asString.trim().lowercase()) {
                    "true", "1", "y", "yes" -> true
                    "false", "0", "n", "no" -> false
                    else -> null
                }
            }
        }
        return null
    }
}
