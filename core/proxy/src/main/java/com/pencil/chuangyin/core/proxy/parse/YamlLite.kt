package com.pencil.chuangyin.core.proxy.parse

/**
 * 极简 YAML 子集解析器。
 *
 * **为什么自研**：Clash 订阅只用到「缩进映射 / 缩进序列 / 行内流式列表 / 引号标量 / 块标量」
 * 这几种结构，引入完整 YAML 库（如 SnakeYAML）会显著增大包体，并与其对 Android 的兼容性
 * 维护成本不成正比。本解析器只实现配置真正需要的语法，遇到不支持的写法会保留原始文本，
 * 由上层 [ClashConfigParser] 决定丢弃或降级。
 *
 * 支持：
 * - `key: value`、嵌套映射、`- item` 序列、`- key: value` 行内首键；
 * - `[a, b]` / `{a: b}` 流式集合（含嵌套与引号）；
 * - `"..."` / `'...'` 引号标量（双引号支持 `\n`、`\"`、`\\`）；
 * - `|` / `>` 块标量；
 * - `#` 行尾注释（引号内不算）。
 */
internal sealed interface YamlValue {

    data class Obj(val entries: Map<String, YamlValue>) : YamlValue

    data class Arr(val items: List<YamlValue>) : YamlValue

    data class Scalar(val value: String) : YamlValue

    data object Null : YamlValue
}

// 访问器一律声明为「可空接收者」扩展：配置里缺失的键直接返回 null，
// 调用方无需层层写 `?.`，也不会因为漏判空而崩溃。
internal fun YamlValue?.asObject(): Map<String, YamlValue>? = (this as? YamlValue.Obj)?.entries

internal fun YamlValue?.asArray(): List<YamlValue>? = (this as? YamlValue.Arr)?.items

internal fun YamlValue?.asString(): String? = (this as? YamlValue.Scalar)?.value

internal fun YamlValue?.asInt(): Int? = asString()?.trim()?.toIntOrNull()

internal fun YamlValue?.asLong(): Long? = asString()?.trim()?.toLongOrNull()

internal fun YamlValue?.asBoolean(): Boolean? = when (val value = asString()?.trim()?.lowercase()) {
    "true", "yes", "on", "1" -> true
    "false", "no", "off", "0" -> false
    else -> null
}

internal object YamlLite {

    fun parse(source: String): YamlValue = Parser(source).parse()
}

private class Parser(source: String) {

    private data class Line(val indent: Int, val text: String)

    private val lines: List<Line> = preprocess(source)

    private var pos = 0

    fun parse(): YamlValue {
        if (lines.isEmpty()) return YamlValue.Null
        return parseBlock(lines[0].indent)
    }

    // ---------------- 预处理 ----------------

    private fun preprocess(source: String): List<Line> {
        val raw = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val withExpanded = raw.flatMap { expandSequenceItem(it) }
        return withExpanded.mapNotNull { rawLine ->
            var line = rawLine.removePrefix("\uFEFF").replace("\t", "  ")
            line = stripComment(line)
            if (line.isBlank()) return@mapNotNull null
            val trimmed = line.trimEnd()
            val content = trimmed.trimStart()
            if (content == "---" || content == "...") return@mapNotNull null
            val indent = trimmed.length - content.length
            Line(indent, content)
        }
    }

    /**
     * 把 `- name: value` 拆成 `-` + 下一行 `name: value`，让后续解析只处理
     * 「序列项 = 独立块」这一种形态，避免行内首键与后续同级键的缩进耦合。
     */
    private fun expandSequenceItem(rawLine: String): List<String> {
        val line = rawLine.removePrefix("\uFEFF").replace("\t", "  ")
        val content = stripComment(line).trimEnd()
        val body = content.trimStart()
        if (!isDash(body)) return listOf(content)
        val afterDash = body.substring(1)
        val rest = afterDash.trim()
        if (rest.isEmpty()) return listOf(content)
        // 流式集合 / 纯标量不展开，保持单行。
        if (splitKeyValue(rest) == null) return listOf(content)
        val indent = content.length - body.length
        val offset = 1 + (afterDash.length - afterDash.trimStart().length)
        return listOf(
            " ".repeat(indent) + "-",
            " ".repeat(indent + offset) + rest,
        )
    }

    private fun isDash(text: String): Boolean = text == "-" || text.startsWith("- ")

