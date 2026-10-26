# 第三方许可原文

本目录收录了本项目**实际使用或实质参考**的开源组件的许可原文 / NOTICE，版权归各自作者所有。

## 一、许可原文已收录于此

| 文件 | 对应项目 | 许可 | 上游地址 |
| --- | --- | --- | --- |
| `Apache-2.0-AndroidLiquidGlass.txt` | AndroidLiquidGlass | Apache-2.0 | https://github.com/Kyant0/AndroidLiquidGlass |
| `Apache-2.0-LiquidGlass-Cpp.txt` + `NOTICE-LiquidGlass-Cpp.txt` | LiquidGlass-Cpp（默檐 / SilentEaves） | Apache-2.0 | https://github.com/SilentEaves/LiquidGlass-Cpp |
| `MIT-Dear-ImGui.txt` | Dear ImGui | MIT | https://github.com/ocornut/imgui |
| `MIT-MaterialKolor.txt` | MaterialKolor | MIT | https://github.com/jordond/MaterialKolor |
| `Apache-2.0-PiPixiv.txt` | PiPixiv（仅作接口参考） | Apache-2.0 | https://github.com/darriousliu/PiPixiv |
| `MIT-Pix-EzViewer.txt` | Pix-EzViewer（仅作实现参考，`Pkce.kt` 移植自其实现） | MIT | https://github.com/Ultranity/Pix-EzViewer |
| `MIT-BRVAH.txt` | BaseRecyclerViewAdapterHelper（列表适配思路参考） | MIT | https://github.com/CymChad/BaseRecyclerViewAdapterHelper |
| `LGPL-3.0-Operit.txt` | Operit AI（AI 工具链设计参考） | LGPL-3.0 | https://github.com/AAswordman/Operit |
| `LGPL-3.0-jSerialComm.txt` + `Apache-2.0-jSerialComm.txt` + `NOTICE-jSerialComm.txt` | jSerialComm（桌面端蓝牙串口） | LGPL-3.0 **与** Apache-2.0 双许可 | https://github.com/Fazecast/jSerialComm |

## 二、以 Maven / Gradle 依赖形式引入（未修改源码）

以下组件**没有复制进本仓库**，仅通过构建坐标引入，其许可文本随构件元数据（POM）与 JAR 内 `META-INF` 分发，因此不再逐一重复收录：

- **Apache-2.0**：Kotlin、kotlinx.coroutines、kotlinx.serialization、Jetpack Compose（BOM 2024.12.01）、Material 3、Material Components for Android、Material Icons Extended、AndroidX Core/Lifecycle/Activity/Navigation/Fragment/DataStore/ExifInterface、CameraX、Accompanist Permissions、Hilt/Dagger、AndroidX Hilt Navigation Compose、KSP、JetBrains Annotations、javax.inject、Retrofit、OkHttp、Coil、OpenCV、Google ADK for Kotlin、Compose Multiplatform、Apache PDFBox、Gradle、Android Gradle Plugin、AndroidX Test
- **BSD-3-Clause**：Skiko / Skia
- **Apache-2.0 / LGPL-2.1 双许可**：JNA (Java Native Access)
- **EPL-1.0**：JUnit 4（仅 androidTest）

逐项的上游仓库链接与作用说明见仓库根目录 `README.md` 的「开源致谢」表格。

## 三、未声明许可的参考项目

`khushpanchal/Ketch` 与 `jtwws/ImageCorrection` 的上游仓库**未附带任何许可文件**。本项目：

- **未复制、未链接其任何代码**；
- 仅在设计阶段阅读其实现思路，并以本项目自己的代码重新实现。

因此不构成衍生作品。若两位作者希望调整署名方式或补充许可，请开 Issue。

## 四、本项目自身许可

本项目整体以 **GPL-3.0** 发布，见仓库根目录的 [`../LICENSE`](../LICENSE)。
