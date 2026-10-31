package com.pencil.chuangyin.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pencil.chuangyin.printer.ShotTransferBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 极简拍照：只负责「按下快门 → 得到一张方向正确的照片 → 交给图片打印页」。
 *
 * 取景、构图、锐化、二值化等一律不做——这些属于图片打印页与本页之后的处理链路；
 * 需要文档校正请走「文稿扫描」（FairScan）。
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val shotBus: ShotTransferBus,
) : ViewModel() {

    var flashAuto by mutableStateOf(true)
        private set

    /** 快门进行中：禁用重复点击，避免同一时刻连拍多张。 */
    var isCapturing by mutableStateOf(false)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    /** 照片已写入传递总线，UI 收到后返回图片打印页。 */
    private val _completed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completed = _completed.asSharedFlow()

    fun toggleFlash(imageCapture: ImageCapture?) {
        flashAuto = !flashAuto
        runCatching {
            imageCapture?.flashMode = if (flashAuto) {
                ImageCapture.FLASH_MODE_AUTO
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
        }
    }

    fun capture(imageCapture: ImageCapture) {
        if (isCapturing) return
        isCapturing = true
        val dir = File(appContext.cacheDir, ShotTransferBus.SHOT_DIR).apply { runCatching { mkdirs() } }
        val file = File(dir, "photo_${System.nanoTime()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch { deliver(file) }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    _messages.tryEmit(appContext.getString(R.string.camera_capture_failed))
                }
            },
        )
    }

    /**
     * 归一化 EXIF 方向后交给传递总线。
     *
     * 图片打印页的 `FileImageSource` 直接按像素解码、不读 EXIF；若这里不转正，
     * 竖拍照片在打印页会躺倒。旋转后重新编码为 JPEG，避免依赖下游解析方向标签。
     */
    private suspend fun deliver(raw: File) {
        try {
            val normalized = withContext(Dispatchers.IO) { normalizeOrientation(raw) }
            shotBus.offer(listOf(normalized))
            _completed.tryEmit(Unit)
        } catch (_: Throwable) {
            _messages.tryEmit(appContext.getString(R.string.camera_capture_failed))
        } finally {
            runCatching { if (raw.exists()) raw.delete() }
            isCapturing = false
        }
    }

    private fun normalizeOrientation(raw: File): File {
        val orientation = runCatching {
            androidx.exifinterface.media.ExifInterface(raw.absolutePath)
                .getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )
        }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            // 无旋转：直接复制成成品文件，交给下游统一按 shot_ 前缀管理。
            val target = File(raw.parentFile, "shot_${System.nanoTime()}.jpg")
            raw.copyTo(target, overwrite = true)
            return target
        }
        val decoded = BitmapFactory.decodeFile(raw.absolutePath) ?: error("decode failed")
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true,
        )
        val target = File(raw.parentFile, "shot_${System.nanoTime()}.jpg")
        FileOutputStream(target).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        if (rotated !== decoded) runCatching { decoded.recycle() }
        runCatching { rotated.recycle() }
        return target
    }
}
