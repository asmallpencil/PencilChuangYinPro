package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.ToolPermission

/**
 * 工具名 → 作用边界（与安卓端一致）。
 *
 * - [ToolPermission.WORKSPACE]：只在 agent 工作区内产生副作用；
 * - [ToolPermission.DANGEROUS]：工作区之外的对外副作用或不可逆操作；
 * - [ToolPermission.READ]：只读、无副作用（默认）。
 */
object ToolPermissions {

    private val WORKSPACE = setOf(
        ToolNames.WRITE_FILE,
        ToolNames.MAKE_DIR,
        ToolNames.COPY_FILE,
        ToolNames.MOVE_FILE,
        ToolNames.EXTRACT_ARCHIVE,
        ToolNames.CREATE_ARCHIVE,
        ToolNames.EDIT_IMAGE,
        ToolNames.DRAW_IMAGE,
    )

    private val DANGEROUS = setOf(
        ToolNames.DELETE_FILE,
        ToolNames.PRINT_TEXT,
        ToolNames.PRINT_TABLE,
        ToolNames.PRINT_IMAGE,
        ToolNames.PRINT_CONVERSATION,
        ToolNames.TEST_FEED,
        ToolNames.APP_NAVIGATE,
        ToolNames.PIXIV_DOWNLOAD_ILLUST,
        ToolNames.PIXIV_DOWNLOAD_NOVEL,
        ToolNames.RUN_COMMAND,
    )

    /** 查询工具的作用边界；未登记的工具按只读处理。 */
    fun of(name: String): ToolPermission = when (name) {
        in WORKSPACE -> ToolPermission.WORKSPACE
        in DANGEROUS -> ToolPermission.DANGEROUS
        else -> ToolPermission.READ
    }
}
