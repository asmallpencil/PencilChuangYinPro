package com.pencil.chuangyin.feature.barcode

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.barcode.BarcodeFormatOption
import com.pencil.chuangyin.core.barcode.BarcodeGenerator
import com.pencil.chuangyin.core.ui.data.GallerySaver
import com.pencil.chuangyin.core.ui.editor.PageLayerTransform
import com.pencil.chuangyin.printer.BarcodeContentTransferBus
import com.pencil.chuangyin.printer.BinarizeAlgorithm
import com.pencil.chuangyin.printer.ConnectionState
import com.pencil.chuangyin.printer.ImagePrintOptions
import com.pencil.chuangyin.printer.ImageProcessOptions
import com.pencil.chuangyin.printer.PrintPhase
import com.pencil.chuangyin.printer.PrintProgress
import com.pencil.chuangyin.printer.PrintResult
import com.pencil.chuangyin.printer.PrinterController
import com.pencil.chuangyin.printer.PrinterDevice
import com.pencil.chuangyin.printer.PrinterSettings
import com.pencil.chuangyin.printer.PrinterStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 条码页的一次性提示事件（文案由 UI 层映射为字符串资源）。 */
sealed interface BarcodeNotice {
    data object ContentEmpty : BarcodeNotice
    data object GenerateFailed : BarcodeNotice
    data object Saved : BarcodeNotice
    data object SaveFailed : BarcodeNotice
    data object ScanFailed : BarcodeNotice
}