    /** 去掉引号外的 `#` 注释（要求 `#` 前为空白，避免误伤 URL fragment）。 */
    private fun stripComment(line: String): String {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && inDouble -> i++
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                c == '#' && !inSingle && !inDouble -> {
                    if (i == 0 || line[i - 1].isWhitespace()) return line.substring(0, i)
                }
            }
            i++
        }
        return line
    }

    // ---------------- 块解析 ----------------

    private fun parseBlock(indent: Int): YamlValue {
        if (pos >= lines.size) return YamlValue.Null
        return if (isDash(lines[pos].text)) parseSequence(indent) else parseMapping(indent)
    }

    private fun parseSequence(indent: Int): YamlValue {
        val items = mutableListOf<YamlValue>()
        while (pos < lines.size) {
            val line = lines[pos]
            if (line.indent != indent || !isDash(line.text)) break
            val rest = if (line.text == "-") "" else line.text.substring(1).trim()
            pos++
            when {
                rest.isEmpty() -> {
                    val next = lines.getOrNull(pos)
                    items += if (next != null && next.indent > indent) parseBlock(next.indent) else YamlValue.Null
                }
                splitKeyValue(rest) != null -> {
                    // 理论上预处理已经拆开，这里兜底保证不会把 map 当成标量。
                    val map = linkedMapOf<String, YamlValue>()
                    val kv = splitKeyValue(rest)!!
                    parsePair(map, kv.first, kv.second, indent)
                    items += YamlValue.Obj(map)
                }
                else -> items += parseFlowOrScalar(rest)
            }
        }
        return YamlValue.Arr(items)
    }

    private fun parseMapping(indent: Int): YamlValue {
        val map = linkedMapOf<String, YamlValue>()
        while (pos < lines.size) {
            val line = lines[pos]
            if (line.indent != indent || isDash(line.text)) break
            val kv = splitKeyValue(line.text)
            if (kv == null) {
                pos++
                continue
            }
            pos++
            parsePair(map, kv.first, kv.second, indent)
        }
        return YamlValue.Obj(map)
    }

    private fun parsePair(
        map: MutableMap<String, YamlValue>,
        rawKey: String,
        rawRest: String,
        indent: Int,
    ) {
        val key = unquote(rawKey)
        val rest = rawRest.trim()
        val next = lines.getOrNull(pos)
        when {
            rest.isEmpty() -> {
                map[key] = when {
                    next == null -> YamlValue.Null
                    next.indent > indent -> parseBlock(next.indent)
                    // YAML 允许序列与键同缩进（rules:\n- DOMAIN,...）。
                    next.indent == indent && isDash(next.text) -> parseSequence(indent)
                    else -> YamlValue.Null
                }
            }
            rest == "|" || rest == "|-" || rest == "|+" || rest == ">" || rest == ">-" || rest == ">+" -> {
                val sb = StringBuilder()
                while (pos < lines.size && lines[pos].indent > indent) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(lines[pos].text)
                    pos++
                }
                map[key] = YamlValue.Scalar(sb.toString())
            }
            else -> map[key] = parseFlowOrScalar(rest)
        }
    }

    private fun parseFlowOrScalar(text: String): YamlValue {
        val t = text.trim()
        return when {
            t.startsWith("[") && t.endsWith("]") ->
                YamlValue.Arr(splitFlow(t.substring(1, t.length - 1)).map { parseFlowOrScalar(it) })
            t.startsWith("{") && t.endsWith("}") -> {
                val map = linkedMapOf<String, YamlValue>()
                splitFlow(t.substring(1, t.length - 1)).forEach { item ->
                    val kv = splitKeyValue(item)
                    if (kv != null) map[unquote(kv.first)] = parseFlowOrScalar(kv.second)
                    else if (item.isNotBlank()) map[unquote(item)] = YamlValue.Null
                }
                YamlValue.Obj(map)
            }
            t.isEmpty() || t == "~" || t.equals("null", ignoreCase = true) -> YamlValue.Null
            else -> YamlValue.Scalar(unquote(t))
        }
    }

    /** 按顶层逗号切分流式集合，引号与嵌套括号内的逗号不切。 */
    private fun splitFlow(text: String): List<String> {
        val result = mutableListOf<String>()
        var inSingle = false
        var inDouble = false
        var depth = 0
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && inDouble -> {
                    sb.append(c)
                    if (i + 1 < text.length) {
                        sb.append(text[i + 1])
                        i++
                    }
                }
                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    sb.append(c)
                }
                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    sb.append(c)
                }
                inSingle || inDouble -> sb.append(c)
                c == '[' || c == '{' -> {
                    depth++
                    sb.append(c)
                }
                c == ']' || c == '}' -> {
                    depth--
                    sb.append(c)
                }
                c == ',' && depth == 0 -> {
                    result += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotBlank()) result += sb.toString().trim()
        return result.filter { it.isNotEmpty() }
    }
}

/**
 * 找到顶层第一个 `key: value` 的冒号（引号与括号内不算，冒号后必须是空白或行尾）。
 * 找不到时返回 null，表示该行是纯标量。
 */
private fun splitKeyValue(text: String): Pair<String, String>? {
    val trimmed = text.trim()
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) return null
    var inSingle = false
    var inDouble = false
    var depth = 0
    var i = 0
    while (i < trimmed.length) {
        val c = trimmed[i]
        when {
            c == '\\' && inDouble -> i++
            c == '\'' && !inDouble -> inSingle = !inSingle
            c == '"' && !inSingle -> inDouble = !inDouble
            inSingle || inDouble -> Unit
            c == '[' || c == '{' -> depth++
            c == ']' || c == '}' -> depth--
            c == ':' && depth == 0 -> {
                val next = trimmed.getOrNull(i + 1)
                if (next == null || next.isWhitespace()) {
                    val key = trimmed.substring(0, i).trim()
                    if (key.isEmpty()) return null
                    return key to trimmed.substring(i + 1).trim()
                }
            }
        }
        i++
    }
    return null
}

private fun unquote(value: String): String {
    val t = value.trim()
    if (t.length >= 2 && t.first() == '\'' && t.last() == '\'') {
        return t.substring(1, t.length - 1).replace("''", "'")
    }
    if (t.length >= 2 && t.first() == '"' && t.last() == '"') {
        val body = t.substring(1, t.length - 1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (val n = body[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> {
                        sb.append('\\')
                        sb.append(n)
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
    return t
}
