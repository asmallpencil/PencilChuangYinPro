package com.pencil.chuangyin.feature.camera.camera

import androidx.compose.ui.graphics.Color

/**
 * 取景器固定深色场景配色。
 *
 * 相机预览的背景是实时画面（不受应用主题控制），其上控件必须保持高对比白色；
 * 这是相机类 App 的通用做法，与主题色板无关，故集中在此单一 token 中，
 * 业务页面仍一律走 MaterialTheme.colorScheme。
 */
internal object CameraTokens {
    val scrim: Color = Color.Black
    val content: Color = Color.White
    val contentSecondary: Color = Color.White.copy(alpha = 0.72f)
    val panel: Color = Color.Black.copy(alpha = 0.42f)
    val gridLine: Color = Color.White.copy(alpha = 0.22f)

    /** 文档检测框强调色（取景画面上的专用橙色，不随主题变化，相机场景例外）。 */
    val pageAccent: Color = Color(0xFFFF9500)
}
