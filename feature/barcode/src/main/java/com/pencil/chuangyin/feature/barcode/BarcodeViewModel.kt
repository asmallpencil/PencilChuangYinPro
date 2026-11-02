package com.pencil.chuangyin.feature.barcode

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.barcode.BarcodeDecoder
import com.pencil.chuangyin.core.barcode.BarcodeFormatOption
import com.pencil.chuangyin.core.barcode.BarcodeGenerator
import com.pencil.chuangyin.core.barcode.DecodedBarcode
import com.pencil.chuangyin.core.ui.data.GallerySaver
import com.pencil.chuangyin.core.ui.editor.PageLayerTransform
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
    val isSaving: Boolean = false,
    val connectedDevice: PrinterDevice? = null,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val progress: PrintProgress? = null,
    val lastResult: PrintResult? = null,
    val printerStatus: PrinterStatus? = null,
    /** 打印机是否正处于过热保护（固件自主冷却中）。 */
    val overheatActive: Boolean = false,
    /** 相册图片解码结果；非 null 时界面弹出结果对话框。 */
    val scanResult: DecodedBarcode? = null,
    val isScanning: Boolean = false,
    val notice: BarcodeNotice? = null,
)

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val printerController: PrinterController,
    private val printerSettings: PrinterSettings,
    private val gallerySaver: GallerySaver,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<BarcodeUiState> = _state.asStateFlow()

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
    }

    // ---- 内容 / 格式 ----

    fun setContent(value: String) {
        _state.update { it.copy(content = value) }
    }

    /** 切换格式；已生成过条码时立即按新格式重新生成。 */
    fun setFormat(option: BarcodeFormatOption) {
        val snapshot = _state.value
        if (snapshot.format == option) return
        _state.update { it.copy(format = option) }
        if (snapshot.generatedBitmap != null && snapshot.content.isNotEmpty()) generate()
    }

    /** 生成条码并切回默认图层变换（新条码不应沿用旧位置）。 */
    fun generate() {
        val snapshot = _state.value
        if (snapshot.isGenerating) return
        val content = snapshot.content
        if (content.isEmpty()) {
            _state.update { it.copy(notice = BarcodeNotice.ContentEmpty) }
            return
        }
        val option = snapshot.format
        val width = printerSettings.printWidthPx()
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(isGenerating = true) }
            val raw = BarcodeGenerator.encode(content, option, widthPx = width)
            if (raw == null) {
                _state.update { it.copy(isGenerating = false, notice = BarcodeNotice.GenerateFailed) }
                return@launch
            }
            val page = BarcodePageComposer.compose(raw, PageLayerTransform(), width)
            _state.update {
                it.copy(
                    generatedBitmap = raw,
                    previewBitmap = page,
                    editTransform = PageLayerTransform(),
                    editorOpen = false,
                    isGenerating = false,
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

    // ---- 扫描解码 ----

    /** 从相库图片解码条码；识别失败给一次性提示，不弹结果框。 */
    fun scanFromUri(uri: Uri) {
        if (_state.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isScanning = true) }
            val decoded = BarcodeDecoder.decodeFromUri(appContext, uri)
            _state.update {
                it.copy(
                    isScanning = false,
                    scanResult = decoded,
                    notice = if (decoded == null) BarcodeNotice.ScanFailed else it.notice,
                )
            }
        }
    }

    /** 把扫描结果填入输入框并关闭结果对话框。 */
    fun applyScanResultToContent() {
        val decoded = _state.value.scanResult ?: return
        _state.update { it.copy(content = decoded.text, scanResult = null) }
    }

    fun clearScanResult() = _state.update { it.copy(scanResult = null) }

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
