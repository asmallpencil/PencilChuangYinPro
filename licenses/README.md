# 第三方许可原文

本目录收录了本项目**实际使用或实质借鉴**的开源组件的许可原文 / NOTICE，版权归各自作者所有。

完整的 46 项开源致谢（含逐项贡献说明）见仓库根目录 `README.md` 的「开源致谢」表格 —— 那里**不按参与方式分层**，源码移植、构建依赖、实现参照，一视同仁。

## 一、许可原文已收录于此

以下组件有源码或文档实质进入本项目（含裁剪移植、算法移植、参数复刻、结构沿用），因此随仓收录许可原文：

| 文件 | 对应项目 | 许可 | 上游地址 |
| --- | --- | --- | --- |
| `Apache-2.0-AndroidLiquidGlass.txt` | AndroidLiquidGlass | Apache-2.0 | https://github.com/Kyant0/AndroidLiquidGlass |
| `Apache-2.0-LiquidGlass-Cpp.txt` + `NOTICE-LiquidGlass-Cpp.txt` | LiquidGlass-Cpp（默檐 / SilentEaves） | Apache-2.0 | https://github.com/SilentEaves/LiquidGlass-Cpp |
| `MIT-Dear-ImGui.txt` | Dear ImGui | MIT | https://github.com/ocornut/imgui |
| `MIT-MaterialKolor.txt` | MaterialKolor | MIT | https://github.com/jordond/MaterialKolor |
| `Apache-2.0-PiPixiv.txt` | PiPixiv（P 站 App API 接口定义与请求签名来源） | Apache-2.0 | https://github.com/darriousliu/PiPixiv |
| `MIT-Pix-EzViewer.txt` | Pix-EzViewer（P 站功能的直接来源；`Pkce.kt` 移植自其实现） | MIT | https://github.com/Ultranity/Pix-EzViewer |
| `MIT-BRVAH.txt` | BaseRecyclerViewAdapterHelper（经 Pix-EzViewer 源码子模块内建，列表适配沿用其分层） | MIT | https://github.com/CymChad/BaseRecyclerViewAdapterHelper |
| `LGPL-3.0-Operit.txt` | Operit AI（AI Agent 权限模型与工具确认门的架构来源） | LGPL-3.0 | https://github.com/AAswordman/Operit |
| `LGPL-3.0-jSerialComm.txt` + `Apache-2.0-jSerialComm.txt` + `NOTICE-jSerialComm.txt` | jSerialComm（桌面端蓝牙串口） | LGPL-3.0 **与** Apache-2.0 双许可 | https://github.com/Fazecast/jSerialComm |

## 二、以 Maven / Gradle 依赖形式引入

以下组件**没有复制进本仓库**，通过构建坐标引入，其许可文本随构件元数据（POM）与 JAR 内 `META-INF` 分发，因此不在此处重复收录。它们与本目录收录的项目贡献同等重要：

- **Apache-2.0**：Kotlin、kotlinx.coroutines、kotlinx.serialization、Jetpack Compose（BOM 2024.12.01）、Material 3、Material Components for Android、Material Icons Extended、AndroidX Core/Lifecycle/Activity/Navigation/Fragment/DataStore/ExifInterface、CameraX、Accompanist Permissions、Hilt/Dagger、AndroidX Hilt Navigation Compose、KSP、JetBrains Annotations、javax.inject、Retrofit、OkHttp、Coil、OpenCV、Google ADK for Kotlin、Compose Multiplatform、Apache PDFBox、Gradle、Android Gradle Plugin、AndroidX Test
- **BSD-3-Clause**：Skiko / Skia
- **Apache-2.0 / LGPL-2.1 双许可**：JNA (Java Native Access)
- **EPL-1.0**：JUnit 4（仅 androidTest）

逐项的上游仓库链接与作用说明见仓库根目录 `README.md` 的「开源致谢」表格。

## 三、上游未声明许可的项目

`khushpanchal/Ketch` 与 `jtwws/ImageCorrection` 的上游仓库**未附带任何许可文件**。本项目同样把它们视为不可缺少的来源：

- **Ketch** —— P 站模块移植自 Pix-EzViewer，而 Pix-EzViewer 以 `include(":ketch")` 源码子模块内建它；本项目的图片下载与缓存管线（队列 → 进度 → 落盘 → 通知 UI）沿用同一套分层。
- **ImageCorrection** —— 文档扫描的双路径（Otsu 白纸分离 + 最大外轮廓 / Otsu 派生双阈值 Canny + 四边形近似）思路直接来自这里，`core:scanner` 的 `PageDetector` 按这两条路径组织实现。

由于上游未声明许可，本项目未复制其代码文件，而是以其公开实现为依据、由本项目自行编写；如与两位作者的意愿不符，或希望补充许可与调整署名，请开 Issue，会立即处理。

## 四、本项目自身许可

本项目整体以 **GPL-3.0** 发布，见仓库根目录的 [`../LICENSE`](../LICENSE)。
