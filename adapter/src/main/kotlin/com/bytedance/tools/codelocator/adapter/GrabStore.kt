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

    fun importFromCodeLocatorFile(path: String, deviceNotice: String? = null, sourceRoot: String? = null): ToolResult<GrabMeta> {
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
        saveSnapshot(meta, parsed.appJson, parsed.imageBytes, sourceRoot)
        return ToolResult(success = true, data = meta, grabId = grabId)
    }

    fun importLive(appJson: String, screenshotBytes: ByteArray?, deviceSerial: String, deviceNotice: String?, sourceRoot: String? = null): ToolResult<GrabMeta> {
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
        saveSnapshot(meta, appJson, screenshotBytes ?: ByteArray(0), sourceRoot)
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
        val root = Jsons.parseObject(file.readText())
        if (!root.has("indexes") || root.get("indexes").isJsonNull) {
            root.add("indexes", JsonObject())
        }
        if (!root.has("composeIndexes") || root.get("composeIndexes").isJsonNull) {
            root.add("composeIndexes", JsonObject())
        }
        if (!root.has("componentIndexes") || root.get("componentIndexes").isJsonNull) {
            root.add("componentIndexes", JsonObject())
        }
        if (!root.has("renderIndexes") || root.get("renderIndexes").isJsonNull) {
            root.add("renderIndexes", JsonObject())
        }
        if (!root.has("semanticsIndexes") || root.get("semanticsIndexes").isJsonNull) {
            root.add("semanticsIndexes", JsonObject())
        }
        if (!root.has("linkIndexes") || root.get("linkIndexes").isJsonNull) {
            root.add("linkIndexes", JsonObject())
        }
        return Jsons.gson.fromJson(root, GrabSnapshot::class.java)
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

    fun getComposeIndex(grabId: String): Map<String, ComposeIndexItem> {
        val file = Constants.grabsRoot.resolve(grabId).resolve("compose_index.json").toFile()
        if (!file.exists()) {
            val snapshot = loadSnapshot(grabId)
            if (snapshot.composeIndexes.isNotEmpty()) return snapshot.composeIndexes
            return buildComposeIndex(snapshot.uiTree)
        }
        val root = Jsons.readJsonObject(file)
        val out = linkedMapOf<String, ComposeIndexItem>()
        root.entrySet().forEach { (k, v) ->
            if (v.isJsonObject) {
                out[k] = Jsons.gson.fromJson(v, ComposeIndexItem::class.java)
            }
        }
        if (out.isNotEmpty()) return out
        val snapshot = loadSnapshot(grabId)
        if (snapshot.composeIndexes.isNotEmpty()) return snapshot.composeIndexes
        return buildComposeIndex(snapshot.uiTree)
    }

    fun getComponentIndex(grabId: String): Map<String, ComposeComponentIndexItem> {
        val file = Constants.grabsRoot.resolve(grabId).resolve("component_index.json").toFile()
        if (!file.exists()) {
            val snapshot = loadSnapshot(grabId)
            if (snapshot.componentIndexes.isNotEmpty()) return snapshot.componentIndexes
            return buildComponentIndex(snapshot.uiTree)
        }
        val root = Jsons.readJsonObject(file)
        val out = linkedMapOf<String, ComposeComponentIndexItem>()
        root.entrySet().forEach { (k, v) ->
            if (v.isJsonObject) {
                out[k] = Jsons.gson.fromJson(v, ComposeComponentIndexItem::class.java)
            }
        }
        if (out.isNotEmpty()) return out
        val snapshot = loadSnapshot(grabId)
        if (snapshot.componentIndexes.isNotEmpty()) return snapshot.componentIndexes
        return buildComponentIndex(snapshot.uiTree)
    }

    fun getRenderIndex(grabId: String): Map<String, ComposeRenderIndexItem> {
        val file = Constants.grabsRoot.resolve(grabId).resolve("render_index.json").toFile()
        if (!file.exists()) {
            val snapshot = loadSnapshot(grabId)
            if (snapshot.renderIndexes.isNotEmpty()) return snapshot.renderIndexes
            return buildRenderIndex(snapshot.uiTree)
        }
        val root = Jsons.readJsonObject(file)
        val out = linkedMapOf<String, ComposeRenderIndexItem>()
        root.entrySet().forEach { (k, v) ->
            if (v.isJsonObject) {
                out[k] = Jsons.gson.fromJson(v, ComposeRenderIndexItem::class.java)
            }
        }
        if (out.isNotEmpty()) return out
        val snapshot = loadSnapshot(grabId)
        if (snapshot.renderIndexes.isNotEmpty()) return snapshot.renderIndexes
        return buildRenderIndex(snapshot.uiTree)
    }

    fun getSemanticsIndex(grabId: String): Map<String, ComposeSemanticsIndexItem> {
        val file = Constants.grabsRoot.resolve(grabId).resolve("semantics_index.json").toFile()
        if (!file.exists()) {
            val snapshot = loadSnapshot(grabId)
            if (snapshot.semanticsIndexes.isNotEmpty()) return snapshot.semanticsIndexes
            return buildSemanticsIndex(snapshot.uiTree)
        }
        val root = Jsons.readJsonObject(file)
        val out = linkedMapOf<String, ComposeSemanticsIndexItem>()
        root.entrySet().forEach { (k, v) ->
            if (v.isJsonObject) {
                out[k] = Jsons.gson.fromJson(v, ComposeSemanticsIndexItem::class.java)
            }
        }
        if (out.isNotEmpty()) return out
        val snapshot = loadSnapshot(grabId)
        if (snapshot.semanticsIndexes.isNotEmpty()) return snapshot.semanticsIndexes
        return buildSemanticsIndex(snapshot.uiTree)
    }

    fun getLinkIndex(grabId: String): Map<String, ComposeLinkIndexItem> {
        val file = Constants.grabsRoot.resolve(grabId).resolve("link_index.json").toFile()
        if (!file.exists()) {
            val snapshot = loadSnapshot(grabId)
            if (snapshot.linkIndexes.isNotEmpty()) return snapshot.linkIndexes
            return buildLinkIndex(snapshot.uiTree)
        }
        val root = Jsons.readJsonObject(file)
        val out = linkedMapOf<String, ComposeLinkIndexItem>()
        root.entrySet().forEach { (k, v) ->
            if (v.isJsonObject) {
                out[k] = Jsons.gson.fromJson(v, ComposeLinkIndexItem::class.java)
            }
        }
        if (out.isNotEmpty()) return out
        val snapshot = loadSnapshot(grabId)
        if (snapshot.linkIndexes.isNotEmpty()) return snapshot.linkIndexes
        return buildLinkIndex(snapshot.uiTree)
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

    private fun saveSnapshot(meta: GrabMeta, appJson: String, imageBytes: ByteArray, sourceRoot: String?) {
        val dir = Constants.grabsRoot.resolve(meta.grabId)
        Files.createDirectories(dir)

        val screenshotRef = if (imageBytes.isNotEmpty()) "screenshot.png" else null
        if (imageBytes.isNotEmpty()) {
            Files.write(dir.resolve("screenshot.png"), imageBytes)
        }

        val snapshot = SnapshotMapper.map(meta, appJson, screenshotRef, sourceRoot)

        Files.writeString(dir.resolve("meta.json"), Jsons.toJson(meta))
        Files.writeString(dir.resolve("snapshot.json"), Jsons.toJson(snapshot))
        Files.writeString(dir.resolve("index.json"), Jsons.toJson(snapshot.indexes))
        Files.writeString(dir.resolve("compose_index.json"), Jsons.toJson(snapshot.composeIndexes))
        Files.writeString(dir.resolve("component_index.json"), Jsons.toJson(snapshot.componentIndexes))
        Files.writeString(dir.resolve("render_index.json"), Jsons.toJson(snapshot.renderIndexes))
        Files.writeString(dir.resolve("semantics_index.json"), Jsons.toJson(snapshot.semanticsIndexes))
        Files.writeString(dir.resolve("link_index.json"), Jsons.toJson(snapshot.linkIndexes))
    }

    private fun buildComposeIndex(tree: List<ViewNodeDto>): Map<String, ComposeIndexItem> {
        val out = linkedMapOf<String, ComposeIndexItem>()
        tree.forEach { fillComposeIndex(it, out) }
        return out
    }

    private fun buildComponentIndex(tree: List<ViewNodeDto>): Map<String, ComposeComponentIndexItem> {
        val out = linkedMapOf<String, ComposeComponentIndexItem>()
        tree.forEach { fillComponentIndex(it, out) }
        return out
    }

    private fun buildRenderIndex(tree: List<ViewNodeDto>): Map<String, ComposeRenderIndexItem> {
        val out = linkedMapOf<String, ComposeRenderIndexItem>()
        tree.forEach { fillRenderIndex(it, out) }
        return out
    }

    private fun buildSemanticsIndex(tree: List<ViewNodeDto>): Map<String, ComposeSemanticsIndexItem> {
        val out = linkedMapOf<String, ComposeSemanticsIndexItem>()
        tree.forEach { fillSemanticsIndex(it, out) }
        return out
    }

    private fun buildLinkIndex(tree: List<ViewNodeDto>): Map<String, ComposeLinkIndexItem> {
        val out = linkedMapOf<String, ComposeLinkIndexItem>()
        tree.forEach { fillLinkIndex(it, out) }
        return out
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

    private fun fillComponentIndex(node: ViewNodeDto, out: MutableMap<String, ComposeComponentIndexItem>) {
        node.composeCapture?.componentTree?.forEach { componentNode ->
            fillComponentNodeIndex(node.memAddr, componentNode, null, out)
        }
        node.children.forEach { fillComponentIndex(it, out) }
    }

    private fun fillComponentNodeIndex(
        hostMemAddr: String,
        node: ComposeComponentNodeDto,
        parentComponentId: String?,
        out: MutableMap<String, ComposeComponentIndexItem>
    ) {
        val key = SnapshotMapper.componentKey(hostMemAddr, node.componentId)
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
        node.children.forEach { child -> fillComponentNodeIndex(hostMemAddr, child, node.componentId, out) }
    }

    private fun fillRenderIndex(node: ViewNodeDto, out: MutableMap<String, ComposeRenderIndexItem>) {
        node.composeCapture?.renderTree?.forEach { renderNode ->
            fillRenderNodeIndex(node.memAddr, renderNode, out)
        }
        node.children.forEach { fillRenderIndex(it, out) }
    }

    private fun fillRenderNodeIndex(
        hostMemAddr: String,
        node: ComposeRenderNodeDto,
        out: MutableMap<String, ComposeRenderIndexItem>
    ) {
        val key = SnapshotMapper.renderKey(hostMemAddr, node.renderId)
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
            styleSummary = node.styleSummary
        )
        node.children.forEach { child -> fillRenderNodeIndex(hostMemAddr, child, out) }
    }

    private fun fillSemanticsIndex(node: ViewNodeDto, out: MutableMap<String, ComposeSemanticsIndexItem>) {
        node.composeCapture?.semanticsTree?.forEach { semanticsNode ->
            fillSemanticsNodeIndex(node.memAddr, semanticsNode, out)
        }
        node.children.forEach { fillSemanticsIndex(it, out) }
    }

    private fun fillSemanticsNodeIndex(
        hostMemAddr: String,
        node: ComposeSemanticsNodeDto,
        out: MutableMap<String, ComposeSemanticsIndexItem>
    ) {
        val key = SnapshotMapper.semanticsKey(hostMemAddr, node.semanticsId)
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
            actions = node.actions
        )
        node.children.forEach { child -> fillSemanticsNodeIndex(hostMemAddr, child, out) }
    }

    private fun fillLinkIndex(node: ViewNodeDto, out: MutableMap<String, ComposeLinkIndexItem>) {
        node.composeCapture?.links?.forEach { linkNode ->
            val key = SnapshotMapper.linkKey(node.memAddr, linkNode.sourceNodeType, linkNode.sourceId, linkNode.targetNodeType, linkNode.targetId)
            out[key] = ComposeLinkIndexItem(
                linkKey = key,
                hostMemAddr = node.memAddr,
                sourceNodeType = linkNode.sourceNodeType,
                targetNodeType = linkNode.targetNodeType,
                sourceId = linkNode.sourceId,
                targetId = linkNode.targetId,
                confidence = linkNode.confidence,
                linkStrategy = linkNode.linkStrategy
            )
        }
        node.children.forEach { fillLinkIndex(it, out) }
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
