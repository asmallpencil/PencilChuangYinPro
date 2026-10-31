package com.pencil.chuangyin.feature.scanner

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.pencil.chuangyin.printer.ShotTransferBus
import com.pencil.chuangyin.printer.ShotTransferMode
import dagger.hilt.android.lifecycle.HiltViewModel
import org.fairscan.app.FairScanHost
import java.io.File
import javax.inject.Inject

/** 把扫描成品交给图片打印页：单页/多页都按顺序直接加入打印队列。 */
@HiltViewModel
class ScannerTransferViewModel @Inject constructor(
    private val shotBus: ShotTransferBus,
) : ViewModel() {

    fun offer(files: List<File>) {
        shotBus.offer(files, ShotTransferMode.APPEND_ALL)
    }
}

/**
 * 文稿扫描入口：内嵌 FairScan（GPL-3.0）原文界面与识别流程。
 *
 * 打印完成 → 成品图交给 [ShotTransferBus] → 回到图片打印页。
 */
@Composable
fun ScannerRoute(
    onClose: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val transferViewModel: ScannerTransferViewModel = hiltViewModel()
    FairScanHost(
        onPrintFiles = { files ->
            transferViewModel.offer(files)
            onConfirmed()
        },
        onExit = onClose,
    )
}
