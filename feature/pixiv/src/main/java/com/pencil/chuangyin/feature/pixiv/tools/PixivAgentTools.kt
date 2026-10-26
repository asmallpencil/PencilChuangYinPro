package com.pencil.chuangyin.feature.pixiv.tools

import com.pencil.chuangyin.core.agent.model.AgentTool
import com.pencil.chuangyin.core.agent.model.ToolArtifact
import com.pencil.chuangyin.core.agent.model.ToolArtifactKind
import com.pencil.chuangyin.core.agent.model.ToolContext
import com.pencil.chuangyin.core.agent.model.ToolOutput
import com.pencil.chuangyin.core.agent.model.ToolSpec
import com.pencil.chuangyin.feature.pixiv.core.auth.PixivAuthRepository
import com.pencil.chuangyin.feature.pixiv.data.PixivImageDownloader
import com.pencil.chuangyin.feature.pixiv.data.PixivRepository
import com.pencil.chuangyin.feature.pixiv.domain.PixivIllust
import com.pencil.chuangyin.feature.pixiv.domain.PixivNovel
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchSort
import com.pencil.chuangyin.feature.pixiv.domain.PixivSearchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P 站能力接入 AI Agent。
 *
 * 由 feature:pixiv 自行注册 6 个 pixiv_* 工具（[PixivAgentToolModule]），使 AI 能浏览推荐、
 * 搜索插画/小说、下载原图/正文，并与 print_image / app_navigate 串成完整链路
 * （如「跳转 P 站 → 下载主页第 N 张 → 打印 → 搜索并选最短小说下载」）。
 *
 * 工具契约来自 core:agent；参数解析与 Schema 构造在本模块内自洽实现，
 * 避免 feature:pixiv 反依赖 feature:ai（后者已依赖前者）。
 */

/** P 站工具名（取值与 feature:ai 的 ToolNames 严格一致）。 */
internal object PixivToolNames {
    const val HOME_FEED = "pixiv_home_feed"
    const val SEARCH_ILLUST = "pixiv_search_illust"
    const val ILLUST_DETAIL = "pixiv_illust_detail"
    const val DOWNLOAD_ILLUST = "pixiv_download_illust"
    const val SEARCH_NOVEL = "pixiv_search_novel"
    const val DOWNLOAD_NOVEL = "pixiv_download_novel"
}

/** P 站工具参数名（取值与 feature:ai 的 ToolParams 严格一致）。 */
private object Param {
    const val WORD = "word"
    const val SORT = "sort"
    const val TARGET = "target"
    const val LIMIT = "limit"
    const val ILLUST_ID = "illust_id"
    const val NOVEL_ID = "novel_id"
    const val PAGE_INDEX = "page_index"
}

/** 未登录提示（附带可跳转的登录目的地）。 */
private const val LOGIN_REQUIRED =
    "尚未登录 P 站，无法访问内容。请提示用户登录，或用 app_navigate 打开 pixiv_login 页面完成登录后重试。"

private const val DEFAULT_LIMIT = 10
private const val MAX_LIMIT = 30

/** 排序 / 检索目标的枚举取值（与 P 站 API 参数一致）。 */
private val PIXIV_SORTS: List<String> = PixivSearchSort.entries.map { it.apiValue }
private val PIXIV_TARGETS: List<String> = PixivSearchTarget.entries.map { it.apiValue }

/** OpenAI function calling 参数 Schema 轻量构造器（本模块自洽，避免依赖 feature:ai）。 */
private object PixivSchema {

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
}

// ── 参数解析辅助（宽容读取，类型不符时回退默认值） ──────────────────────────────

private fun JsonObject.pixivString(key: String): String? =
    runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()?.trim()?.ifEmpty { null }

private fun JsonObject.pixivInt(key: String, default: Int): Int =
    pixivIntOrNull(key) ?: default

private fun JsonObject.pixivIntOrNull(key: String): Int? =
    runCatching { get(key)?.jsonPrimitive?.intOrNull }.getOrNull()
        ?: runCatching { get(key)?.jsonPrimitive?.contentOrNull?.trim()?.toDouble()?.toInt() }.getOrNull()

private fun JsonObject.pixivSort(): PixivSearchSort {
    val raw = pixivString(Param.SORT)?.lowercase() ?: return PixivSearchSort.DATE_DESC
    return PixivSearchSort.entries.firstOrNull { it.apiValue == raw } ?: PixivSearchSort.DATE_DESC
}

