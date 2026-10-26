package com.pencil.chuangyin.desktop.ai.tools

import com.pencil.chuangyin.desktop.ai.AgentTool
import com.pencil.chuangyin.desktop.ai.ToolContext
import com.pencil.chuangyin.desktop.ai.ToolOutput
import com.pencil.chuangyin.desktop.ai.ToolPermission
import com.pencil.chuangyin.desktop.ai.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI function calling 的参数 Schema 轻量构造器。
 *
 * 安卓端由 ADK 的 `@Tool` / `@Param` 注解 + KSP 在编译期生成，桌面端没有这条链路，
 * 因此这里手写等价的最小 DSL，保证同一批工具的参数声明与原实现一致。
 */
internal object ToolSchema {

    fun parameters(required: List<String>, properties: Map<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }

    fun string(description: String, enumValues: List<String>? = null): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        enumValues?.let { values ->
            put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        }
    }

    fun integer(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    fun number(description: String): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    fun boolean(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    fun stringArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        putJsonObject("items") { put("type", "string") }
    }

    fun objectArray(description: String, properties: Map<String, JsonObject>, required: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", "array")
            put("description", description)
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
                put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
            }
        }
}

/**
 * 把 suspend lambda 包成 [AgentTool]。
 *
 * [permission] 默认按 [ToolPermissions] 里的登记推导，保证与安卓端同一套边界判定。
 */
internal fun agentTool(
    name: String,
    description: String,
    parameters: JsonObject,
    permission: ToolPermission = ToolPermissions.of(name),
    invoke: suspend (JsonObject, ToolContext) -> ToolOutput,
): AgentTool = SimpleTool(ToolSpec(name, description, parameters, permission), invoke)

private class SimpleTool(
    override val spec: ToolSpec,
    private val block: suspend (JsonObject, ToolContext) -> ToolOutput,
) : AgentTool {
    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput = block(arguments, context)
}

/**
 * 统一执行壳：上报进度、把异常转成可读的工具失败结果（取消异常照常抛出）。
 */
internal suspend fun safeTool(
    context: ToolContext,
    progress: String,
    block: suspend () -> ToolOutput,
): ToolOutput {
    context.reportProgress(progress)
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ToolOutput("操作失败：${e.message ?: "未知错误，请稍后重试。"}", isError = true)
    }
}

/** 工具失败的统一返回。 */
internal fun failure(message: String): ToolOutput = ToolOutput(message, isError = true)

/** 把 [Workspace] 抛出的错误转为工具结果。 */
internal fun failure(error: Throwable): ToolOutput =
    ToolOutput(error.message ?: error.javaClass.simpleName, isError = true)

/** 人类可读的字节数。 */
internal fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
