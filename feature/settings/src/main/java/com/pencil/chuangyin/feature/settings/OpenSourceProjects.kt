package com.pencil.chuangyin.feature.settings

/**
 * 本应用使用的开源项目清单（名称 + 开发团队/作者 + 开源协议 + 主页链接）。
 * 新增依赖时同步补充，保证许可证披露完整。
 */
internal data class OpenSourceProject(
    val name: String,
    val author: String,
    val license: String,
    val url: String,
)

/** 开源项目列表（按字母序）。 */
internal val OPEN_SOURCE_PROJECTS: List<OpenSourceProject> = listOf(
    OpenSourceProject("Accompanist Permissions", "Google", "Apache-2.0", "https://github.com/google/accompanist"),
    OpenSourceProject("AndroidLiquidGlass（Kotlin/Compose 液态玻璃实现）", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
    OpenSourceProject("AndroidX Activity Compose", "Google / JetBrains", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
    OpenSourceProject("AndroidX CameraX", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/camera"),
    OpenSourceProject("AndroidX Core KTX", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
    OpenSourceProject("AndroidX DataStore", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    OpenSourceProject("AndroidX ExifInterface", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/exifinterface"),
    OpenSourceProject("AndroidX Fragment", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/fragment"),
    OpenSourceProject("AndroidX Hilt Navigation Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/hilt"),
    OpenSourceProject("AndroidX Lifecycle", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    OpenSourceProject("AndroidX Navigation Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    OpenSourceProject("Coil", "coil-kt 团队", "Apache-2.0", "https://github.com/coil-kt/coil"),
    OpenSourceProject("Dagger / Hilt", "Google / Square", "Apache-2.0", "https://github.com/google/dagger"),
    // AI 工具链 @Tool/@Param/generatedTools 注解机制的核心实现（core:agent 引入官方运行时，
    // feature:ai 通过官方 google-adk-kotlin-processor 在编译期生成工具包装器，minSdk 24）。
    OpenSourceProject("Google ADK for Kotlin（AI Agent 工具链）", "Google", "Apache-2.0", "https://github.com/google/adk-kotlin"),
    OpenSourceProject("ImageCorrection", "jtwws", "MIT", "https://github.com/jtwws/ImageCorrection.git"),
    OpenSourceProject("Dear ImGui", "Omar Cornut (ocornut)", "MIT", "https://github.com/ocornut/imgui"),
    OpenSourceProject("Jetpack Compose", "Google / JetBrains", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    OpenSourceProject("Kotlin", "JetBrains", "Apache-2.0", "https://kotlinlang.org/"),
    OpenSourceProject("kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceProject("kotlinx.serialization", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    OpenSourceProject("Kyant0/shapes（形状库，液态玻璃透镜折射所需）", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/shapes"),
    OpenSourceProject("LiquidGlass-Cpp（液态玻璃 C++ 移植）", "默檐 (SilentEaves)", "Apache-2.0", "https://github.com/SilentEaves/LiquidGlass-Cpp"),
    OpenSourceProject("Material Components", "Google", "Apache-2.0", "https://github.com/material-components/material-components-android"),
    OpenSourceProject("Material3", "Google / JetBrains", "Apache-2.0", "https://m3.material.io/"),
    OpenSourceProject("MaterialKolor", "Jordond", "Apache-2.0", "https://github.com/jordond/MaterialKolor"),
    OpenSourceProject("OkHttp", "Square", "Apache-2.0", "https://square.github.io/okhttp/"),
    OpenSourceProject("OpenCV", "OpenCV 团队", "Apache-2.0", "https://opencv.org/"),
    // AI 助手工具链/多模态能力的设计参考（工具调用、文件与图片理解等思路借鉴）。
    OpenSourceProject("Operit AI（AI 助手工具链/多模态参考）", "AAswordman", "LGPL-3.0", "https://github.com/AAswordman/Operit"),
    // P 站功能开发参考的开源第三方 Pixiv 客户端（接口与实现参考，未引入其二进制制品）。
    OpenSourceProject("PiPixiv（Pixiv 客户端参考）", "darriousliu", "MIT", "https://github.com/darriousliu/PiPixiv"),
    OpenSourceProject("Pix-EzViewer（Pixiv 客户端参考）", "ultranity", "MIT", "https://github.com/ultranity/Pix-EzViewer"),
    OpenSourceProject("Retrofit", "Square", "Apache-2.0", "https://square.github.io/retrofit/"),
)