package com.pencil.chuangyin.desktop.image

import androidx.compose.ui.graphics.ImageBitmap
import com.pencil.chuangyin.desktop.text.PrintProgress
import com.pencil.chuangyin.desktop.text.PrintResult
import com.pencil.chuangyin.desktop.text.PrinterStatus

/**
 * 图片打印数据模型 —— 与安卓端 `feature/image` + `core/printer` 的同名类型逐字段对齐，
 * 使桌面端页面（来源入口、预览翻页、逐页参数覆盖、批量份数、底栏状态机）与安卓端
 * 共用同一套状态语义，而不是另起一套简化实现。
 */

/** 黑白化算法（与安卓端 BinarizeAlgorithm 一致）。 */
enum class BinarizeAlgorithm {
    FixedThreshold,
    FloydSteinberg,
    Atkinson,
    SobelEdge,
    SauvolaAdaptive,
}

/** 图片处理参数（与安卓端 ImageProcessOptions 一致）。 */
data class ImageProcessOptions(
    val algorithm: BinarizeAlgorithm = BinarizeAlgorithm.FixedThreshold,
    val threshold: Int = DEFAULT_THRESHOLD,
    val rotationDegrees: Int = 0,
) {
    init {
        require(threshold in MIN_THRESHOLD..MAX_THRESHOLD) { "threshold 越界：$threshold" }
        require(rotationDegrees % 90 == 0 && rotationDegrees in 0..270) {
            "rotationDegrees 必须是 0/90/180/270：$rotationDegrees"
        }
    }

    companion object {
        const val MIN_THRESHOLD = 0
        const val MAX_THRESHOLD = 255

        /** 与安卓端一致：默认阈值 149（略高于中点，线稿更锐利）。 */
        const val DEFAULT_THRESHOLD = 149

        val ROTATION_STEPS = listOf(0, 90, 180, 270)
    }
}

/** 打印任务里的一页（与安卓端 ImagePageInfo 一致）。 */
data class ImagePageInfo(val name: String)

/** 一次性提示（与安卓端 ImageNotice 一致）。 */
sealed interface ImageNotice {
    data class Added(val pages: Int) : ImageNotice
    data class Saved(val count: Int) : ImageNotice
    data object ArchiveEmpty : ImageNotice
    data object PdfFailed : ImageNotice
    data object ImportFailed : ImageNotice
    data object SaveFailed : ImageNotice
}

/** 选择页里的一项（与安卓端 PickItem 一致；桌面端无「外部成品转移」来源）。 */
sealed interface PickItem {
    val key: String

    data class PdfPage(val pageIndex: Int) : PickItem {
        override val key: String = "pdf_$pageIndex"
    }

    data class ArchiveImage(val path: String, val fileIndex: Int) : PickItem {
        override val key: String = "arc_$fileIndex"
    }
}

/** 选择页类型：决定标题与缩略图来源。 */
enum class PickKind { PDF, ARCHIVE }

/** 选择页状态（与安卓端 PickUiState 一致）。 */
data class PickUiState(
    val kind: PickKind,
    val items: List<PickItem>,
    val selected: Set<String>,
    val thumbnails: Map<String, ImageBitmap> = emptyMap(),
)

/** 图片打印页完整状态（与安卓端 ImageEditorUiState 一致）。 */
data class ImageEditorUiState(
    val pages: List<ImagePageInfo> = emptyList(),
    val currentIndex: Int = 0,
    val processOptions: ImageProcessOptions = ImageProcessOptions(),
    val currentPageCustomized: Boolean = false,
    val batchEnabled: Boolean = false,
    val copies: Int = DEFAULT_COPIES,
    val pick: PickUiState? = null,
    val previewBitmap: ImageBitmap? = null,
    val isPreparing: Boolean = false,
    val isSaving: Boolean = false,
    /** 已连接打印机名称（镜像 PrinterConnectionStore.connectionState；null 表示未连接）。 */
    val connectedName: String? = null,
    val isConnecting: Boolean = false,
    val isPrinting: Boolean = false,
    val progress: PrintProgress? = null,
    val lastResult: PrintResult? = null,
    val printerStatus: PrinterStatus? = null,
    val overheatActive: Boolean = false,
    val notice: ImageNotice? = null,
)

/** 默认打印份数（与安卓端 DEFAULT_COPIES 一致）。 */
const val DEFAULT_COPIES = 2