data class BarcodeUiState(
    val content: String = "",
    val format: BarcodeFormatOption = BarcodeFormatOption.QR_CODE,
    /** 未经图层变换的原始条码位图（图层编辑与重新合成使用）。 */
    val generatedBitmap: Bitmap? = null,
    /** 按当前图层参数合成后的预览位图。 */
    val previewBitmap: Bitmap? = null,
    val editTransform: PageLayerTransform = PageLayerTransform(),
    val editorOpen: Boolean = false,
    val isGenerating: Boolean = false,
    /** 当前内容/格式无法编码出条码时置位，界面上做内联错误提示。 */
    val generateFailed: Boolean = false,
    val isSaving: Boolean = false,
    val connectedDevice: PrinterDevice? = null,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val progress: PrintProgress? = null,
    val lastResult: PrintResult? = null,
    val printerStatus: PrinterStatus? = null,
    /** 打印机是否正处于过热保护（固件自主冷却中）。 */
    val overheatActive: Boolean = false,
    val notice: BarcodeNotice? = null,
)

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val printerController: PrinterController,
    private val printerSettings: PrinterSettings,
    private val gallerySaver: GallerySaver,
    private val contentTransferBus: BarcodeContentTransferBus,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BarcodeUiState> = _state.asStateFlow()

    /** 输入防抖后的编码任务；新输入到来时取消上一次，避免逐字生成堆积。 */
    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            printerController.connectionState.collect { connection ->
                _state.update {
                    it.copy(
                        connectedDevice = (connection as? ConnectionState.Connected)?.device,
                        isConnecting = connection is ConnectionState.Connecting,
                    )
                }
            }
        }
        viewModelScope.launch {
            printerController.statusEvents.collect { status ->
                _state.update { it.copy(printerStatus = status) }
            }
        }
        viewModelScope.launch {
            printerController.deviceState.collect { device ->
                _state.update { it.copy(overheatActive = PrinterStatus.Overheat in device.activeStatuses) }
            }
        }
        // 通用扫描器回填：扫描结果页点「填入条码内容」后，内容总线会推来文本。
        viewModelScope.launch {
            contentTransferBus.pending.collect { text ->
                if (text.isNullOrBlank()) return@collect
                contentTransferBus.consume()
                _state.update { it.copy(content = text) }
                scheduleRefresh(delayMs = 0L)
            }
        }
    }

    // ---- 内容 / 格式 ----

    /** 输入变化：防抖后自动重排预览，不需要再点「生成」。 */
    fun setContent(value: String) {
        _state.update { it.copy(content = value) }
        scheduleRefresh(delayMs = AUTO_REFRESH_DELAY_MS)
    }

    /** 切换格式后立即按新格式刷新。 */
    fun setFormat(option: BarcodeFormatOption) {
        if (_state.value.format == option) return
        _state.update { it.copy(format = option) }
        scheduleRefresh(delayMs = 0L)
    }

    /**
     * 自动刷新：取消上一次待执行任务，按当前内容/格式重新编码。
     * 保留用户已调好的图层变换（尺寸/位置/锁定），连续输入时预览不会跳回默认。
     */
    private fun scheduleRefresh(delayMs: Long) {
        generateJob?.cancel()
        val snapshot = _state.value
        if (snapshot.content.isBlank()) {
            _state.update {
                it.copy(
                    generatedBitmap = null,
                    previewBitmap = null,
                    isGenerating = false,
                    generateFailed = false,
                )
            }
            return
        }
        generateJob = viewModelScope.launch(Dispatchers.Default) {
            if (delayMs > 0) delay(delayMs)
            _state.update { it.copy(isGenerating = true) }
            val width = printerSettings.printWidthPx()
            val raw = BarcodeGenerator.encode(snapshot.content, snapshot.format, widthPx = width)
            if (raw == null) {
                _state.update {
                    it.copy(
                        generatedBitmap = null,
                        previewBitmap = null,
                        isGenerating = false,
                        generateFailed = true,
                    )
                }
                return@launch
            }
            val transform = _state.value.editTransform
            val page = BarcodePageComposer.compose(raw, transform, width)
            _state.update {
                it.copy(
                    generatedBitmap = raw,
                    previewBitmap = page,
                    editorOpen = false,
                    isGenerating = false,
                    generateFailed = false,
                )
            }
        }
    }

    // ---- 图层编辑 ----

    fun openEditor() {
        if (_state.value.generatedBitmap == null) return
        _state.update { it.copy(editorOpen = true) }
    }

    /** 退出编辑：按最新图层参数重新合成预览。 */
    fun closeEditor() {
        val snapshot = _state.value
        val raw = snapshot.generatedBitmap
        _state.update { it.copy(editorOpen = false) }
        if (raw == null) return
        viewModelScope.launch(Dispatchers.Default) {
            val page = BarcodePageComposer.compose(raw, snapshot.editTransform, printerSettings.printWidthPx())
            _state.update { it.copy(previewBitmap = page) }
        }
    }

    /** 编辑中的实时参数：只在退出编辑时合成，避免每帧重编码。 */
    fun updateEditTransform(transform: PageLayerTransform) {
        _state.update { it.copy(editTransform = transform) }
    }

    // ---- 保存 ----

    fun save() {
        val snapshot = _state.value
        if (snapshot.generatedBitmap == null || snapshot.isSaving || snapshot.isPrinting) return
        val content = snapshot.content
        val option = snapshot.format
        val transform = snapshot.editTransform
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            val width = printerSettings.printWidthPx()
            val raw = BarcodeGenerator.encode(content, option, widthPx = width)
            if (raw == null) {
                _state.update { it.copy(isSaving = false, notice = BarcodeNotice.SaveFailed) }
                return@launch
            }
            val page = BarcodePageComposer.compose(raw, transform, width)
            if (page !== raw) runCatching { raw.recycle() }
            val count = gallerySaver.save(listOf(page), "barcode_${System.currentTimeMillis()}")
            runCatching { page.recycle() }
            _state.update {
                it.copy(
                    isSaving = false,
                    notice = if (count > 0) BarcodeNotice.Saved else BarcodeNotice.SaveFailed,
                )
            }
        }
    }

    // ---- 打印 ----

    /** 打印一张条码（与单张打印一致的语义，保留以便首页复用）。 */
    fun print() = startPrintJob()

    fun printSingle() = startPrintJob()

    private fun startPrintJob() {
        val snapshot = _state.value
        if (snapshot.isPrinting || snapshot.generatedBitmap == null) return
        val content = snapshot.content
        val option = snapshot.format
        val transform = snapshot.editTransform
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isPrinting = true,
                    lastResult = null,
                    progress = PrintProgress(PrintPhase.Preparing),
                )
            }
            val width = printerSettings.printWidthPx()
            val result = printerController.printImagePages(
                pageCount = 1,
                options = ImagePrintOptions(
                    // 条码是纯黑白图像，固定阈值可避免抖动算法破坏模块边缘。
                    process = ImageProcessOptions(algorithm = BinarizeAlgorithm.FixedThreshold),
                ),
                onProgress = { p -> _state.update { it.copy(progress = p) } },
                pageLoader = {
                    val raw = BarcodeGenerator.encode(content, option, widthPx = width)
                        ?: Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(android.graphics.Color.WHITE)
                        }
                    val page = BarcodePageComposer.compose(raw, transform, width)
                    if (page !== raw) runCatching { raw.recycle() }
                    page
                },
            )
            _state.update { it.copy(isPrinting = false, progress = null, lastResult = result) }
        }
    }

    fun cancelPrint() = printerController.cancelPrint()

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun clearResult() = _state.update { it.copy(lastResult = null) }

    private fun initialState(): BarcodeUiState {
        val connected = (printerController.connectionState.value as? ConnectionState.Connected)?.device
        return BarcodeUiState(
            connectedDevice = connected,
            isConnecting = printerController.connectionState.value is ConnectionState.Connecting,
        )
    }
}

/** 输入停顿后自动刷新的防抖时长。 */
private const val AUTO_REFRESH_DELAY_MS = 320L