private fun JsonObject.pixivTarget(): PixivSearchTarget {
    val raw = pixivString(Param.TARGET)?.lowercase() ?: return PixivSearchTarget.PARTIAL_TAG
    return PixivSearchTarget.entries.firstOrNull { it.apiValue == raw } ?: PixivSearchTarget.PARTIAL_TAG
}

private fun pixivFailure(message: String): ToolOutput = ToolOutput(message, isError = true)

/** 统一执行壳：上报进度、把网络/解析异常转成可读的工具失败结果（取消异常照常抛出）。 */
private suspend inline fun runPixiv(
    context: ToolContext,
    progress: String,
    block: () -> ToolOutput,
): ToolOutput {
    context.reportProgress(progress)
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        pixivFailure("P 站操作失败：${e.message ?: "未知错误，请稍后重试。"}")
    }
}

// ── 列表渲染 ────────────────────────────────────────────────────────────────

private fun renderIllustList(title: String, items: List<PixivIllust>, limit: Int): String {
    if (items.isEmpty()) return "$title：（没有结果）"
    val shown = items.take(limit)
    return buildString {
        appendLine("$title（共 ${items.size} 条，展示前 ${shown.size} 条；序号即页面上从左到右、从上到下的顺序）：")
        shown.forEachIndexed { index, illust ->
            appendLine(
                "${index + 1}. [illust_id=${illust.id}] ${illust.title} — ${illust.user.name}" +
                    " · ${illust.pageCount} 页 · 收藏 ${illust.totalBookmarks} · 类型 ${illust.type}"
            )
        }
        appendLine("提示：下载原图调用 pixiv_download_illust（illust_id，可选 page_index）；下载后可 print_image 打印。")
    }.trim()
}

private fun renderNovelList(title: String, items: List<PixivNovel>, limit: Int): String {
    if (items.isEmpty()) return "$title：（没有结果）"
    val shown = items.take(limit)
    return buildString {
        appendLine("$title（共 ${items.size} 条，展示前 ${shown.size} 条）：")
        shown.forEachIndexed { index, novel ->
            appendLine(
                "${index + 1}. [novel_id=${novel.id}] ${novel.title} — ${novel.user?.name ?: "未知作者"}" +
                    " · 正文约 ${novel.textLength} 字 · 收藏 ${novel.totalBookmarks}"
            )
        }
        appendLine("提示：如需「最短的一篇」，按上面的字数选最小者，再调用 pixiv_download_novel（novel_id）。")
    }.trim()
}

private fun renderIllustDetail(illust: PixivIllust): String = buildString {
    appendLine("作品详情：")
    appendLine("- illust_id：${illust.id}")
    appendLine("- 标题：${illust.title}")
    appendLine("- 作者：${illust.user.name}（@${illust.user.account}）")
    appendLine("- 类型：${illust.type} · 页数：${illust.pageCount} · 尺寸：${illust.width}×${illust.height}")
    appendLine("- 发布时间：${illust.createDate} · 浏览：${illust.totalView} · 收藏：${illust.totalBookmarks}")
    if (illust.tags.isNotEmpty()) {
        appendLine("- 标签：" + illust.tags.take(20).joinToString("、") { it.name })
    }
    appendLine("- 各页原图链接：")
    illust.pages.forEachIndexed { index, page -> appendLine("  第 ${index + 1} 页：${page.original}") }
    if (illust.caption.isNotBlank()) {
        appendLine("- 简介：" + illust.caption.take(300).replace("\n", " "))
    }
}.trim()

// ── 工具实现 ────────────────────────────────────────────────────────────────

/** 获取 P 站主页推荐列表（带序号），供 AI 定位「主页第 N 张」。 */
@Singleton
class PixivHomeFeedTool @Inject constructor(
    private val repository: PixivRepository,
    private val auth: PixivAuthRepository,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = PixivToolNames.HOME_FEED,
        description = "获取 P 站主页推荐作品列表（带序号）。用户说「主页第 N 张」「右边第三张」时即按返回顺序计数。" +
            "拿到 illust_id 后用 pixiv_download_illust 下载原图，再 print_image 打印。",
        parameters = PixivSchema.parameters(
            required = emptyList(),
            properties = mapOf(
                Param.LIMIT to PixivSchema.integer("返回条数，默认 $DEFAULT_LIMIT，范围 1-$MAX_LIMIT。"),
            ),
        ),
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        if (!auth.isLoggedIn()) return pixivFailure(LOGIN_REQUIRED)
        val limit = arguments.pixivInt(Param.LIMIT, DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return runPixiv(context, "获取 P 站主页推荐…") {
            val feed = repository.recommended()
            ToolOutput(renderIllustList("P 站主页推荐", feed.items, limit))
        }
    }
}

