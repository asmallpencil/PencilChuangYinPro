package com.pencil.chuangyin.core.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * 液态玻璃实现方式的渲染能力检测。
 *
 * 两种实现都依赖平台图形能力，且**各自的可用条件不同**，因此设置页需要按实现方式给出准确提示，
 * 而不是笼统地说「需要 GLES3」。检测结果仅供参考提示，实际渲染路径仍会在运行时自行降级。
 */
object LiquidGlassEngine {

    /**
     * Kotlin（Compose）实现是否可用：需要 Android 12(API31) 的 RenderEffect 与
     * Android 13(API33) 的 AGSL RuntimeShader（折射/色散着色器）。
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun kyantAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** C++ 实现是否可用（跨平台，仅受 ABI/原生库装载影响；由 app 层探测后回填）。 */
    fun cppAvailable(nativeReported: Boolean): Boolean = nativeReported
}

/**
 * 指定实现方式当前是否可用（结合 [available] 门控与运行环境）。
 *
 * @param cppAvailable C++ 原生库在当前设备是否可用（由宿主经 JNI 探测）。
 */
fun LiquidGlassRenderer.isAvailable(cppAvailable: Boolean): Boolean = when (this) {
    LiquidGlassRenderer.Cpp -> LiquidGlassEngine.cppAvailable(cppAvailable)
    LiquidGlassRenderer.Kyant -> LiquidGlassEngine.kyantAvailable()
}
