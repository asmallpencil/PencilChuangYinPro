package com.pencil.chuangyin.feature.barcode.scan

import org.json.JSONArray
import org.json.JSONObject

/** 识别模式：二维矩阵码 / 一维条码。 */
enum class ScanMode {
    Qr,
    Barcode,
}

/** 一次扫描结果。 */
data class ScanResult(
    val text: String,
    val formatName: String,
    val scannedAt: Long,
)

/** 保存在「扫描保存」页的一条记录。 */
data class SavedScan(
    val id: Long,
    val text: String,
    val formatName: String,
    val savedAt: Long,
)

/** 一次性提示，由界面映射成文案，VM 不直接持有 Context 文案。 */
enum class ScanNotice {
    Copied,
    CopyPartFailed,
    Saved,
    SaveFailed,
    NoUrl,
    OpenFailed,
    AlbumMissed,
    CameraUnavailable,
}

/**
 * 「扫描保存」页的本地存储：SharedPreferences + 单段 JSON。
 *
 * 只保存扫描文本与格式，不落图片；最多保留 [MAX_ITEMS] 条，按保存时间倒序。
 */
class ScanHistoryStore(context: android.content.Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    fun list(): List<SavedScan> = runCatching {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val text = item.optString("text")
                if (text.isBlank()) continue
                add(
                    SavedScan(
                        id = item.optLong("id"),
                        text = text,
                        formatName = item.optString("format"),
                        savedAt = item.optLong("savedAt"),
                    ),
                )
            }
        }.sortedByDescending { it.savedAt }
    }.getOrElse { emptyList() }

    fun add(text: String, formatName: String): List<SavedScan> {
        val entry = SavedScan(
            id = System.currentTimeMillis(),
            text = text,
            formatName = formatName,
            savedAt = System.currentTimeMillis(),
        )
        val next = (listOf(entry) + list().filterNot { it.text == text })
            .take(MAX_ITEMS)
        persist(next)
        return next
    }

    fun remove(id: Long): List<SavedScan> {
        val next = list().filterNot { it.id == id }
        persist(next)
        return next
    }

    private fun persist(items: List<SavedScan>) {
        runCatching {
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("text", item.text)
                        put("format", item.formatName)
                        put("savedAt", item.savedAt)
                    },
                )
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "barcode_scan_history"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 100
    }
}