/** 按关键词搜索 P 站插画/漫画。 */
@Singleton
class PixivSearchIllustTool @Inject constructor(
    private val repository: PixivRepository,
    private val auth: PixivAuthRepository,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = PixivToolNames.SEARCH_ILLUST,
        description = "按关键词搜索 P 站插画/漫画，返回带序号的候选列表（含 illust_id），供 AI 自主挑选满足要求的作品。",
        parameters = PixivSchema.parameters(
            required = listOf(Param.WORD),
            properties = mapOf(
                Param.WORD to PixivSchema.string("搜索关键词，如「猫」「风景」。"),
                Param.SORT to PixivSchema.string("排序。", enumValues = PIXIV_SORTS),
                Param.TARGET to PixivSchema.string("匹配范围。", enumValues = PIXIV_TARGETS),
                Param.LIMIT to PixivSchema.integer("返回条数，默认 $DEFAULT_LIMIT，范围 1-$MAX_LIMIT。"),
            ),
        ),
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        val word = arguments.pixivString(Param.WORD)
            ?: return pixivFailure("缺少搜索关键词 word。")
        if (!auth.isLoggedIn()) return pixivFailure(LOGIN_REQUIRED)
        val sort = arguments.pixivSort()
        val target = arguments.pixivTarget()
        val limit = arguments.pixivInt(Param.LIMIT, DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return runPixiv(context, "搜索「$word」…") {
            val result = repository.search(word = word, sort = sort, target = target)
            ToolOutput(renderIllustList("插画搜索「$word」", result.items, limit))
        }
    }
}

/** 获取单个插画作品的详情（含各页原图 URL）。 */
@Singleton
class PixivIllustDetailTool @Inject constructor(
    private val repository: PixivRepository,
    private val auth: PixivAuthRepository,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = PixivToolNames.ILLUST_DETAIL,
        description = "获取 P 站作品详情：标题、作者、标签、页数与每一页的原图链接。用于下载前确认作品信息。",
        parameters = PixivSchema.parameters(
            required = listOf(Param.ILLUST_ID),
            properties = mapOf(
                Param.ILLUST_ID to PixivSchema.integer("作品 ID（来自列表结果的 illust_id）。"),
            ),
        ),
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        val illustId = arguments.pixivInt(Param.ILLUST_ID, 0)
        if (illustId <= 0) return pixivFailure("illust_id 无效，请提供正确的作品 ID。")
        if (!auth.isLoggedIn()) return pixivFailure(LOGIN_REQUIRED)
        return runPixiv(context, "获取作品详情…") {
            ToolOutput(renderIllustDetail(repository.detail(illustId)))
        }
    }
}

/** 下载插画作品原图到本地，返回可直接打印的文件路径。 */
@Singleton
class PixivDownloadIllustTool @Inject constructor(
    private val repository: PixivRepository,
    private val downloader: PixivImageDownloader,
    private val auth: PixivAuthRepository,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = PixivToolNames.DOWNLOAD_ILLUST,
        description = "下载 P 站作品原图到本地并返回文件绝对路径（可直接用于 print_image 打印）。" +
            "page_index 为 1 基页码：只下载指定那一页；省略则下载全部页。" +
            "只想打印封面/某一张时传 page_index=1。",
        parameters = PixivSchema.parameters(
            required = listOf(Param.ILLUST_ID),
            properties = mapOf(
                Param.ILLUST_ID to PixivSchema.integer("作品 ID（列表结果的 illust_id）。"),
                Param.PAGE_INDEX to PixivSchema.integer("要下载的页码，从 1 开始；省略则下载全部页。"),
            ),
        ),
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        val illustId = arguments.pixivInt(Param.ILLUST_ID, 0)
        if (illustId <= 0) return pixivFailure("illust_id 无效，请提供正确的作品 ID。")
        if (!auth.isLoggedIn()) return pixivFailure(LOGIN_REQUIRED)
        val pageIndex = arguments.pixivIntOrNull(Param.PAGE_INDEX)
        return runPixiv(context, "下载作品原图…") {
            val illust = repository.detail(illustId)
            if (illust.pages.isEmpty()) return@runPixiv pixivFailure("该作品没有可下载的图片页面。")
            val indices = if (pageIndex != null) {
                if (pageIndex !in 1..illust.pages.size) {
                    return@runPixiv pixivFailure("页码 $pageIndex 超出范围（该作品共 ${illust.pages.size} 页）。")
                }
                listOf(pageIndex - 1)
            } else {
                illust.pages.indices.toList()
            }
            val files = downloader.downloadPages(illust, indices) { done, total ->
                context.reportProgress("下载第 $done/$total 张…")
            }
            val artifacts = files.map { file ->
                ToolArtifact(
                    id = "pixiv_${illust.id}_${file.name}",
                    kind = ToolArtifactKind.IMAGE,
                    path = file.absolutePath,
                    label = "${illust.title} · ${file.name}",
                )
            }
            ToolOutput(
                content = buildString {
                    appendLine("已下载《${illust.title}》（illust_id=${illust.id}）共 ${files.size} 张原图：")
                    files.forEachIndexed { index, file -> appendLine("${index + 1}. ${file.absolutePath}") }
                    appendLine("可直接调用 print_image 打印上述路径；如需长期保存可用 copy_file 复制到工作区。")
                }.trim(),
                artifacts = artifacts,
            )
        }
    }
}

