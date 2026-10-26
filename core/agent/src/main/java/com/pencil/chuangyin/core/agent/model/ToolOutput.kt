package com.pencil.chuangyin.core.agent.model

import kotlinx.serialization.Serializable

/** 工具产物的类型常量。 */
object ToolArtifactKind {
    /** 可直接预览的图片。 */
    const val IMAGE = "image"

    /** 一般文件。 */
    const val FILE = "file"
}

/** 工具执行产生的可展示产物。 */
@Serializable
data class ToolArtifact(
    val id: String,
    /** 产物类型，取值见 [ToolArtifactKind]。 */
    val kind: String,
    /** 本地绝对路径。 */
    val path: String,
    /** 展示用名称。 */
    val label: String? = null,
)

/** 工具执行结果。 */
data class ToolOutput(
    val content: String,
    val isError: Boolean = false,
    val artifacts: List<ToolArtifact> = emptyList(),
)
