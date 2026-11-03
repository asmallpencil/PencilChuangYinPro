package com.pencil.chuangyin.feature.barcode.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.core.barcode.BarcodeDecoder
import com.pencil.chuangyin.printer.BarcodeContentTransferBus
import com.pencil.chuangyin.printer.TextTransferBus
import com.pencil.chuangyin.printer.TextTransferPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BarcodeScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textTransferBus: TextTransferBus,
    private val barcodeTransferBus: BarcodeContentTransferBus,
) : ViewModel() {

    private val history = ScanHistoryStore(context)

    var mode by mutableStateOf(ScanMode.Qr)
        private set

    var torchOn by mutableStateOf(false)
        private set

    var result by mutableStateOf<ScanResult?>(null)
        private set

    var saved by mutableStateOf(history.list())
        private set

    var savedSheetOpen by mutableStateOf(false)
        private set

    var selectCopyOpen by mutableStateOf(false)
        private set

    var notice by mutableStateOf<ScanNotice?>(null)
        private set

    var navigateToTextPrint by mutableStateOf(false)
        private set

    var cameraUnavailable by mutableStateOf(false)
        private set

    /** 当前结果是否已在保存页中（按内容去重）。 */
    val currentSaved: Boolean
        get() = result?.let { r -> saved.any { it.text == r.text } } == true

    fun selectMode(next: ScanMode) {
        if (mode == next) return
        mode = next
    }

    fun toggleTorch() {
        torchOn = !torchOn
    }

    fun onDecoded(text: String, formatName: String): Boolean {
        if (result != null || text.isBlank()) return false
        result = ScanResult(text = text, formatName = formatName, scannedAt = System.currentTimeMillis())
        return true
    }

    fun rescan() {
        result = null
        selectCopyOpen = false
    }

    fun markCameraUnavailable(unavailable: Boolean) {
        cameraUnavailable = unavailable
    }

    fun copyAll() {
        val text = result?.text ?: return
        copy(text)
    }

    fun copyPart(text: String) {
        if (text.isBlank()) {
            notice = ScanNotice.CopyPartFailed
            return
        }
        copy(text)
        selectCopyOpen = false
    }

    private fun copy(text: String) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        manager.setPrimaryClip(ClipData.newPlainText("scanned", text))
        notice = ScanNotice.Copied
    }

    fun saveCurrent() {
        val current = result ?: return
        runCatching { history.add(current.text, current.formatName) }
            .onSuccess {
                saved = it
                notice = ScanNotice.Saved
            }
            .onFailure { notice = ScanNotice.SaveFailed }
    }

    fun openSavedSheet() {
        saved = history.list()
        savedSheetOpen = true
    }

    fun closeSavedSheet() {
        savedSheetOpen = false
    }

    fun deleteSaved(id: Long) {
        saved = runCatching { history.remove(id) }.getOrDefault(saved)
    }

    fun toggleSelectCopy() {
        selectCopyOpen = !selectCopyOpen
    }

    fun closeSelectCopy() {
        selectCopyOpen = false
    }

    fun requestTextPrint() {
        val current = result ?: return
        textTransferBus.offer(
            TextTransferPayload(title = "扫描内容", content = current.text),
        )
        navigateToTextPrint = true
    }

    fun reportNoUrl() {
        notice = ScanNotice.NoUrl
    }

    fun consumeTextPrintNavigation() {
        navigateToTextPrint = false
    }

    fun fillBarcode() {
        val text = result?.text ?: return
        barcodeTransferBus.offer(text)
    }

    fun decodeAlbum(uri: Uri) {
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                BarcodeDecoder.decodeFromUri(context, uri)
            }
            if (decoded == null) {
                notice = ScanNotice.AlbumMissed
            } else {
                onDecoded(decoded.text, decoded.formatName)
            }
        }
    }

    fun openUrl(url: String, onFail: () -> Unit = {}) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                notice = ScanNotice.OpenFailed
                onFail()
            }
    }

    fun consumeNotice() {
        notice = null
    }
}
