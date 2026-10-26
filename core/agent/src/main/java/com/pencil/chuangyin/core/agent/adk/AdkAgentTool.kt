@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.pencil.chuangyin.core.agent.adk

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext as AdkToolContext
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import com.pencil.chuangyin.core.agent.model.AgentTool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolContext
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.model.ToolPermission
import com.pencil.chuangyin.core.agent.model.ToolSpec
import com.pencil.chuangyin.core.agent.runtime.MutableToolCallScope
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 官方 Google ADK for Kotlin [FunctionTool] → 项目 [AgentTool] 适配器。
 *
 * 工具集类中的函数用官方 @Tool/@Param 注解声明，由官方 google-adk-kotlin-processor 在编译期
 * 生成 FunctionTool 包装器（含 FunctionDeclaration 形式的参数 Schema 与确认门）；本适配器负责：
 * 1. 把官方 [com.google.adk.kt.types.FunctionDeclaration] 的 Schema 转为 OpenAI JSON Schema；
 * 2. 把模型的 JsonObject 参数转为官方 execute 期望的 Map<String, Any>；
 * 3. 在每次调用前把项目 [ToolContext] 挂到 [ToolCallScope] 上，供工具函数体桥接会话/进度上报；
 * 4. 把官方确认门放行所需的 ToolConfirmation 直接置为已确认（不可逆操作的确认判定由
 *    AgentEngine 的 ToolConfirmationGate 会话级确认流承担，已在进入本适配器前放行）；
 * 5. 把官方 run() 的返回（mapOf(RESULT_KEY to ...) / mapOf(ERROR_KEY to ...)）解回 [ToolOutput]。
 */
class AdkAgentTool(
    private val delegate: FunctionTool,
    /** 适配器需要写入 current（桥接项目 ToolContext），因此声明为具体可变实现。 */
    private val scope: MutableToolCallScope,
    /** 工具触碰的边界：引擎据当前权限模式决定是否要求用户确认。 */
    permission: ToolPermission = ToolPermission.READ,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = delegate.name,
        description = delegate.description,
        parameters = delegate.declaration()?.parameters?.toJsonSchema() ?: EMPTY_OBJECT_SCHEMA,
        permission = permission,
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        scope.current = context
        try {
            val raw = delegate.run(newOfficialContext(), arguments.toJavaArgs())
            return raw.toToolOutput()
        } finally {
            scope.current = null
        }
    }

    /**
     * 构造官方 ToolContext。InvocationContext 仅作占位（工具函数体不使用官方会话服务），
     * 进程内共享；functionCallId 每次生成，满足官方确认门 requestConfirmation 的非空校验。
     */
    private fun newOfficialContext(): AdkToolContext = AdkToolContext(
        invocationContext = SHARED_INVOCATION_CONTEXT,
        functionCallId = "adk-" + UUID.randomUUID(),
        toolConfirmation = ToolConfirmation(confirmed = true),
    )

    private fun JsonElement.toJavaArgs(): Map<String, Any> {
        val obj = this as? JsonObject ?: return emptyMap()
        return buildMap {
            for ((key, value) in obj) {
                when (value) {
                    is JsonNull -> Unit
                    else -> value.toJavaAny()?.let { put(key, it) }
                }
            }
        }
    }

    private fun JsonElement.toJavaAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> toJavaPrimitive()
        is JsonArray -> mapNotNull { it.toJavaAny() }
        is JsonObject -> toJavaArgs()
    }

    /** 与官方生成器的参数提取强约束对齐：String→String、布尔→Boolean、整数→Number、其余→Double。 */
    private fun JsonPrimitive.toJavaPrimitive(): Any = when {
        isString -> content
        content == "true" -> true
        content == "false" -> false
        else -> content.toLongOrNull()
            ?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
            ?: content.toDoubleOrNull()
            ?: content
    }

    private fun Any.toToolOutput(): ToolOutput {
        if (this === Unit) return ToolOutput(UNIT_RESULT_MESSAGE)
        val map = this as? Map<*, *> ?: return ToolOutput(toString())
        val error = map[FunctionTool.ERROR_KEY]
        if (error != null) return ToolOutput(error.toString(), isError = true)
        return map[BaseTool.RESULT_KEY].toResultOutput()
    }

    private fun Any?.toResultOutput(): ToolOutput = when (this) {
        null -> ToolOutput("")
        is Map<*, *> -> toOutputMap()
        else -> ToolOutput(toString())
    }

    /** 工具函数返回的 ToolOutput 数据类被官方生成器序列化为「属性名 → 值」的 Map。 */
    private fun Map<*, *>.toOutputMap(): ToolOutput {
        val content = this["content"] as? String
            ?: return ToolOutput(toString())
        val isError = (this["isError"] as? Boolean) == true
        val artifacts = (this["artifacts"] as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.toToolArtifact() }
            ?: emptyList()
        return ToolOutput(content = content, isError = isError, artifacts = artifacts)
    }

    private fun Map<*, *>.toToolArtifact(): ToolArtifact? {
        val id = this["id"] as? String ?: return null
        val kind = this["kind"] as? String ?: return null
        val path = this["path"] as? String ?: return null
        return ToolArtifact(id = id, kind = kind, path = path, label = this["label"] as? String)
    }

    private companion object {
        val EMPTY_OBJECT_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
        }

        /**
         * 官方 InvocationContext 的最小占位实例：session/agent 仅需类型合法，
         * 工具执行路径不会触达官方会话与 Agent 运行时。
         */
        val SHARED_INVOCATION_CONTEXT: InvocationContext by lazy {
            InvocationContext(
                session = Session(key = SessionKey(appName = "chuangyin", userId = "agent", id = null)),
                agent = PlaceholderAgent,
            )
        }

        const val UNIT_RESULT_MESSAGE = "操作已完成"
    }

    /** [InvocationContext] 所需的最小 BaseAgent 实现，永不运行。 */
    private object PlaceholderAgent : BaseAgent(name = "chuangyin-agent") {
        override fun runAsyncImpl(context: InvocationContext): Flow<com.google.adk.kt.events.Event> =
            emptyFlow()
    }
}

/** 官方 [Schema] → OpenAI function calling 的 JSON Schema。 */
private fun Schema.toJsonSchema(): JsonObject = buildJsonObject {
    type?.let { put("type", it.wireName()) }
    description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
    enum?.takeIf { it.isNotEmpty() }?.let { values ->
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }
    properties?.takeIf { it.isNotEmpty() }?.let { props ->
        put(
            "properties",
            buildJsonObject { props.forEach { (name, schema) -> put(name, schema.toJsonSchema()) } },
        )
    }
    items?.let { put("items", it.toJsonSchema()) }
    required?.takeIf { it.isNotEmpty() }?.let { names ->
        put("required", JsonArray(names.map { JsonPrimitive(it) }))
    }
}

private fun Type.wireName(): String = when (this) {
    Type.STRING -> "string"
    Type.NUMBER -> "number"
    Type.INTEGER -> "integer"
    Type.BOOLEAN -> "boolean"
    Type.ARRAY -> "array"
    Type.OBJECT -> "object"
    else -> "string"
}
