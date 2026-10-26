package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool

/**
 * 工具装配入口（对齐安卓端的 Hilt 多绑定 `@IntoSet` 聚合）。
 *
 * 桌面端没有依赖注入框架，所有工具在这里一次性构造并按固定顺序汇总；
 * [com.pencil.chuangyin.desktop.ai.ToolRegistry] 内部会按名称再排序，因此顺序只影响可读性。
 */
object AgentTools {

    /**
     * 构造本轮会话可用的全部工具。
     *
     * @param workspace 工具共享的存储沙箱（产物目录 / 可读根）。
     */
    fun build(workspace: Workspace): Set<AgentTool> {
        val shellRuntime = ShellRuntime(workspace)
        return buildSet {
            addAll(FileTools(workspace).tools)
            addAll(AdvancedFileTools(workspace).tools)
            addAll(ArchiveTools(workspace).tools)
            addAll(ImageTools(workspace).tools)
            addAll(DrawTools(workspace).tools)
            addAll(TableTools(workspace).tools)
            addAll(PrintTools(workspace).tools)
            addAll(WebTools().tools)
            addAll(ShellTools(shellRuntime, workspace).tools)
            addAll(AppTools().tools)
        }
    }
}
