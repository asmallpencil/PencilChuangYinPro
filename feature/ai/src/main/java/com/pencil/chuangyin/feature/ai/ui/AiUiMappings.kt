package com.pencil.chuangyin.feature.ai.ui

import androidx.annotation.StringRes
import com.pencil.chuangyin.core.agent.model.AgentErrorKind
import com.pencil.chuangyin.feature.ai.R
import com.pencil.chuangyin.feature.ai.tools.ToolNames

/**
 * 工具名 → 界面文案资源。
 *
 * 工具是模型可见的「实现细节」，直接展示英文标识对用户没有意义，因此统一翻译成动词短语。
 * 未收录的工具回退到 [R.string.ai_tool_unknown]。
 */
@StringRes
internal fun toolLabelRes(toolName: String?): Int = when (toolName) {
    ToolNames.LIST_FILES -> R.string.ai_tool_list_files
    ToolNames.READ_FILE -> R.string.ai_tool_read_file
    ToolNames.WRITE_FILE -> R.string.ai_tool_write_file
    ToolNames.DELETE_FILE -> R.string.ai_tool_delete_file
    ToolNames.SEARCH_FILES -> R.string.ai_tool_search_files
    ToolNames.PRINT_TEXT -> R.string.ai_tool_print_text
    ToolNames.PRINT_IMAGE -> R.string.ai_tool_print_image
    ToolNames.PRINT_CONVERSATION -> R.string.ai_tool_print_conversation
    ToolNames.PRINTER_STATUS -> R.string.ai_tool_printer_status
    ToolNames.TEST_FEED -> R.string.ai_tool_test_feed
    ToolNames.DRAW_IMAGE -> R.string.ai_tool_draw_image
    ToolNames.MAKE_DIR -> R.string.ai_tool_make_dir
    ToolNames.COPY_FILE -> R.string.ai_tool_copy_file
    ToolNames.MOVE_FILE -> R.string.ai_tool_move_file
    ToolNames.FILE_INFO -> R.string.ai_tool_file_info
    ToolNames.EXTRACT_ARCHIVE -> R.string.ai_tool_extract_archive
    ToolNames.CREATE_ARCHIVE -> R.string.ai_tool_create_archive
    ToolNames.EDIT_IMAGE -> R.string.ai_tool_edit_image
    ToolNames.PRINT_TABLE -> R.string.ai_tool_print_table
    ToolNames.APP_NAVIGATE -> R.string.ai_tool_app_navigate
    ToolNames.APP_LIST_DESTINATIONS -> R.string.ai_tool_app_list_destinations
    ToolNames.PIXIV_HOME_FEED -> R.string.ai_tool_pixiv_home_feed
    ToolNames.PIXIV_SEARCH_ILLUST -> R.string.ai_tool_pixiv_search_illust
    ToolNames.PIXIV_ILLUST_DETAIL -> R.string.ai_tool_pixiv_illust_detail
    ToolNames.PIXIV_DOWNLOAD_ILLUST -> R.string.ai_tool_pixiv_download_illust
    ToolNames.PIXIV_SEARCH_NOVEL -> R.string.ai_tool_pixiv_search_novel
    ToolNames.PIXIV_DOWNLOAD_NOVEL -> R.string.ai_tool_pixiv_download_novel
    ToolNames.WEB_SEARCH -> R.string.ai_tool_web_search
    ToolNames.WEB_FETCH -> R.string.ai_tool_web_fetch
    ToolNames.RUN_COMMAND -> R.string.ai_tool_run_command
    ToolNames.SHELL_SETUP -> R.string.ai_tool_shell_setup
    ToolNames.SHELL_STATUS -> R.string.ai_tool_shell_status
    else -> R.string.ai_tool_unknown
}

/** 错误类别 → 界面文案资源。 */
@StringRes
internal fun agentErrorRes(kind: AgentErrorKind): Int = when (kind) {
    AgentErrorKind.MissingConfig -> R.string.ai_error_missing_config
    AgentErrorKind.Network -> R.string.ai_error_network
    AgentErrorKind.Http -> R.string.ai_error_http
    AgentErrorKind.Protocol -> R.string.ai_error_protocol
    AgentErrorKind.Tool -> R.string.ai_error_tool
    AgentErrorKind.Cancelled -> R.string.ai_error_cancelled
    AgentErrorKind.Unknown -> R.string.ai_error_unknown
}
