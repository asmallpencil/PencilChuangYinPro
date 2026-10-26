package com.pencil.chuangyin.feature.settings

/**
 * 本应用使用的开源项目清单（名称 + 开发团队/作者 + 开源协议 + 主页链接）。
 * 新增依赖时同步补充，保证许可证披露完整；与仓库根目录 README.md 的致谢表保持一致。
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
    OpenSourceProject("AndroidX Activity Compose", "Google / JetBrains", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/activity"),
    OpenSourceProject("AndroidX CameraX", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/camera"),
    OpenSourceProject("AndroidX Core KTX", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
    OpenSourceProject("AndroidX DataStore", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    OpenSourceProject("AndroidX ExifInterface", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/exifinterface"),
    OpenSourceProject("AndroidX Fragment", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/fragment"),
    OpenSourceProject("AndroidX Hilt Navigation Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/hilt"),
    OpenSourceProject("AndroidX Lifecycle", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    OpenSourceProject("AndroidX Navigation Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    OpenSourceProject("AndroidX Test（仪器测试运行器）", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/test"),
    OpenSourceProject("AndroidLiquidGlass（Kotlin/Compose 液态玻璃实现）", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/AndroidLiquidGlass"),
    OpenSourceProject("Coil", "coil-kt 团队", "Apache-2.0", "https://github.com/coil-kt/coil"),
    OpenSourceProject("Dagger / Hilt", "Google / Square", "Apache-2.0", "https://github.com/google/dagger"),
    // AI 工具链 @Tool/@Param/generatedTools 注解机制的核心实现（core:agent 引入官方运行时，
    // feature:ai 通过官方 google-adk-kotlin-processor 在编译期生成工具包装器，minSdk 24）。
    OpenSourceProject("Google ADK for Kotlin（AI Agent 工具链）", "Google", "Apache-2.0", "https://github.com/google/adk-kotlin"),
    OpenSourceProject("Dear ImGui", "Omar Cornut (ocornut)", "MIT", "https://github.com/ocornut/imgui"),
    OpenSourceProject("JetBrains Annotations", "JetBrains", "Apache-2.0", "https://github.com/JetBrains/java-annotations"),
    OpenSourceProject("Jetpack Compose", "Google / JetBrains", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    OpenSourceProject("JUnit 4", "JUnit 团队", "EPL-1.0", "https://github.com/junit-team/junit4"),
    OpenSourceProject("KSP（Kotlin Symbol Processing）", "Google / JetBrains", "Apache-2.0", "https://github.com/google/ksp"),
    OpenSourceProject("Kotlin", "JetBrains", "Apache-2.0", "https://kotlinlang.org/"),
    OpenSourceProject("Kyant0/shapes（形状库，液态玻璃透镜折射所需）", "Kyant0", "Apache-2.0", "https://github.com/Kyant0/shapes"),
    OpenSourceProject("LiquidGlass-Cpp（液态玻璃 C++ 移植）", "默檐 (SilentEaves)", "Apache-2.0", "https://github.com/SilentEaves/LiquidGlass-Cpp"),
    OpenSourceProject("Material 3（含 Expressive）", "Google / JetBrains", "Apache-2.0", "https://m3.material.io/"),
    OpenSourceProject("Material Components for Android", "Google", "Apache-2.0", "https://github.com/material-components/material-components-android"),
    OpenSourceProject("Material Icons Extended", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/compose-material"),
    OpenSourceProject("MaterialKolor", "Jordond", "MIT", "https://github.com/jordond/MaterialKolor"),
    OpenSourceProject("OkHttp", "Square", "Apache-2.0", "https://square.github.io/okhttp/"),
    OpenSourceProject("OpenCV", "OpenCV 团队", "Apache-2.0", "https://opencv.org/"),
    // P 站功能开发参考的开源第三方 Pixiv 客户端（接口与实现参考，未引入其二进制制品）。
    OpenSourceProject("PiPixiv（Pixiv 客户端参考）", "darriousliu", "Apache-2.0", "https://github.com/darriousliu/PiPixiv"),
    OpenSourceProject("Pix-EzViewer（Pixiv 客户端参考）", "Ultranity", "MIT", "https://github.com/Ultranity/Pix-EzViewer"),
    OpenSourceProject("Retrofit", "Square", "Apache-2.0", "https://square.github.io/retrofit/"),
    OpenSourceProject("kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    OpenSourceProject("kotlinx.serialization", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    // AI 助手工具链/多模态能力的设计参考（工具调用、文件与图片理解等思路借鉴）。
    OpenSourceProject("Operit AI（AI 助手工具链/多模态参考）", "AAswordman", "LGPL-3.0", "https://github.com/AAswordman/Operit"),
    // 文档扫描算法思路参考（Otsu / Canny / 四边形近似），未复制其代码；上游未声明许可。
    OpenSourceProject("ImageCorrection（文档校正算法参考）", "jtwws", "未声明", "https://github.com/jtwws/ImageCorrection"),
    // 以下为 Windows 桌面端专属依赖。
    OpenSourceProject("Compose Multiplatform（桌面端 UI）", "JetBrains", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform"),
    OpenSourceProject("Skiko（桌面端 Skia 绑定）", "JetBrains", "BSD-3-Clause", "https://github.com/JetBrains/skiko"),
    OpenSourceProject("Apache PDFBox（PDF 栅格化）", "Apache 基金会", "Apache-2.0", "https://github.com/apache/pdfbox"),
    OpenSourceProject("JNA（Winsock RFCOMM 蓝牙直连）", "JNA 社区", "Apache-2.0 / LGPL-2.1", "https://github.com/java-native-access/jna"),
    OpenSourceProject("jSerialComm（蓝牙串口 COMx）", "Fazecast", "LGPL-3.0 / Apache-2.0", "https://github.com/Fazecast/jSerialComm"),
)