/** 按关键词搜索 P 站小说（含正文字数，便于选最短一篇）。 */
@Singleton
class PixivSearchNovelTool @Inject constructor(
    private val repository: PixivRepository,
    private val auth: PixivAuthRepository,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = PixivToolNames.SEARCH_NOVEL,
        description = "按关键词搜索 P 站小说，返回带序号的候选列表（含 novel_id 与正文字数 textLength）。" +
            "用户要求「选最短的一篇」时，按返回的字数选最小者。",
        parameters = PixivSchema.parameters(
            required = listOf(Param.WORD),
            properties = mapOf(
                Param.WORD to PixivSchema.string("搜索关键词。"),
                Param.SORT to PixivSchema.string("排序。", enumValues = PIXIV_SORTS),
                Param.TARGET to PixivSchema.string("匹配范围。", enumValues = PIXIV_TARGETS),
                Param.LIMIT to PixivSchema.integer("返回条数，默认 $DEFAULT_LIMIT，范围 1-$MAX_LIMIT。"),
            ),
        ),
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        val word = arguments.pixivString(Param.WORD)
            ?: return pixivFailure("缺少搜索关键词 word。")
        if (!auth.isLoggedIn()) return pixivFailure(LOGIN_REQUIRED)
        val sort = arguments.pixivSort()
        val target = arguments.pixivTarget()
        val limit = arguments.pixivInt(Param.LIMIT, DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return runPixiv(context, "搜索小说「$word」…") {
            val result = repository.searchNovel(word = word, sort = sort, target = target)
            ToolOutput(renderNovelList("小说搜索「$word」", result.items, limit))
        }
    }
}

/** 下载小说正文为本地文本文件。 */
@Singleton
class PixivDownloadNovelTool @Inject constructor(
    private val repository: PixivRepository,
    private val auth: PixivAuthRepository,
) : AgentTool {

    override val spec: ToolSpec = ToolSpec(
        name = PixivToolNames.DOWNLOAD_NOVEL,
        description = "下载 P 站小说正文为本地文本文件（.txt，含标题与正文），返回文件绝对路径，" +
            "并可继续 read_file 阅读或用 print_text 打印。novel_id 来自 pixiv_search_novel 结果。",
        parameters = PixivSchema.parameters(
            required = listOf(Param.NOVEL_ID),
            properties = mapOf(
                Param.NOVEL_ID to PixivSchema.integer("小说 ID（pixiv_search_novel 结果的 novel_id）。"),
            ),
        ),
    )

    override suspend fun invoke(arguments: JsonObject, context: ToolContext): ToolOutput {
        val novelId = arguments.pixivInt(Param.NOVEL_ID, 0)
        if (novelId <= 0) return pixivFailure("novel_id 无效，请提供正确的小说 ID。")
        if (!auth.isLoggedIn()) return pixivFailure(LOGIN_REQUIRED)
        return runPixiv(context, "下载小说…") {
            val novel = repository.novelDetail(novelId)
            context.reportProgress("拉取《${novel.title}》正文…")
            val text = repository.novelText(novelId)
            val file = repository.downloadNovel(novel, text)
            ToolOutput(
                content = buildString {
                    appendLine("已下载小说《${novel.title}》")
                    appendLine("- 文件：${file.absolutePath}")
                    appendLine("- 正文约 ${text.length} 字")
                    if (text.isNotBlank()) {
                        appendLine("- 开头预览：" + text.take(200).replace("\n", " "))
                    }
                }.trim(),
                artifacts = listOf(
                    ToolArtifact(
                        id = "pixiv_novel_${novel.id}",
                        kind = ToolArtifactKind.FILE,
                        path = file.absolutePath,
                        label = novel.title,
                    ),
                ),
            )
        }
    }
}
