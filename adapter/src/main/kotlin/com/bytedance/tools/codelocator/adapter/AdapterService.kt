package com.bytedance.tools.codelocator.adapter

class AdapterService(
    private val store: GrabStore,
    private val adb: AdbGateway,
    private val viewerManager: ViewerManager
) {

    fun grabLive(deviceSerial: String?): ToolResult<GrabMeta> {
        return adb.grabUiState(deviceSerial)
    }

    fun grabFromFile(path: String?): ToolResult<GrabMeta> {
        val target = if (!path.isNullOrBlank()) {
            path
        } else {
            val latest = store.latestHistoryFile()
                ?: throw AdapterException("INVALID_ARGUMENT", "No .codeLocator file found in ~/.codeLocator_main/historyFile")
            latest.absolutePath
        }
        return store.importFromCodeLocatorFile(target)
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
}
