# 铅笔创印 Pro · Pencil ChuangYin Pro

> 一支笔，一张纸，一台蓝牙热敏打印机 —— 把屏幕里的文字、图片、照片和小说的即刻印出来。
> Android 与 Windows 双端同源，共用同一套液态玻璃视觉语言与打印协议栈。

<p>
  <a href="LICENSE"><img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue.svg"></a>
  <img alt="AI-built" src="https://img.shields.io/badge/built%20with-Trae%20%2B%20DeepSeek%20V4.1F-8A2BE2.svg">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.x-7F52FF.svg">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4.svg">
  <img alt="Android" src="https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84.svg">
  <img alt="Windows" src="https://img.shields.io/badge/Windows-10%2F11-0078D4.svg">
  <img alt="Version" src="https://img.shields.io/badge/version-2.0.0.0-orange.svg">
</p>

---

## ⚠️ 首先说明：这是一个 AI 构建的项目

**本项目是一个 AI 原生（AI-built）项目，绝大部分代码由 AI 编程助手生成、重构与维护，而非传统意义上逐行手写的工程。**

| 项 | 说明 |
| --- | --- |
| AI IDE / Agent | **[Trae](https://www.trae.ai)** —— 全程在 Trae 中完成需求拆解、方案设计、编码、编译排错与文档撰写 |
| 驱动模型 | **DeepSeek V4.1F**（DS V4.1F） |
| 人类承担的部分 | 提出需求、确定产品方向与设计约束（UI 统一、启动不强制登录、双端同步等）、评估产出、实机验证与最终取舍 |
| 协作方式 | 人给方向与验收标准 → AI 读写代码、跑 Gradle 构建、定位报错并迭代 → 人复核并决定是否采纳 |

因此：

- 代码的**架构与实现是 AI 与人类协作的结果**，风格上会带有明显的「AI 辅助生成」特征（注释密度高、模块划分规整、命名偏描述性）；
- AI 生成不代表免于审查 —— 遇到问题时请以源码与实际行为为准，欢迎开 Issue 纠正；
- 我们在此**如实标注 AI 参与程度**，不做「纯手工打造」的包装。这也是本项目选择 GPL-3.0 的原因之一：AI 辅助产出的代码同样应当接受开源共同体的审视与改进。

> 如果你在别处看到这个项目，请以此处说明为准。

---

## 这是什么

铅笔创印 Pro 是一款面向**蓝牙热敏打印机**（58mm / 80mm 小票机、便携错题机、错题打印机等）的内容创作与打印工具。它不只做「把一段文字丢给打印机」，而是把**排版、图片处理、拍照扫描、素材来源和 AI 助手**串成一条完整的「从想法到纸面」的链路。

项目是**多模块 Android 工程 + Compose Multiplatform 桌面端**：Android 端为主形态，Windows 桌面端复用同一套 P 站协议实现与打印协议栈，UI 以「安卓大屏/平板形态」呈现同一套信息架构。

## 功能一览

### 打印

- **蓝牙打印链路**：设备配对 → 连接 → 状态/电量回读 → 逐行栅格化 → `GS v 0` 位图分包下发，支持打印中断、进度回调、走纸测试。
- **多通道传输**：Android 走 `BluetoothSocket`（SPP / 不安全 SPP 双策略）；Windows 走 Winsock `AF_BTH` RFCOMM 直连，或已配对生成的「蓝牙链接上的标准串行 (COMx)」虚拟串口。
- **文本打印**：富文本编辑、字号字距、对齐、分页、纸张宽度（58/80mm）自适应，逐行位图渲染保证中文字形。
- **图片打印**：裁剪、旋转、滤镜、抖动/二值化（含 Sauvola 等自适应算法）、PDF 导入分页、多页批量。
- **图表与表格**：AI 助手可生成表格、图表并直接送入打印队列。

### 拍照与扫描

- CameraX 实时取景 + OpenCV 文档边缘检测、透视校正、去阴影，拍完即打印。

### Pixiv（P 站）图库

- 浏览 / 搜索 / 排行榜 / 关注流，作品详情、评论区（读取与发表）、收藏、下载、**一键打印全部页**。
- 小说阅读：正文排版、收藏、下载、直接送入文本打印页。
- 独立实现的 App API 客户端（Retrofit + OkHttp + kotlinx.serialization），仅作个人学习与研究用途。

### AI 打印助手

- 基于 OpenAI 兼容接口的 Agent 运行时（SSE 流式、工具调用、上下文折叠、模型配置仓库）。
- 使用 **Google ADK for Kotlin** 的 `@Tool` / `@Param` 注解 + KSP 处理器生成工具描述，内置「存储 / 打印 / 绘图 / 表格 / 联网检索」等工具集。
- 启动即进首页，P 站只是三个顶级 Tab 之一 —— 不登录也能用打印。

### 界面

- **液态玻璃（Liquid Glass）**：backdrop 模糊、透镜折射、边缘高光、噪点、弹簧动画一整套参数化实现；Android 侧另有 C++（Dear ImGui + OpenGL ES）玻璃渲染管线。
- **动态取色**：基于 MaterialKolor 从壁纸/图片生成 Material 3 Expressive 配色，支持手动调色盘与深浅色。

## 架构

```
app                  Application 装配、导航宿主、权限入口
core/
  printer            打印协议栈（扫描 / 连接 / 栅格化 / 分包 / 状态轮询）
  scanner            OpenCV 文档扫描算法
  agent              OpenAI 兼容 Agent 内核（SSE、工具协议、上下文折叠）
  backdrop           液态玻璃 Compose 实现（vendor 自 AndroidLiquidGlass）
  kyant-shapes       圆角形状 / 透镜折射所需的 Shape API（vendor 自 Kyant0/shapes）
  theme              动态取色、字体、玻璃主题
  ui                 通用玻璃组件库
  resources          资源与品牌
feature/
  dashboard          首页、蓝牙打印机连接页
  text               文本编辑与打印
  image              图片处理与打印
  camera             拍照扫描
  pixiv              P 站浏览 / 评论 / 下载 / 打印
  ai                 AI 打印助手（会话、工具、UI）
  settings           设置
  onboarding         首次引导
desktopApp           Windows 桌面端（Compose Multiplatform / JVM → app-image）
```

- 依赖注入统一使用 **Hilt**；状态以 `StateFlow` + 单向数据流驱动。
- 打印协议层与平台无关，仅「传输通道」和「位图渲染后端」按平台实现（Android `Bitmap` / Desktop Skia）。

## 构建

### 环境

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.13（仓库自带 wrapper） |
| Android Gradle Plugin | 8.10.1 |
| Kotlin | 2.2.20 |
| NDK | 26.3.11579264（仅 Android，C++ 玻璃管线需要） |
| CMake | 3.22.1 |

### Android

```bash
# 调试包
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 发布包（开启 R8 混淆与资源压缩）
./gradlew :app:assembleRelease
```

### Windows 桌面版

```bash
# 产出可直接运行的 app-image（内含 PencilChuangYinPro.exe）
./gradlew :desktopApp:createDistributable
# 产物：desktopApp/build/compose/binaries/main/app/PencilChuangYinPro/
```

> 说明：`packageMsi` / `packageExe` 需要 WiX，若你的网络无法访问 GitHub 下载 WiX，直接使用 `createDistributable` 的 app-image 即可，双击 `PencilChuangYinPro.exe` 运行。

## 下载

安装包与源码归档都在 **[Releases](https://github.com/asmallpencil/PencilChuangYinPro/releases)** 页面。

当前版本 **v2.0.0.0**：

| 平台 | 文件 | 说明 |
| --- | --- | --- |
| Windows 10/11 x64 | [`PencilChuangYinPro-2.0.0.0-windows-x64.zip`](https://github.com/asmallpencil/PencilChuangYinPro/releases/download/v2.0.0.0/PencilChuangYinPro-2.0.0.0-windows-x64.zip) | 解压后双击 `PencilChuangYinPro.exe` 运行，免安装 |
| Android 7.0+（arm64-v8a / armeabi-v7a） | [`PencilChuangYinPro-2.0.0.0-debug.apk`](https://github.com/asmallpencil/PencilChuangYinPro/releases/download/v2.0.0.0/PencilChuangYinPro-2.0.0.0-debug.apk) | debug 签名包，安装前需允许「未知来源」 |

> APK 使用 debug 签名，仅供测试与自用；如需分发请自行配置 release 签名。

---

## 开源致谢

这个项目站在很多优秀的开源项目肩上。下面**按类别逐条列出所有实际参与构建、或对实现有实质影响的开源项目**，附许可与上游链接，向所有作者致谢。

> 标注说明：**① 入库源码** = 代码已 vendor 进本仓库；**② 构建依赖** = 以 Maven/Gradle 坐标引入、未修改源码；**③ 思路参考** = 未链接进产物，但实现思路/接口定义/行为观察对本项目有实质帮助。

### 一、界面与液态玻璃

| 项目 | 作者 | 作用 | 许可 | 引入方式 | 链接 |
| --- | --- | --- | --- | --- | --- |
| **AndroidLiquidGlass** | Kyant0 | 液态玻璃 Compose 实现（模糊、折射、高光、噪点）的主要来源，`core:backdrop` 即其裁剪移植版 | Apache-2.0 | ① 入库源码 | https://github.com/Kyant0/AndroidLiquidGlass |
| **Kyant0/shapes** | Kyant0 | 圆角矩形 / 连续曲率形状 API，液态玻璃透镜折射的基础，`core:kyant-shapes` 为其裁剪移植版 | Apache-2.0 | ① 入库源码 | https://github.com/Kyant0/shapes |
| **LiquidGlass-Cpp** | 默檐 / SilentEaves | Android 端 C++ 玻璃渲染管线（`app/src/main/cpp/lg`）的来源实现 | Apache-2.0 | ① 入库源码 | https://github.com/SilentEaves/LiquidGlass-Cpp |
| **Dear ImGui** | Omar Cornut & 贡献者 | C++ 即时模式 UI 框架，承载上述玻璃渲染与调试面板 | MIT | ① 入库源码 | https://github.com/ocornut/imgui |
| **MaterialKolor** | Jordond | 由壁纸/图片生成 Material 3 Expressive 动态配色 | **MIT** | ② 构建依赖 | https://github.com/jordond/MaterialKolor |

### 二、Android 平台与 UI 基础设施

| 项目 | 作者 | 作用 | 许可 | 引入方式 | 链接 |
| --- | --- | --- | --- | --- | --- |
| **Kotlin** | JetBrains | 全项目开发语言 | Apache-2.0 | ② 构建依赖 | https://github.com/JetBrains/kotlin |
| **Jetpack Compose**（BOM 2024.12.01） | Google / JetBrains | 全部声明式 UI（Foundation / UI / Graphics / Tooling） | Apache-2.0 | ② 构建依赖 | https://github.com/androidx/androidx |
| **Material 3（含 Expressive 1.3.1）** | Google / JetBrains | 组件与设计语言 | Apache-2.0 | ② 构建依赖 | https://github.com/androidx/androidx |
| **Material Icons Extended** | Google | 扩展图标集 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/compose-material |
| **Material Components for Android** | Google | 传统 XML 主题与组件基座（`com.google.android.material:material:1.12.0`） | Apache-2.0 | ② 构建依赖 | https://github.com/material-components/material-components-android |
| **AndroidX Core KTX** | Google | Kotlin 扩展与基础能力 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/core |
| **AndroidX Lifecycle** | Google | ViewModel / 生命周期感知 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| **AndroidX Activity Compose** | Google | Compose 宿主 Activity、`registerForActivityResult` | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/activity |
| **AndroidX Navigation Compose** | Google | 页面导航 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/navigation |
| **AndroidX Fragment KTX** | Google | Fragment 兼容层 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/fragment |
| **AndroidX DataStore** | Google | 键值持久化（设置、模型配置） | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/datastore |
| **AndroidX ExifInterface** | Google | 照片 EXIF 方向归一 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/exifinterface |
| **CameraX** | Google | 拍照取景与图像采集 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/camera |
| **Accompanist Permissions** | Google | 运行时权限封装 | Apache-2.0 | ② 构建依赖 | https://github.com/google/accompanist |
| **Hilt / Dagger** | Google / Square | 依赖注入 | Apache-2.0 | ② 构建依赖 | https://github.com/google/dagger |
| **AndroidX Hilt Navigation Compose** | Google | Hilt × Navigation 集成 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/hilt |
| **kotlinx.serialization** | JetBrains | JSON 序列化（P 站接口、AI 工具参数、会话持久化） | Apache-2.0 | ② 构建依赖 | https://github.com/Kotlin/kotlinx.serialization |
| **kotlinx.coroutines** | JetBrains | 协程与 Flow | Apache-2.0 | ② 构建依赖 | https://github.com/Kotlin/kotlinx.coroutines |
| **JetBrains Annotations** | JetBrains | `@NotNull` 等编译期注解 | Apache-2.0 | ② 构建依赖 | https://github.com/JetBrains/java-annotations |
| **javax.inject** | JSR-330 | `@Inject` 注解（桌面端复用 Android 侧会话类） | Apache-2.0 | ② 构建依赖 | https://github.com/javax-inject/javax-inject |

### 三、图像、网络与 AI

| 项目 | 作者 | 作用 | 许可 | 引入方式 | 链接 |
| --- | --- | --- | --- | --- | --- |
| **OpenCV** | OpenCV 团队 | 文档扫描（边缘检测、透视变换、去阴影）与图像预处理 | Apache-2.0 | ② 构建依赖 | https://github.com/opencv/opencv |
| **Coil** | coil-kt 团队 | 图片加载与缓存 | Apache-2.0 | ② 构建依赖 | https://github.com/coil-kt/coil |
| **Retrofit** | Square | P 站 App API 的 HTTP 客户端封装 | Apache-2.0 | ② 构建依赖 | https://github.com/square/retrofit |
| **OkHttp** | Square | HTTP/2、连接池、SSE 流式读取 | Apache-2.0 | ② 构建依赖 | https://github.com/square/okhttp |
| **Google ADK for Kotlin** | Google | Agent 工具协议（`@Tool` / `@Param` 注解 + KSP 生成器） | Apache-2.0 | ② 构建依赖 | https://github.com/google/adk-kotlin |

### 四、Windows 桌面端

| 项目 | 作者 | 作用 | 许可 | 引入方式 | 链接 |
| --- | --- | --- | --- | --- | --- |
| **Compose Multiplatform** | JetBrains | 桌面端 UI（与 Android 共享设计语言） | Apache-2.0 | ② 构建依赖 | https://github.com/JetBrains/compose-multiplatform |
| **Skia / Skiko** | Google / JetBrains | 桌面端位图绘制与栅格化（随 Compose 提供） | BSD-3-Clause | ② 构建依赖 | https://github.com/JetBrains/skiko |
| **Apache PDFBox** | Apache 基金会 | PDF 导入：取页数与栅格化（桌面端无 Android PdfRenderer） | Apache-2.0 | ② 构建依赖 | https://github.com/apache/pdfbox |
| **JNA (Java Native Access)** | JNA 社区 | 直接调用 Winsock `AF_BTH` 做 RFCOMM 蓝牙直连 | Apache-2.0 / LGPL-2.1 双许可 | ② 构建依赖 | https://github.com/java-native-access/jna |
| **jSerialComm** | Fazecast | 读写「蓝牙链接上的标准串行 (COMx)」虚拟串口 | **LGPL-3.0 / Apache-2.0 双许可** | ② 构建依赖 | https://github.com/Fazecast/jSerialComm |

### 五、构建工具链与测试

| 项目 | 作者 | 作用 | 许可 | 引入方式 | 链接 |
| --- | --- | --- | --- | --- | --- |
| **Gradle** | Gradle Inc. | 构建系统（仓库自带 wrapper 8.13） | Apache-2.0 | ② 构建依赖 | https://github.com/gradle/gradle |
| **Android Gradle Plugin** | Google | Android 构建插件 | Apache-2.0 | ② 构建依赖 | https://developer.android.com/build |
| **KSP（Kotlin Symbol Processing）** | Google / JetBrains | Hilt 与 ADK 注解的编译期代码生成 | Apache-2.0 | ② 构建依赖 | https://github.com/google/ksp |
| **JUnit 4** | JUnit 团队 | 单元测试框架（仅 androidTest 使用） | EPL-1.0 | ② 构建依赖 | https://github.com/junit-team/junit4 |
| **AndroidX Test Ext JUnit** | Google | Android 仪器测试运行器（仅 androidTest 使用） | Apache-2.0 | ② 构建依赖 | https://developer.android.com/jetpack/androidx/releases/test |

### 六、协议与实现参考

以下项目**未被链接进构建产物**，但它们的实现思路、接口定义或行为观察对本项目有实质帮助，同样致以诚挚感谢：

| 项目 | 作者 | 参考内容 | 许可 | 链接 |
| --- | --- | --- | --- | --- |
| **PiPixiv** | darriousliu | P 站 App API 的接口定义与请求签名方式 | Apache-2.0 | https://github.com/darriousliu/PiPixiv |
| **Pix-EzViewer** | Ultranity / Perol_Notsfsssf | P 站客户端的信息架构、网络层组织与 OAuth PKCE 流程（`Pkce.kt` 直接移植自其实现） | MIT | https://github.com/Ultranity/Pix-EzViewer |
| **BaseRecyclerViewAdapterHelper (BRVAH)** | CymChad | 列表适配与分页思路 | **MIT** | https://github.com/CymChad/BaseRecyclerViewAdapterHelper |
| **Ketch** | khushpanchal | 图片下载/加载管线的设计取舍（本项目最终未采用，仅作方案比对） | 上游未声明许可 | https://github.com/khushpanchal/Ketch |
| **Operit AI** | AAswordman | AI 助手工具链与多模态能力的设计参考（工具调用、文件与图片理解的思路） | LGPL-3.0 | https://github.com/AAswordman/Operit |
| **ImageCorrection** | jtwws | 基于 OpenCV 的图像校正程序，文档扫描路径（Otsu / Canny / 四边形近似）的思路参考 | 上游未声明许可 | https://github.com/jtwws/ImageCorrection |

> **关于「未声明许可」的两个项目**：本仓库**未复制其任何代码**，仅在设计阶段读过其实现思路并自行重写，因此不构成衍生作品；若作者希望调整署名方式，请开 Issue。

### 七、特别感谢

- **Kyant**（[Kyant0](https://github.com/Kyant0)）—— AndroidLiquidGlass 与 shapes 让「液态玻璃」在 Compose 里真正成立。
- **默檐**（[SilentEaves](https://github.com/SilentEaves)）—— LiquidGlass-Cpp 提供了完整的玻璃渲染管线与参数体系。
- **Omar Cornut** 及 Dear ImGui 全体贡献者 —— 让 C++ 侧的即时光学实验成为可能。
- **darriousliu**（PiPixiv）、**Ultranity / Perol_Notsfsssf**（Pix-EzViewer）—— P 站 API 的公开实现为社区铺了路。
- **AAswordman**（Operit）、**jtwws**（ImageCorrection）—— AI 工具链与文档扫描两块的思路启发。
- AndroidX、JetBrains、Square、Google、Apache 基金会等所有把基础设施开源出来的团队。

> 若你是上述项目的作者，发现署名或链接有误、或希望调整致谢方式，请开 Issue，我会尽快处理。

## 许可

本项目以 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

这意味着你可以自由使用、修改、再发布本项目，但**衍生作品也必须以 GPL-3.0 开源**，并保留原作者署名与许可声明。

部分第三方组件（如 **jSerialComm**、**JNA**、**Operit**）采用 LGPL 许可，本项目仅以动态链接 / 未修改源码的方式使用它们，符合 LGPL 的再链接要求。

第三方组件的许可原文收录于 [`licenses/`](licenses/) 目录，其版权归各自作者所有。

## 免责声明

- 本项目是**非官方**第三方工具，与 Pixiv Inc. 无任何关联，也未获其授权或认可。
- P 站相关功能仅用于**个人学习、技术研究与内容备份**；请遵守 Pixiv 的服务条款与所在地法律法规，不要用于任何商业或侵权用途。你所访问的内容与账号行为由你自行负责。
- 蓝牙热敏打印协议依据设备**实际通信行为**（抓包观测）与公开的指令示例整理实现，**未包含任何厂商闭源 SDK 的二进制或反编译代码**。不同型号打印机的指令集存在差异，可能需要在设置中调整纸张宽度/浓度。
- 本项目由 AI 辅助生成，代码虽经实机验证，仍可能存在未覆盖的边界情况。使用时请先在废纸上试打。
- 本项目按「现状」提供，不附带任何担保。使用本软件造成的硬件损坏、耗材浪费或数据丢失，作者不承担责任。

## 参与贡献

欢迎 Issue 与 PR。提交前请确保：

1. `./gradlew :app:assembleDebug` 与 `./gradlew :desktopApp:compileKotlin` 均能通过；
2. 新增的第三方代码/资源已在 README 致谢表中补充来源与许可（包括新增的 Maven 依赖）；
3. 不提交任何密钥、账号信息或他人版权素材；
4. 若使用 AI 工具生成代码，请在 PR 描述中注明，便于评审。
