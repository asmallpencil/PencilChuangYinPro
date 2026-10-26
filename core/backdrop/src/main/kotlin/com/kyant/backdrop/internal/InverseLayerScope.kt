package com.kyant.backdrop.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.unit.Density
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 记录「反向变换」所需的图形层属性。
 *
 * 说明：上游 `GraphicsLayerScope` 在新版 Compose 中以接口默认实现/实验 API 形式提供
 * 部分属性（`blendMode`/`colorFilter` 等），在 Kotlin 2.2.20 下无法被 override。
 * 本实现仅承载 [com.kyant.backdrop.backdrops.LayerBackdrop] 反向变换真正用到的字段，
 * 不再实现 `GraphicsLayerScope` 接口，避免与上游接口签名耦合。
 */
class InverseLayerScope {

    var size: Size = Size.Unspecified
    var density: Float = 1f
    var fontScale: Float = 1f

    var scaleX: Float = 1f
    var scaleY: Float = 1f
    var alpha: Float = 1f
    var translationX: Float = 0f
    var translationY: Float = 0f
    var shadowElevation: Float = 0f
    var rotationX: Float = 0f
    var rotationY: Float = 0f
    var rotationZ: Float = 0f
    var cameraDistance: Float = DefaultCameraDistance
    var transformOrigin: TransformOrigin = TransformOrigin.Center
    var shape = RectangleShape
    var clip: Boolean = false

    private var matrix: Matrix? = null

    /**
     * 供 `layerBlock` 配置本作用域属性后，计算其在绘制坐标系下的反向变换。
     */
    fun DrawTransform.inverseTransform(
        density: Density,
        layerBlock: (InverseLayerScope.() -> Unit)?
    ) {
        this@InverseLayerScope.size = size
        this@InverseLayerScope.density = density.density
        this@InverseLayerScope.fontScale = density.fontScale

        layerBlock?.invoke(this@InverseLayerScope)

        inverseTransformAtTopLeft(
            rotationZ = rotationZ,
            scaleX = scaleX,
            scaleY = scaleY
        )
    }

    fun reset() {
        size = Size.Unspecified
        density = 1f
        fontScale = 1f

        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationX = 0f
        translationY = 0f
        shadowElevation = 0f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        cameraDistance = DefaultCameraDistance
        transformOrigin = TransformOrigin.Center
        shape = RectangleShape
        clip = false

        matrix = null
    }

    private fun DrawTransform.inverseTransformAtTopLeft(
        rotationZ: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        if (rotationZ == 0f) {
            if (scaleX != 0f && scaleY != 0f) {
                scale(1f / scaleX, 1f / scaleY, Offset.Zero)
            }
            return
        }

        val matrix = matrix ?: Matrix().also { matrix = it }
        if (matrix.values.size < 16) return

        val rz = rotationZ * (PI / 180.0)
        val rsz = sin(rz).toFloat()
        val rcz = cos(rz).toFloat()

        val a00 = rcz * scaleX
        val a01 = rsz * scaleY
        val a10 = -rsz * scaleX
        val a11 = rcz * scaleY

        val det = a00 * a11 - a01 * a10
        if (det == 0f) return
        val invDet = 1f / det
        matrix[0, 0] = a11 * invDet
        matrix[0, 1] = -a01 * invDet
        matrix[1, 0] = -a10 * invDet
        matrix[1, 1] = a00 * invDet

        transform(matrix)
    }
}
