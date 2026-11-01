package com.pencil.chuangyin.core.agent.config

import kotlinx.coroutines.flow.StateFlow

/** 模型配置仓库。 */
interface AiConfigRepository {

    /** 已保存的全部模型配置。 */
    val models: StateFlow<List<AiModelConfig>>

    /** 当前选中的模型 ID。 */
    val activeModelId: StateFlow<String?>

    /** 当前选中的模型配置；未选中时为 null。 */
    val activeModel: StateFlow<AiModelConfig?>

    /**
     * 新增或更新模型配置。
     *
     * [AiModelConfig.id] 为空时按新增处理，由仓库分配 ID。
     * @return 落库后的配置（含最终 ID）。
     */
    suspend fun upsert(model: AiModelConfig): AiModelConfig

    /** 删除指定模型；若删除的是当前选中项，会自动切换到剩余的第一项。 */
    suspend fun remove(id: String)

    /** 切换当前选中的模型；传入 null 或非法 ID 时视为清空。 */
    suspend fun setActive(id: String?)

    /**
     * 开发者调试用的系统提示词覆盖：非空时替换内置提示词（仅调试页可写）。
     * 生产路径为空，行为与内置提示词完全一致。
     */
    val systemPromptOverride: StateFlow<String?>

    /** 保存/清除提示词覆盖（null 或空白即恢复内置提示词）。 */
    suspend fun setSystemPromptOverride(value: String?)
}
