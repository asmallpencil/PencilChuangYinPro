package com.pencil.chuangyin.feature.ai.tools

import com.pencil.chuangyin.core.agent.model.ToolPermission

/**
 * 工具名 → 作用边界。
 *
 * 引擎不再按工具写死「是否确认」，而是结合用户当前权限模式与这里声明的作用域动态判定，
 * 因此每个工具只需标注它触碰的边界：
 * - [ToolPermission.WORKSPACE]：只在 agent 工作区内产生副作用（写、建目录、复制、移动、解压、绘图）；
 * - [ToolPermission.DANGEROUS]：工作区之外的对外副作用或不可逆操作（删除、打印、跳转、下载、执行命令）；
 * - [ToolPermission.READ]：只读、无副作用（默认），含浏览、读取、查询状态、联网检索。
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
        ToolNames.EXTRACT_VIDEO_FRAMES,
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
        ToolNames.SHELL_SETUP,
    )

    /** 查询工具的作用边界；未登记的工具按只读处理。 */
    fun of(name: String): ToolPermission = when (name) {
        in WORKSPACE -> ToolPermission.WORKSPACE
        in DANGEROUS -> ToolPermission.DANGEROUS
        else -> ToolPermission.READ
    }
}
