package com.bytedance.tools.codelocator.adapter

import java.io.File
import java.nio.file.Path

object Constants {
    const val ACTION_DEBUG_LAYOUT_INFO = "com.bytedance.tools.codelocator.action_debug_layout_info"
    const val ACTION_CHANGE_VIEW_INFO = "com.bytedance.tools.codelocator.action_change_view_info"
    const val ACTION_GET_TOUCH_VIEW = "com.bytedance.tools.codelocator.action_get_touch_view"

    const val KEY_SHELL_ARGS = "codeLocator_shell_args"
    const val KEY_SAVE_TO_FILE = "codeLocator_save_to_file"
    const val KEY_NEED_COLOR = "codeLocator_need_color"
    const val KEY_CHANGE_VIEW = "codeLocator_change_view"

    const val RESULT_KEY_ERROR = "Error"
    const val RESULT_KEY_DATA = "Data"
    const val RESULT_KEY_PKG = "PN"
    const val RESULT_KEY_TARGET_CLASS = "TC"
    const val RESULT_KEY_FILE_PATH = "FP"
    const val RESULT_KEY_STACK = "ST"

    const val TYPE_OPERATE_VIEW = "V"
    const val EDIT_GET_VIEW_DATA = "GVD"
    const val EDIT_GET_VIEW_CLASS_INFO = "GVCI"
    const val EDIT_IGNORE = "X"

    const val TOOL_GRAB_UI_STATE = "grab_ui_state"
    const val TOOL_LOAD_LOCAL_GRAB = "load_local_grab"
    const val TOOL_LIST_GRABS = "list_grabs"
    const val TOOL_OPEN_WEB_VIEWER = "open_web_viewer"
    const val TOOL_GET_VIEW_DATA = "get_view_data"
    const val TOOL_GET_VIEW_CLASS_INFO = "get_view_class_info"
    const val TOOL_TRACE_TOUCH = "trace_touch"

    val home: Path = Path.of(System.getProperty("user.home"))
    val mcpRoot: Path = home.resolve(".codeLocator_mcp")
    val grabsRoot: Path = mcpRoot.resolve("grabs")
    val viewerRoot: Path = mcpRoot.resolve("viewer")
    val stateFile: Path = mcpRoot.resolve("state.json")
    val viewerStateFile: Path = viewerRoot.resolve("server.json")
    val historyDir: Path = home.resolve(".codeLocator_main").resolve("historyFile")

    val tempDir: File = mcpRoot.resolve("tmp").toFile()
}

class AdapterException(
    val code: String,
    override val message: String,
    val details: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null
) : RuntimeException(message, cause)
