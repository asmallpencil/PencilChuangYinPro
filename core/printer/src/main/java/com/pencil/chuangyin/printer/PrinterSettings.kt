package com.pencil.chuangyin.printer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 打印机硬件相关的用户设置（当前为热敏纸宽度）。
 *
 * 纸宽以毫米为单位持久化，打印管线按 58mm=384 点（主流 58mm 热敏打印机标准）换算为打印头像素宽。
 */
interface PrinterSettings {

    /** 当前热敏纸宽度（毫米）状态流，供 UI 订阅。 */
    val paperWidthMm: StateFlow<Int>

    /** 设置热敏纸宽度（毫米），范围 [PAPER_WIDTH_MM_MIN]..[PAPER_WIDTH_MM_MAX]。 */
    suspend fun setPaperWidthMm(mm: Int)

    /**
     * 同步读取当前纸宽（毫米）。打印管线在打印时调用，故需同步可用；
     * DataStore 首次读取前返回默认值，不会阻塞打印流程。
     */
    fun currentPaperWidthMm(): Int

    /** 按当前纸宽换算的打印头像素宽（58mm=384 点）。 */
    fun printWidthPx(): Int
}

/** 热敏纸宽度下限（毫米）。 */
const val PAPER_WIDTH_MM_MIN = 1

/** 热敏纸宽度上限（毫米，主流 58mm 热敏纸）。 */
const val PAPER_WIDTH_MM_MAX = 58

/** 热敏纸宽度默认值。 */
const val PAPER_WIDTH_MM_DEFAULT = PAPER_WIDTH_MM_MAX

/** 58mm 纸对应的打印头像素宽（主流 58mm 热敏打印机标准）。 */
private const val PRINT_WIDTH_PX_AT_58MM = PrinterCommand.PRINT_WIDTH_PX

@Singleton
class DataStorePrinterSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) : PrinterSettings {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val _paperWidthMm = MutableStateFlow(PAPER_WIDTH_MM_DEFAULT)
    override val paperWidthMm: StateFlow<Int> = _paperWidthMm.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 不在构造期做 runBlocking 首读：该单例在首页就绪路径上会被注入，
        // 同步读 DataStore 等于在主线程上做一次磁盘 I/O（冷启动几十毫秒）。
        // 下面的订阅会在首个值到达时（毫秒级）回填，语义不变且不阻塞首帧。
        scope.launch {
            context.dataStore.data.map { it[KEY_PAPER_WIDTH_MM] ?: PAPER_WIDTH_MM_DEFAULT }
                .collect { value ->
                    _paperWidthMm.value = value.coerceIn(PAPER_WIDTH_MM_MIN, PAPER_WIDTH_MM_MAX)
                }
        }
    }

    override suspend fun setPaperWidthMm(mm: Int) {
        val safe = mm.coerceIn(PAPER_WIDTH_MM_MIN, PAPER_WIDTH_MM_MAX)
        _paperWidthMm.value = safe
        context.dataStore.edit { it[KEY_PAPER_WIDTH_MM] = safe }
    }

    override fun currentPaperWidthMm(): Int = _paperWidthMm.value

    override fun printWidthPx(): Int {
        val mm = currentPaperWidthMm().toFloat()
        return ((mm / PAPER_WIDTH_MM_MAX.toFloat()) * PRINT_WIDTH_PX_AT_58MM)
            .toInt()
            .coerceIn(8, PRINT_WIDTH_PX_AT_58MM)
    }

    private companion object {
        const val DATASTORE_NAME = "pencil_print_printer_settings"
        val KEY_PAPER_WIDTH_MM = intPreferencesKey("paperWidthMm")
    }
}
