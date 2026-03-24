package com.bytedance.tools.codelocator.adapter

data class GrabMeta(
    val grabId: String,
    val source: String,
    val deviceSerial: String? = null,
    val packageName: String? = null,
    val activity: String? = null,
    val grabTime: Long,
    val deviceNotice: String? = null
)

data class ViewNodeDto(
    val memAddr: String,
    val className: String,
    val idStr: String? = null,
    val text: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val visible: Boolean = true,
    val alpha: Double = 1.0,
    val composeCapture: ComposeCaptureDto? = null,
    val composeNodes: List<ComposeNodeDto> = emptyList(),
    val children: List<ViewNodeDto> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
)

data class GrabSnapshot(
    val meta: GrabMeta,
    val uiTree: List<ViewNodeDto>,
    val screenshotRef: String? = null,
    val indexes: Map<String, ViewIndexItem> = emptyMap(),
    val composeIndexes: Map<String, ComposeIndexItem> = emptyMap(),
    val componentIndexes: Map<String, ComposeComponentIndexItem> = emptyMap(),
    val renderIndexes: Map<String, ComposeRenderIndexItem> = emptyMap(),
    val semanticsIndexes: Map<String, ComposeSemanticsIndexItem> = emptyMap(),
    val linkIndexes: Map<String, ComposeLinkIndexItem> = emptyMap(),
    val activityStack: List<ActivityStackItemDto> = emptyList(),
    val activityStackOrderInfo: ActivityStackOrderInfo? = null
)

data class ActivityStackItemDto(
    val memAddr: String,
    val className: String,
    val startInfo: String? = null,
    val current: Boolean = false,
    val covered: Boolean = false,
    val paused: Boolean = false,
    val stopped: Boolean = false,
    val fragments: List<FragmentNodeDto> = emptyList(),
    val orderStable: Boolean? = null
)

data class ActivityStackOrderInfo(
    val orderingMode: String = "current_first_system_record_order",
    val topActivityStable: Boolean = true,
    val backgroundActivitiesUnstable: Boolean = true,
    val warning: String = "Only the top activity is considered stable. Background activities keep the captured system record order and may be inaccurate."
)

data class FragmentNodeDto(
    val memAddr: String,
    val className: String,
    val tag: String? = null,
    val fragmentId: Int? = null,
    val viewMemAddr: String? = null,
    val visible: Boolean = false,
    val added: Boolean = false,
    val userVisibleHint: Boolean = false,
    val boundViewVisible: Boolean = false,
    val coveredByTopActivity: Boolean = false,
    val effectiveVisible: Boolean = false,
    val children: List<FragmentNodeDto> = emptyList()
)

data class ViewIndexItem(
    val memAddr: String,
    val className: String,
    val idStr: String? = null,
    val text: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

data class ComposeNodeDto(
    val nodeId: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val visibleToUser: Boolean = true,
    val selected: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focusable: Boolean = false,
    val actions: List<String> = emptyList(),
    val children: List<ComposeNodeDto> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
)

data class ComposeCaptureDto(
    val composeCaptureVersion: String? = null,
    val componentTree: List<ComposeComponentNodeDto> = emptyList(),
    val renderTree: List<ComposeRenderNodeDto> = emptyList(),
    val semanticsTree: List<ComposeSemanticsNodeDto> = emptyList(),
    val links: List<ComposeLinkDto> = emptyList(),
    val errors: List<String> = emptyList()
)

data class ComposeComponentNodeDto(
    val componentId: String,
    val displayName: String? = null,
    val sourcePathToken: String? = null,
    val sourcePath: String? = null,
    val sourceLine: Int = 0,
    val sourceColumn: Int = 0,
    val confidence: Double = 0.0,
    val frameworkNode: Boolean = false,
    val pathResolution: String? = null,
    val children: List<ComposeComponentNodeDto> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
)

data class ComposeRenderNodeDto(
    val renderId: String,
    val parentRenderId: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val visible: Boolean = true,
    val alpha: Double = 1.0,
    val zIndex: Double = 0.0,
    val modifierSummary: String? = null,
    val styleSummary: String? = null,
    val componentId: String? = null,
    val typeName: String? = null,
    val children: List<ComposeRenderNodeDto> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
)

data class ComposeSemanticsNodeDto(
    val semanticsId: String,
    val renderId: String? = null,
    val componentId: String? = null,
    val legacyNodeId: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val visibleToUser: Boolean = true,
    val selected: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focusable: Boolean = false,
    val role: String? = null,
    val className: String? = null,
    val actions: List<String> = emptyList(),
    val children: List<ComposeSemanticsNodeDto> = emptyList(),
    val raw: Map<String, Any?> = emptyMap()
)

data class ComposeLinkDto(
    val sourceNodeType: String,
    val targetNodeType: String,
    val sourceId: String,
    val targetId: String,
    val confidence: Double = 0.0,
    val linkStrategy: String? = null,
    val raw: Map<String, Any?> = emptyMap()
)

data class ComposeIndexItem(
    val composeKey: String,
    val hostMemAddr: String,
    val nodeId: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val visibleToUser: Boolean = true,
    val selected: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focusable: Boolean = false,
    val actions: List<String> = emptyList()
)

data class ComposeComponentIndexItem(
    val componentKey: String,
    val hostMemAddr: String,
    val componentId: String,
    val parentComponentId: String? = null,
    val displayName: String? = null,
    val sourcePathToken: String? = null,
    val sourcePath: String? = null,
    val sourceLine: Int = 0,
    val sourceColumn: Int = 0,
    val confidence: Double = 0.0,
    val frameworkNode: Boolean = false,
    val pathResolution: String? = null
)

data class ComposeRenderIndexItem(
    val renderKey: String,
    val hostMemAddr: String,
    val renderId: String,
    val parentRenderId: String? = null,
    val componentId: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val visible: Boolean = true,
    val alpha: Double = 1.0,
    val zIndex: Double = 0.0,
    val modifierSummary: String? = null,
    val styleSummary: String? = null,
    val typeName: String? = null
)

data class ComposeSemanticsIndexItem(
    val semanticsKey: String,
    val hostMemAddr: String,
    val semanticsId: String,
    val renderId: String? = null,
    val componentId: String? = null,
    val legacyNodeId: String? = null,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val visibleToUser: Boolean = true,
    val selected: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val focusable: Boolean = false,
    val role: String? = null,
    val className: String? = null,
    val actions: List<String> = emptyList()
)

data class ComposeLinkIndexItem(
    val linkKey: String,
    val hostMemAddr: String,
    val sourceNodeType: String,
    val targetNodeType: String,
    val sourceId: String,
    val targetId: String,
    val confidence: Double = 0.0,
    val linkStrategy: String? = null
)

data class McpError(
    val code: String,
    val message: String,
    val details: Map<String, Any?> = emptyMap()
)

data class ToolResult<T>(
    val success: Boolean,
    val data: T? = null,
    val error: McpError? = null,
    val grabId: String? = null
)

data class ViewerOpenResult(
    val url: String,
    val port: Int,
    val pid: Long?,
    val grabId: String?
)

data class GrabWithViewerResult(
    val grab: GrabMeta,
    val viewer: ViewerOpenResult
)

data class ParsedCodeLocatorFile(
    val appJson: String,
    val imageBytes: ByteArray,
    val version: String
)

data class BroadcastDecodedResult(
    val encoded: String,
    val json: String,
    val rawOutput: String
)
