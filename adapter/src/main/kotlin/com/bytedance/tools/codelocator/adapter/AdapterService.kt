package com.bytedance.tools.codelocator.adapter

class AdapterService(
    private val store: GrabStore,
    private val adb: AdbGateway,
    private val viewerManager: ViewerManager
) {

    fun grabLive(deviceSerial: String?, sourceRoot: String? = null): ToolResult<GrabMeta> {
        val effectiveSourceRoot = sourceRoot ?: System.getenv("CODELOCATOR_SOURCE_ROOT")
        return adb.grabUiState(deviceSerial, effectiveSourceRoot)
    }

    fun grabFromFile(path: String?, sourceRoot: String? = null): ToolResult<GrabMeta> {
        val target = if (!path.isNullOrBlank()) {
            path
        } else {
            val latest = store.latestHistoryFile()
                ?: throw AdapterException(
                    "INVALID_ARGUMENT",
                    "No .codeLocator file found in ~/.android-ui-grab/historyFile or legacy ~/.codeLocator_main/historyFile"
                )
            latest.absolutePath
        }
        return store.importFromCodeLocatorFile(target, sourceRoot = sourceRoot ?: System.getenv("CODELOCATOR_SOURCE_ROOT"))
    }

    fun listGrabs(): ToolResult<List<GrabMeta>> {
        return ToolResult(success = true, data = store.listGrabs())
    }

    fun openViewer(grabId: String?): ToolResult<ViewerOpenResult> {
        val id = grabId ?: store.latestGrabId()
        val open = viewerManager.open(id)
        return ToolResult(success = true, data = open, grabId = id)
    }

    fun getViewData(grabId: String, memAddr: String): ToolResult<Map<String, Any?>> {
        val snapshot = store.loadSnapshot(grabId)
        return if (snapshot.meta.source == "live") {
            adb.getViewData(grabId, memAddr)
        } else {
            val raw = store.getViewRaw(grabId, memAddr)
            if (raw == null) {
                ToolResult(
                    success = false,
                    error = McpError("VIEW_NOT_FOUND", "mem_addr not found"),
                    grabId = grabId
                )
            } else {
                ToolResult(
                    success = true,
                    data = linkedMapOf("structured" to raw),
                    grabId = grabId
                )
            }
        }
    }

    fun getViewClassInfo(grabId: String, memAddr: String): ToolResult<Map<String, Any?>> {
        val snapshot = store.loadSnapshot(grabId)
        return if (snapshot.meta.source == "live") {
            adb.getViewClassInfo(grabId, memAddr)
        } else {
            val raw = store.getViewRaw(grabId, memAddr)
            if (raw == null) {
                ToolResult(
                    success = false,
                    error = McpError("VIEW_NOT_FOUND", "mem_addr not found"),
                    grabId = grabId
                )
            } else {
                ToolResult(
                    success = true,
                    data = linkedMapOf(
                        "class_name" to raw["ag"],
                        "raw" to raw
                    ),
                    grabId = grabId
                )
            }
        }
    }

    fun traceTouch(grabId: String): ToolResult<Map<String, Any?>> {
        val snapshot = store.loadSnapshot(grabId)
        return if (snapshot.meta.source == "live") {
            adb.traceTouch(grabId)
        } else {
            ToolResult(
                success = true,
                data = linkedMapOf(
                    "view_ids" to emptyList<String>(),
                    "count" to 0,
                    "note" to "trace_touch requires live grab"
                ),
                grabId = grabId
            )
        }
    }

    fun getComposeNode(grabId: String, nodeIdOrKey: String): ToolResult<Map<String, Any?>> {
        val semanticsIndex = store.getSemanticsIndex(grabId)
        if (semanticsIndex.isNotEmpty()) {
            val exact = semanticsIndex[nodeIdOrKey]
            if (exact != null) {
                return ToolResult(
                    success = true,
                    data = linkedMapOf(
                        "semantics_key" to exact.semanticsKey,
                        "host_mem_addr" to exact.hostMemAddr,
                        "semantics_id" to exact.semanticsId,
                        "legacy_node_id" to exact.legacyNodeId,
                        "structured" to exact
                    ),
                    grabId = grabId
                )
            }
            val suffixMatches = semanticsIndex.values.filter {
                it.semanticsId == nodeIdOrKey ||
                    it.legacyNodeId == nodeIdOrKey ||
                    it.semanticsKey.endsWith(":$nodeIdOrKey")
            }
            if (suffixMatches.size == 1) {
                val node = suffixMatches.first()
                return ToolResult(
                    success = true,
                    data = linkedMapOf(
                        "semantics_key" to node.semanticsKey,
                        "host_mem_addr" to node.hostMemAddr,
                        "semantics_id" to node.semanticsId,
                        "legacy_node_id" to node.legacyNodeId,
                        "structured" to node
                    ),
                    grabId = grabId
                )
            }
            if (suffixMatches.size > 1) {
                return ToolResult(
                    success = false,
                    error = McpError(
                        "COMPOSE_NODE_AMBIGUOUS",
                        "compose node id is ambiguous, use semantics_key",
                        mapOf(
                            "query" to nodeIdOrKey,
                            "candidates" to suffixMatches.take(20).map { it.semanticsKey }
                        )
                    ),
                    grabId = grabId
                )
            }
        }

        val composeIndex = store.getComposeIndex(grabId)
        if (composeIndex.isEmpty()) {
            return ToolResult(
                success = false,
                error = McpError("COMPOSE_NOT_FOUND", "No compose nodes found in snapshot"),
                grabId = grabId
            )
        }

        val exact = composeIndex[nodeIdOrKey]
        if (exact != null) {
            return ToolResult(
                success = true,
                data = linkedMapOf(
                    "compose_key" to exact.composeKey,
                    "host_mem_addr" to exact.hostMemAddr,
                    "node_id" to exact.nodeId,
                    "structured" to exact
                ),
                grabId = grabId
            )
        }

        val suffixMatches = composeIndex.values.filter { it.nodeId == nodeIdOrKey || it.composeKey.endsWith(":$nodeIdOrKey") }
        if (suffixMatches.isEmpty()) {
            return ToolResult(
                success = false,
                error = McpError("COMPOSE_NOT_FOUND", "compose node not found: $nodeIdOrKey"),
                grabId = grabId
            )
        }
        if (suffixMatches.size > 1) {
            return ToolResult(
                success = false,
                error = McpError(
                    "COMPOSE_NODE_AMBIGUOUS",
                    "compose node id is ambiguous, use compose_key",
                    mapOf(
                        "query" to nodeIdOrKey,
                        "candidates" to suffixMatches.take(20).map { it.composeKey }
                    )
                ),
                grabId = grabId
            )
        }

        val node = suffixMatches.first()
        return ToolResult(
            success = true,
            data = linkedMapOf(
                "compose_key" to node.composeKey,
                "host_mem_addr" to node.hostMemAddr,
                "node_id" to node.nodeId,
                "structured" to node
            ),
            grabId = grabId
        )
    }

    fun getComposeComponent(grabId: String, componentIdOrKey: String): ToolResult<Map<String, Any?>> {
        val index = store.getComponentIndex(grabId)
        if (index.isEmpty()) {
            return ToolResult(success = false, error = McpError("COMPONENT_NOT_FOUND", "No compose components found in snapshot"), grabId = grabId)
        }
        val exact = index[componentIdOrKey]
        if (exact != null) {
            return ToolResult(
                success = true,
                data = linkedMapOf(
                    "component_key" to exact.componentKey,
                    "host_mem_addr" to exact.hostMemAddr,
                    "component_id" to exact.componentId,
                    "structured" to exact
                ),
                grabId = grabId
            )
        }
        val matches = index.values.filter { it.componentId == componentIdOrKey || it.componentKey.endsWith(":$componentIdOrKey") }
        if (matches.isEmpty()) {
            return ToolResult(success = false, error = McpError("COMPONENT_NOT_FOUND", "compose component not found: $componentIdOrKey"), grabId = grabId)
        }
        if (matches.size > 1) {
            return ToolResult(
                success = false,
                error = McpError(
                    "COMPONENT_AMBIGUOUS",
                    "compose component id is ambiguous, use component_key",
                    mapOf("query" to componentIdOrKey, "candidates" to matches.take(20).map { it.componentKey })
                ),
                grabId = grabId
            )
        }
        val node = matches.first()
        return ToolResult(
            success = true,
            data = linkedMapOf(
                "component_key" to node.componentKey,
                "host_mem_addr" to node.hostMemAddr,
                "component_id" to node.componentId,
                "structured" to node
            ),
            grabId = grabId
        )
    }

    fun getComposeRender(grabId: String, renderIdOrKey: String): ToolResult<Map<String, Any?>> {
        val index = store.getRenderIndex(grabId)
        if (index.isEmpty()) {
            return ToolResult(success = false, error = McpError("RENDER_NOT_FOUND", "No compose render nodes found in snapshot"), grabId = grabId)
        }
        val exact = index[renderIdOrKey]
        if (exact != null) {
            return ToolResult(
                success = true,
                data = linkedMapOf(
                    "render_key" to exact.renderKey,
                    "host_mem_addr" to exact.hostMemAddr,
                    "render_id" to exact.renderId,
                    "structured" to exact
                ),
                grabId = grabId
            )
        }
        val matches = index.values.filter { it.renderId == renderIdOrKey || it.renderKey.endsWith(":$renderIdOrKey") }
        if (matches.isEmpty()) {
            return ToolResult(success = false, error = McpError("RENDER_NOT_FOUND", "compose render node not found: $renderIdOrKey"), grabId = grabId)
        }
        if (matches.size > 1) {
            return ToolResult(
                success = false,
                error = McpError(
                    "RENDER_AMBIGUOUS",
                    "compose render id is ambiguous, use render_key",
                    mapOf("query" to renderIdOrKey, "candidates" to matches.take(20).map { it.renderKey })
                ),
                grabId = grabId
            )
        }
        val node = matches.first()
        return ToolResult(
            success = true,
            data = linkedMapOf(
                "render_key" to node.renderKey,
                "host_mem_addr" to node.hostMemAddr,
                "render_id" to node.renderId,
                "structured" to node
            ),
            grabId = grabId
        )
    }

    fun getComposeLink(grabId: String, nodeKey: String): ToolResult<Map<String, Any?>> {
        val index = store.getLinkIndex(grabId)
        if (index.isEmpty()) {
            return ToolResult(success = false, error = McpError("LINK_NOT_FOUND", "No compose links found in snapshot"), grabId = grabId)
        }
        val exact = index[nodeKey]
        if (exact != null) {
            return ToolResult(
                success = true,
                data = linkedMapOf(
                    "link_key" to exact.linkKey,
                    "host_mem_addr" to exact.hostMemAddr,
                    "structured" to exact
                ),
                grabId = grabId
            )
        }
        val matches = index.values.filter { it.linkKey == nodeKey || it.linkKey.endsWith(nodeKey) }
        if (matches.isEmpty()) {
            return ToolResult(success = false, error = McpError("LINK_NOT_FOUND", "compose link not found: $nodeKey"), grabId = grabId)
        }
        if (matches.size > 1) {
            return ToolResult(
                success = false,
                error = McpError(
                    "LINK_AMBIGUOUS",
                    "compose link key is ambiguous, use full link_key",
                    mapOf("query" to nodeKey, "candidates" to matches.take(20).map { it.linkKey })
                ),
                grabId = grabId
            )
        }
        val node = matches.first()
        return ToolResult(
            success = true,
            data = linkedMapOf(
                "link_key" to node.linkKey,
                "host_mem_addr" to node.hostMemAddr,
                "structured" to node
            ),
            grabId = grabId
        )
    }
}
