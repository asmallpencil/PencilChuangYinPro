package com.pencil.chuangyin.pro.liquid

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PixelCopy
import com.pencil.chuangyin.core.theme.LiquidGlassStyle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 液态玻璃原生覆盖层：由 LiquidGlass-Cpp（liblgm.so）绘制**导航栏与全部注册控件**。
 *
 * 采用 GLSurfaceView(GLES3) + setZOrderOnTop(true) 承载库的 ImGui 绘制，浮于 Compose 内容之上；
 * Surface 透明（glClear 透明黑），因此只有玻璃控件可见。液态玻璃为 C++ 原生实现，不是
 * Kotlin 仿制——Compose 侧只负责「上报控件位置 / 下发状态 / 分发原生事件」。
 *
 * 控件桥接模型：Compose 侧把界面控件（按钮/开关/滑条/分段行）通过 onGloballyPositioned 上报
 * 屏幕像素坐标，原生层维护动态控件表，每帧绘制对应的玻璃控件并自行消费触摸；状态变化经
 * [nativePollWidgetEvent] 回传主线程再分发给 Compose 回调。
 *
 * 触摸：原生层对导航栏与所有已注册控件区域做命中测试，命中才消费并转发原生，否则透传给下层 Compose。
 * backdrop：用 PixelCopy 抓取窗口内容（不含本 SurfaceView 自身图层）作为折射/透镜背景，
 * 按 [BACKDROP_INTERVAL_MS] 节流；API < 24 或失败时保持无 backdrop（纯表面叠加）。
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    /** 导航栏点击回调（0=首页 1=创作 2=P站）。 */
    var onNavTap: ((Int) -> Unit)? = null

    /** 原生控件事件回调：id / kind(0=Button 1=Toggle 2=Slider 3=Segmented) / value。 */
    var onWidgetEvent: ((id: Int, kind: Int, value: Int) -> Unit)? = null

    /**
     * 原生液态玻璃弹窗结果回调：1=取消（含点击遮罩 / 返回键），2=确定。
     *
     * 仅在关闭动画播完那一帧触发一次；随后 [isDialogVisible] 变回 false。
     */
    var onDialogResult: ((Int) -> Unit)? = null

    /**
     * 主题 / 明暗 / 背景变化后强制刷新 backdrop（忽略 [BACKDROP_INTERVAL_MS] 节流）。
     *
     * 只采一次是不够的：本方法由 Compose 的 `LaunchedEffect` 触发，此时**新主题所在的窗口帧
     * 还没有绘制上屏**，立即 `PixelCopy` 抓到的仍是上一套主题的内容；而 backdrop 之后又受
     * 节流 + `RENDERMODE_WHEN_DIRTY`（无动画时不再出帧）限制，不会自行补采，于是玻璃面永久
     * 停留在旧主题底色上——这就是「液态玻璃控件切换暗色主题无效」的成因。
     *
     * 因此在若干个延迟点重复强制采集，覆盖 Compose 提交新主题帧的时间窗口，确保最终绑定的是
     * 已应用新主题的那一帧。
     */
    fun refreshBackdropNow() {
        forceCapture()
        REFRESH_RETRY_DELAYS_MS.forEach { delay ->
            mainHandler.postDelayed({ forceCapture() }, delay)
        }
    }

    /** 强制采集一次 backdrop 并请求一帧绘制。 */
    private fun forceCapture() {
        forceBackdrop = true
        scheduleBackdropCapture()
        requestRender()
    }

    /** 控件状态缓存（主线程读写）：Slider 值变化时需要 min/max 还原真实值。 */
    private val sliderRanges = HashMap<Int, FloatArray>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val density: Float = resources.displayMetrics.density

    private var glReady = false
    private var tracking = false
    private var lastBackdropAt = 0L
    private var capturing = false
    private var backdropBitmap: Bitmap? = null
    /** 强制立即刷新 backdrop（忽略节流）：主题/背景色变化时由 Compose 侧触发。 */
    private var forceBackdrop = false

    /** 原生液态玻璃弹窗是否活跃：为 true 时本视图接管全部触摸并请求连续出帧。 */
    @Volatile private var dialogActive = false

    /** ACTION_DOWN 命中弹窗后持续跟踪到抬起（弹窗外按下时由原生层自行关闭）。 */
    private var dialogTracking = false

    /** show 之后等待原生确认可见的帧数（兜底：原生弹窗因故未创建时复位状态）。 */
    private var dialogPendingFrames = 0

    /**
     * 模态弹窗（窗口内覆盖层）是否打开。
     *
     * 覆盖层恒在窗口之上，若不拦截，弹窗打开时点到底部导航栏等原生控件仍会命中并消费触摸
     * （Compose 侧的全屏遮罩拦不到原生层）。置 true 后本视图**不再消费任何触摸**，事件全部
     * 交还下层 Compose（遮罩点击关闭 / 卡片按钮），视觉不受影响。
     */
    private var modalTouchBlocked = false

    /** 设置模态弹窗触摸拦截（由 Compose 侧按弹窗可见性切换）。 */
    fun setModalTouchBlocked(blocked: Boolean) {
        modalTouchBlocked = blocked
        if (blocked) tracking = false
    }

    /**
     * 模态弹窗是否要求原生层「即使控件玻璃关闭也继续绘制容器玻璃面（Panel）」。
     *
     * 弹窗由 Compose 在**窗口内**绘制（core:ui 的 `AppDialogHost`），再注册为 MODAL 层 Panel，
     * 借原生玻璃面「镜面」透出——这才是真 C++ 液态玻璃弹窗。但弹窗打开时用户往往尚未开启控件
     * 玻璃（如「开启液态玻璃」的三级警告弹窗），若原生层因此停绘，弹窗便退化为 Compose 实心底，
     * 即「伪液态玻璃弹窗」。置 true 后仅放行 Panel，不牵连按钮/开关等控件。
     *
     * 由 Compose 侧按弹窗可见性切换；所有 App 弹窗都走同一宿主，故无需弹窗逐个声明。
     */
    fun setGlassPanelsAlwaysOn(on: Boolean) {
        queueEvent { nativeSetGlassPanelsAlwaysOn(on) }
        requestRender()
    }

    // ---- 原生液态玻璃模态弹窗（lgx::Modal 自绘，非 Compose 仿制） ----

    /**
     * 显示一块**真 C++ 液态玻璃弹窗**（由 liblgm.so 的 `lgx::Modal` 逐帧自绘玻璃面 + 遮罩）。
     *
     * @param showCancel  是否显示「取消」按钮；false 时确认键占满整行（单按钮弹窗）。
     * @param origin      弹出动画起点（一般传触发按钮的屏幕矩形）。非空且有效时走
     *                    `openFrom`（从按钮位置长出），否则走 `open`（从屏幕中心长出）——
     *                    即开源库内置的两种弹出动画。
     */
    fun showDialog(
        title: String,
        message: String,
        cancelLabel: String,
        confirmLabel: String,
        showCancel: Boolean,
        origin: Rect? = null,
    ) {
        val o = origin?.takeIf { it.right - it.left > 1f && it.bottom - it.top > 1f }
        dialogActive = true
        dialogTracking = false
        dialogPendingFrames = 0
        queueEvent {
            if (o != null) {
                nativeDialogShow(
                    title, message, cancelLabel, confirmLabel, showCancel,
                    true, o.left, o.top, o.right, o.bottom,
                )
            } else {
                nativeDialogShow(
                    title, message, cancelLabel, confirmLabel, showCancel,
                    false, 0f, 0f, 0f, 0f,
                )
            }
        }
        requestRender()
    }

    /** 关闭原生弹窗（`code`：1=取消 2=确定），播放关闭动画后经 [onDialogResult] 回传。 */
    fun closeDialog(code: Int = 1) {
        if (!dialogActive) return
        queueEvent { nativeDialogClose(code) }
        requestRender()
    }

    /** 原生弹窗是否活跃（含弹出/关闭动画期间）。 */
    fun isDialogVisible(): Boolean = dialogActive

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        preserveEGLContextOnPause = true
        setRenderer(GlassRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /**
     * 下发开关：控件层与导航栏分别控制。
     *
     * 注意：**关闭时也必须请求重绘**。本 View 是 zOrderOnTop 的半透明 GLSurfaceView，
     * RENDERMODE_WHEN_DIRTY 模式下若不主动出帧，Surface 会永久停留在最后一帧——
     * 于是关闭玻璃后，最后一帧的玻璃导航栏/面板残像仍压在 Compose 之上，而重新显示的
     * Compose 导航栏只能从残像下方透出来，表现为「双层导航栏 / 背景缺一块」。
     * 这一帧 renderFrame 不画任何控件，只做 glClear，画面归透明。
     */
    fun setGlassEnabled(controls: Boolean, navbar: Boolean) {
        queueEvent {
            nativeSetEnabled(controls, navbar)
            if (!controls) nativeClearWidgets()
        }
        requestRender()
    }

    /** 下发明暗模式（决定玻璃色调与折射明暗）。 */
    fun setLightMode(light: Boolean) {
        queueEvent { nativeSetColorMode(light) }
        requestRender()
    }

    /**
     * 下发 App 真实主题色，供原生控件着色，避免硬编码绿/蓝。
     * @param accentArgb  强调色（colorScheme.primary），用于 Toggle/Slider/TabStrip 的激活色。
     * @param surfaceArgb 玻璃基调色（colorScheme.surface），同时决定库的亮/暗判定。
     * @param iconArgb    图标前景色（colorScheme.onSurface），亮色模式为深色、暗色模式为浅色；
     *                    传 0 时由库按亮暗自适应回退。
     */
    fun setThemeColors(accentArgb: Int, surfaceArgb: Int, iconArgb: Int = 0) {
        queueEvent { nativeSetThemeColors(accentArgb, surfaceArgb, iconArgb) }
        requestRender()
    }

    /** 同步当前导航页索引（Compose pager 变化时调用）。 */
    fun setNavIndex(index: Int) {
        queueEvent { nativeSetNavIndex(index) }
        requestRender()
    }

    /** 下发全局液态玻璃样式（设置页调参实时生效）。 */
    fun setStyle(style: LiquidGlassStyle) {
        queueEvent {
            nativeSetStyle(
                style.blurDp,
                style.lensHeightDp,
                style.lensAmountDp,
                style.spectral,
                style.tintAlpha,
                style.shadowOn,
                style.glassOn,
                style.contourEdge,
            )
        }
        requestRender()
    }

    /**
     * 下发容器玻璃面（Panel）专属参数。
     *
     * 与 [setStyle] 分离的原因：按钮/开关等自绘控件必须共用同一份模糊取值，才能把
     * 屏幕邻域「擦干净、不漏字」；而容器玻璃面盖在实时 Compose 内容之上，模糊只会
     * 让压在下面的文字变糊。因此两者的模糊与采样外扩必须分别下发。
     *
     * @param blurDp  容器面板的模糊半径（dp）。为 0 时不模糊，底层 Compose 内容保持锐利。
     * @param padDp   采样外扩（dp）。越大越能取到面板四周的屏幕邻域；手机屏上容器几乎
     *                顶到边缘时，面板四周取不到内容，只能靠 [fillArgb] 兜底。
     * @param fillArgb 采样缺失处的兜底不透明度（0~1）。真实玻璃并非透明：在无内容可采
     *                的位置必须退化为不透明表面，否则会露出下层被模糊的鬼影。
     */
    fun setPanelStyle(blurDp: Float, padDp: Float, fillArgb: Float) {
        queueEvent { nativeSetPanelStyle(blurDp, padDp, fillArgb) }
        requestRender()
    }

    // ---- 控件注册 / 更新 / 注销（任意线程，内部排队到 GL 线程） ----

    fun registerButton(
        id: Int,
        box: Rect,
        label: String,
        iconId: Int,
        textSizeSp: Float,
        variant: Int = BUTTON_VARIANT_PLAIN,
        selected: Boolean = false,
        cornerRadius: Float = 0f,
    ) {
        queueEvent {
            nativeRegisterButton(
                id, box.left, box.top, box.right, box.bottom, label, iconId, textSizeSp,
                variant, selected, cornerRadius,
            )
        }
        requestRender()
    }

    /** 更新 Button 变体（0=普通 1=芯片 2=图标 3=文字）。 */
    fun setButtonVariant(id: Int, variant: Int) {
        queueEvent { nativeSetButtonVariant(id, variant) }
        requestRender()
    }

    /** 更新芯片选中态（选中时以强调色着色）。 */
    fun setButtonSelected(id: Int, selected: Boolean) {
        queueEvent { nativeSetButtonSelected(id, selected) }
        requestRender()
    }

    /**
     * 设置控件层级（值越大越靠上）。
     *
     * 原生覆盖层恒在 Compose 之上，其内部按层级排序绘制与命中；把覆盖层（抽屉/弹窗）
     * 内控件的层级抬高，即可自动压住导航栏与子导航等低层控件。
     */
    fun setWidgetLayer(id: Int, layer: Int) {
        queueEvent { nativeSetWidgetLayer(id, layer) }
        requestRender()
    }

    /**
     * 设置控件是否参与交互。
     *
     * false = draw-only：原生层只绘制玻璃外观，命中与事件全部交给 Compose。
     * 用于落在 LazyRow 等滚动容器内的芯片——若原生层吞掉触摸，横向滑动手势会失效。
     */
    fun setWidgetInteractive(id: Int, interactive: Boolean) {
        queueEvent { nativeSetWidgetInteractive(id, interactive) }
        requestRender()
    }

    /** 开关液态玻璃呼吸动画（默认开）。 */
    fun setAnimate(animate: Boolean) {
        queueEvent { nativeSetAnimate(animate) }
        requestRender()
    }

    /** 下发图标位图（白色字形 + alpha），上传为 GL 纹理供原生着色绘制。 */
    fun setButtonIconBitmap(id: Int, bitmap: Bitmap) {
        queueEvent { nativeSetButtonIconBitmap(id, bitmap) }
        requestRender()
    }

    /** 清除按钮图标纹理。 */
    fun clearButtonIcon(id: Int) {
        queueEvent { nativeClearButtonIcon(id) }
        requestRender()
    }

    /**
     * 下发导航栏图标位图（白色字形 + alpha），供原生层按主题前景色着色绘制。
     * @param index 导航项下标（0=首页 1=创作 2=P站）。
     */
    fun setNavIconBitmap(index: Int, bitmap: Bitmap) {
        queueEvent { nativeSetNavIconBitmap(index, bitmap) }
        requestRender()
    }

    fun registerToggle(id: Int, box: Rect, on: Boolean) {
        queueEvent { nativeRegisterToggle(id, box.left, box.top, box.right, box.bottom, on) }
        requestRender()
    }

    fun registerSlider(id: Int, box: Rect, value: Float, minValue: Float, maxValue: Float) {
        sliderRanges[id] = floatArrayOf(minValue, maxValue)
        queueEvent {
            nativeRegisterSlider(id, box.left, box.top, box.right, box.bottom, value, minValue, maxValue)
        }
        requestRender()
    }

    fun registerSegmented(id: Int, box: Rect, labels: Array<String>, iconIds: IntArray, activeIndex: Int) {
        queueEvent {
            nativeRegisterSegmented(id, box.left, box.top, box.right, box.bottom, labels, iconIds, activeIndex)
        }
        requestRender()
    }

    /**
     * 注册容器类玻璃面（Card / Drawer / TextField 等）。
     *
     * 只渲染一块原生液态玻璃底（毛玻璃 + 边缘 + 阴影），不参与命中测试、不产生事件；
     * 交互由覆盖其上的 Compose 内容自行处理。
     *
     * @param cornerRadius 圆角半径（物理像素）；<=0 时由原生层按胶囊处理。
     */
    fun registerPanel(id: Int, box: Rect, cornerRadius: Float) {
        queueEvent {
            nativeRegisterPanel(id, box.left, box.top, box.right, box.bottom, cornerRadius)
        }
        requestRender()
    }

    fun updateBox(id: Int, box: Rect, cornerRadius: Float) {
        queueEvent { nativeUpdateBox(id, box.left, box.top, box.right, box.bottom, cornerRadius) }
        requestRender()
    }

    fun updateButton(id: Int, label: String) {
        queueEvent { nativeUpdateButton(id, label) }
        requestRender()
    }

    fun updateToggle(id: Int, on: Boolean) {
        queueEvent { nativeUpdateToggle(id, on) }
        requestRender()
    }

    fun updateSlider(id: Int, value: Float) {
        queueEvent { nativeUpdateSlider(id, value) }
        requestRender()
    }

    fun updateSegmented(id: Int, activeIndex: Int) {
        queueEvent { nativeUpdateSegmented(id, activeIndex) }
        requestRender()
    }

    fun unregisterWidget(id: Int) {
        sliderRanges.remove(id)
        queueEvent { nativeUnregisterWidget(id) }
        requestRender()
    }

    fun clearWidgets() {
        sliderRanges.clear()
        queueEvent { nativeClearWidgets() }
        requestRender()
    }

    /** 查询某 Slider 的 [min, max]。 */
    fun sliderRange(id: Int): FloatArray? = sliderRanges[id]

    /** 释放原生资源（Activity 销毁时调用）。 */
    fun release() {
        sliderRanges.clear()
        dialogActive = false
        dialogTracking = false
        dialogPendingFrames = 0
        queueEvent {
            nativeRelease()
            glReady = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 原生液态玻璃弹窗活跃：整条手势交给原生层（弹窗自绘并自行做命中判定，
        // 点击弹窗外由原生层 close(1)）。优先级高于 modalTouchBlocked。
        if (dialogActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dialogTracking = true
                    nativeTouch(0, event.x, event.y)
                    requestRender()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dialogTracking) nativeTouch(2, event.x, event.y)
                    requestRender()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dialogTracking) nativeTouch(1, event.x, event.y)
                    requestRender()
                    dialogTracking = false
                    return true
                }
            }
            return true
        }
        // 模态弹窗打开：不消费任何触摸，整条手势交还下层 Compose（见 [setModalTouchBlocked]）。
        if (modalTouchBlocked) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = nativeHitTest(event.x, event.y)
                if (tracking) {
                    nativeTouch(0, event.x, event.y)
                    requestRender()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                nativeTouch(2, event.x, event.y)
                requestRender()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                nativeTouch(1, event.x, event.y)
                requestRender()
                tracking = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return false
                nativeTouch(1, event.x, event.y)
                requestRender()
                tracking = false
                return true
            }
        }
        return false
    }

    private fun scheduleBackdropCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (capturing) return
        val now = System.currentTimeMillis()
        // 强制刷新（主题/背景变化）或距上次采集超过节流阈值时才触发。
        if (!forceBackdrop && now - lastBackdropAt < BACKDROP_INTERVAL_MS) return
        lastBackdropAt = now
        forceBackdrop = false
        mainHandler.post { captureBackdrop() }
    }

    private fun captureBackdrop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val activity = context as? Activity ?: return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        // 全分辨率采集：面板/控件直接采样该纹理，半分辨率会经 GL_LINEAR 上采样，
        // 使压在玻璃下的文字与图标整体发糊（用户反馈的「整个区域变模糊」）。
        val bw = w
        val bh = h
        var bmp = backdropBitmap
        if (bmp == null || bmp.width != bw || bmp.height != bh) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            backdropBitmap = bmp
        }
        val target = bmp ?: return
        capturing = true
        try {
            PixelCopy.request(activity.window, target, { result ->
                capturing = false
                if (result == PixelCopy.SUCCESS) {
                    queueEvent { nativeBindBackdrop(target) }
                    requestRender()
                }
            }, mainHandler)
        } catch (t: Throwable) {
            capturing = false
        }
    }

    private inner class GlassRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            // 延迟到 onSurfaceChanged 取得尺寸后再初始化原生层
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (!glReady) {
                glReady = nativeInit(width.toFloat(), height.toFloat(), density)
            } else {
                nativeResize(width.toFloat(), height.toFloat())
            }
            scheduleBackdropCapture()
        }

        override fun onDrawFrame(gl: GL10?) {
            if (!glReady) return
            nativeRender()
            drainDialogResult()
            val nav = nativePollNavTap()
            if (nav >= 0) mainHandler.post { onNavTap?.invoke(nav) }
            drainWidgetEvents()
            scheduleBackdropCapture()
            if (nativeNeedsFrame()) requestRender()
        }
    }

    /**
     * 读取原生弹窗的关闭结果并复位状态。
     *
     * 原生层在关闭动画播完那一帧返回结果码（1/2），此处取出后回主线程分发 [onDialogResult]；
     * 若原生弹窗因故未创建可见（连续若干帧既无结果也不可见），同样复位，避免触摸被永久吞掉。
     */
    private fun drainDialogResult() {
        val code = nativePollDialogResult()
        if (code >= 0) {
            dialogPendingFrames = 0
            mainHandler.post {
                dialogActive = false
                dialogTracking = false
                onDialogResult?.invoke(code)
            }
            return
        }
        if (!dialogActive) return
        if (nativeDialogVisible()) {
            dialogPendingFrames = 0
        } else if (++dialogPendingFrames > 2) {
            dialogPendingFrames = 0
            mainHandler.post {
                dialogActive = false
                dialogTracking = false
            }
        }
    }

    /** 排空原生控件事件队列并分发到主线程回调。 */
    private fun drainWidgetEvents() {
        if (onWidgetEvent == null) return
        while (true) {
            val raw = nativePollWidgetEvent()
            if (raw < 0L) break
            val id = (raw ushr 32).toInt()
            val kind = ((raw ushr 24) and 0xFFL).toInt()
            val value = (raw and 0xFFFFFFL).toInt()
            mainHandler.post { onWidgetEvent?.invoke(id, kind, value) }
        }
    }

    /** 屏幕像素矩形（left/top/right/bottom）。 */
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private external fun nativeInit(width: Float, height: Float, density: Float): Boolean
    private external fun nativeResize(width: Float, height: Float)
    private external fun nativeRender()
    private external fun nativeNeedsFrame(): Boolean
    private external fun nativeSetEnabled(controls: Boolean, navbar: Boolean)
    private external fun nativeSetGlassPanelsAlwaysOn(on: Boolean)
    private external fun nativeSetColorMode(light: Boolean)
    private external fun nativeSetThemeColors(accentArgb: Int, surfaceArgb: Int, iconArgb: Int)
    private external fun nativeSetNavIndex(index: Int)
    private external fun nativePollNavTap(): Int
    private external fun nativePollWidgetEvent(): Long
    private external fun nativeHitTest(x: Float, y: Float): Boolean
    private external fun nativeTouch(action: Int, x: Float, y: Float)
    private external fun nativeBindBackdrop(bitmap: Bitmap): Boolean
    private external fun nativeRelease()

    private external fun nativeRegisterButton(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        label: String,
        iconId: Int,
        textSizeSp: Float,
        variant: Int,
        selected: Boolean,
        cornerRadius: Float,
    )

    private external fun nativeRegisterToggle(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        on: Boolean,
    )

    private external fun nativeRegisterSlider(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        value: Float,
        minValue: Float,
        maxValue: Float,
    )

    private external fun nativeRegisterSegmented(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        labels: Array<String>,
        iconIds: IntArray,
        activeIndex: Int,
    )

    private external fun nativeRegisterPanel(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cornerRadius: Float,
    )

    private external fun nativeUpdateBox(
        id: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cornerRadius: Float,
    )
    private external fun nativeUpdateButton(id: Int, label: String)
    private external fun nativeSetButtonVariant(id: Int, variant: Int)
    private external fun nativeSetButtonSelected(id: Int, selected: Boolean)
    private external fun nativeSetWidgetLayer(id: Int, layer: Int)
    private external fun nativeSetWidgetInteractive(id: Int, interactive: Boolean)
    private external fun nativeSetAnimate(animate: Boolean)
    private external fun nativeSetButtonIconBitmap(id: Int, bitmap: Bitmap): Boolean
    private external fun nativeClearButtonIcon(id: Int)
    private external fun nativeSetNavIconBitmap(index: Int, bitmap: Bitmap)
    private external fun nativeUpdateToggle(id: Int, on: Boolean)
    private external fun nativeUpdateSlider(id: Int, value: Float)
    private external fun nativeUpdateSegmented(id: Int, activeIndex: Int)
    private external fun nativeUnregisterWidget(id: Int)
    private external fun nativeClearWidgets()

    private external fun nativeSetStyle(
        blurDp: Float,
        lensHeightDp: Float,
        lensAmountDp: Float,
        spectral: Float,
        tintAlpha: Float,
        shadowOn: Boolean,
        glassOn: Boolean,
        contourEdge: Boolean,
    )

    private external fun nativeSetPanelStyle(blurDp: Float, padDp: Float, fillAlpha: Float)

    // ---- 原生液态玻璃模态弹窗（lgx::Modal） ----

    private external fun nativeDialogShow(
        title: String,
        message: String,
        cancelLabel: String,
        confirmLabel: String,
        showCancel: Boolean,
        fromOrigin: Boolean,
        originLeft: Float,
        originTop: Float,
        originRight: Float,
        originBottom: Float,
    )

    private external fun nativeDialogClose(code: Int)
    private external fun nativeDialogVisible(): Boolean

    /** 取走原生弹窗关闭结果：1=取消 2=确定；无结果为 -1。 */
    private external fun nativePollDialogResult(): Int

    companion object {
        private const val BACKDROP_INTERVAL_MS = 100L

        /**
         * [refreshBackdropNow] 的补采时刻（毫秒）。避开 100ms 节流阈值，并跨过 Compose 提交
         * 新主题帧所需的若干帧时间，保证最终采到的是新主题画面。
         */
        private val REFRESH_RETRY_DELAYS_MS = longArrayOf(90L, 200L, 420L)

        /** Button 变体：常规玻璃按钮。 */
        const val BUTTON_VARIANT_PLAIN = 0

        /** Button 变体：芯片（选中时强调色着色）。 */
        const val BUTTON_VARIANT_CHIP = 1

        /** Button 变体：纯图标按钮（方形框 → 圆形）。 */
        const val BUTTON_VARIANT_ICON = 2

        /** Button 变体：纯文字按钮（无玻璃面）。 */
        const val BUTTON_VARIANT_TEXT = 3

        /**
         * 原生库是否加载成功。加载失败（不支持的 ABI 等）时本视图不被创建，
         * 主界面按纯 Compose 运行，避免启动崩溃。
         */
        val nativeAvailable: Boolean = try {
            System.loadLibrary("lgm")
            true
        } catch (t: Throwable) {
            false
        }
    }
}
