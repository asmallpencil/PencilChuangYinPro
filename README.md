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
- App API 客户端：接口清单、签名参数与 PKCE 流程来自 **Pix-EzViewer / PiPixiv**，以 Retrofit + OkHttp + kotlinx.serialization 实现，仅作个人学习与研究用途。

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

这个项目的每一行代码，都建立在别人的肩膀上。

下面**不再按「类别」或「使用深度」分层，所有项目一视同仁地列在同一张表里** —— 无论是被 vendor 进仓库的源码、Gradle 坐标引入的依赖，还是被移植、仿写、参照过的实现，都是这个项目得以存在的原因，贡献同等重要。

**排名不分先后，贡献不论形式，共同鸣谢。**

| 项目 | 作者 | 许可 | 对本项目的贡献 | 链接 |
| --- | --- | --- | --- | --- |
| **Accompanist Permissions** | Google | Apache-2.0 | 相机、相册、蓝牙等运行时权限的封装，`rememberMultiplePermissionsState` 是拍照扫描与打印入口的前置门 | https://github.com/google/accompanist |
| **Android Gradle Plugin** | Google | Apache-2.0 | Android 构建插件；多模块工程、CMake 原生构建、R8 混淆与资源压缩均由其驱动 | https://developer.android.com/build |
| **AndroidLiquidGlass** | Kyant0 | Apache-2.0 | **液态玻璃的起点**。`core:backdrop` 即其裁剪移植版，提供 backdrop 模糊、透镜折射、边缘高光、噪点与弹簧动画的完整参数体系；"液态玻璃"在本项目里能成立，靠的就是这套实现 | https://github.com/Kyant0/AndroidLiquidGlass |
| **AndroidX Activity Compose** | Google | Apache-2.0 | Compose 宿主 Activity、`registerForActivityResult` 启动系统相机与文件选择器的通道 | https://developer.android.com/jetpack/androidx/releases/activity |
| **AndroidX CameraX** | Google | Apache-2.0 | 拍照扫描的取景与图像采集管线，`PreviewView` + `ImageAnalysis` 实时把帧交给 OpenCV | https://developer.android.com/jetpack/androidx/releases/camera |
| **AndroidX Core KTX** | Google | Apache-2.0 | Kotlin 扩展与基础能力（`Bundle`、`Context`、`Uri` 等），全模块通用底座 | https://developer.android.com/jetpack/androidx/releases/core |
| **AndroidX DataStore** | Google | Apache-2.0 | 设置项、AI 模型配置、P 站账号令牌的键值持久化 | https://developer.android.com/jetpack/androidx/releases/datastore |
| **AndroidX ExifInterface** | Google | Apache-2.0 | 相册照片的 EXIF 方向归一，避免导入图片打印出来是躺倒的 | https://developer.android.com/jetpack/androidx/releases/exifinterface |
| **AndroidX Fragment KTX** | Google | Apache-2.0 | P 站模块采用「Compose 宿主 + `FragmentContainerView` 承载 `MainHostFragment`」的混合结构，本依赖是该宿主结构的编译基础 | https://developer.android.com/jetpack/androidx/releases/fragment |
| **AndroidX Hilt Navigation Compose** | Google | Apache-2.0 | `hiltViewModel()` 把 Hilt 注入的 ViewModel 绑定到导航图，各页面状态持有者的接线方式 | https://developer.android.com/jetpack/androidx/releases/hilt |
| **AndroidX Lifecycle** | Google | Apache-2.0 | ViewModel、`collectAsStateWithLifecycle` 与生命周期感知，全部页面状态流的承载者 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| **AndroidX Navigation Compose** | Google | Apache-2.0 | 三个顶级 Tab（首页 / P 站 / AI）与各功能页之间的导航图与返回栈 | https://developer.android.com/jetpack/androidx/releases/navigation |
| **AndroidX Test Ext JUnit** | Google | Apache-2.0 | 扫描管线的仪器测试运行器（`androidTest`） | https://developer.android.com/jetpack/androidx/releases/test |
| **Apache PDFBox** | Apache 基金会 | Apache-2.0 | 桌面端 PDF 导入：取页数、按页栅格化（桌面端没有 Android 的 `PdfRenderer`，这是唯一可行路线） | https://github.com/apache/pdfbox |
| **BaseRecyclerViewAdapterHelper (BRVAH)** | CymChad | MIT | 列表适配与多类型 Item 的组织方式。本项目 P 站模块移植自 Pix-EzViewer，而 Pix-EzViewer 以 `include(":BRVAH")` 源码子模块内建它，本项目沿用了同一套列表适配思路来实现 Compose 侧的列表与分页 | https://github.com/CymChad/BaseRecyclerViewAdapterHelper |
| **Coil** | coil-kt 团队 | Apache-2.0 | 图片加载与内存/磁盘缓存，P 站插图、图库缩略图与打印预览全靠它 | https://github.com/coil-kt/coil |
| **Compose Multiplatform** | JetBrains | Apache-2.0 | Windows 桌面端的整套 UI 运行时：与 Android 共用同一套 Composition、Layout 与绘制模型，才可能做到「双端 UI 统一」 | https://github.com/JetBrains/compose-multiplatform |
| **Dear ImGui** | Omar Cornut & 全体贡献者 | MIT | Android 端 C++ 即时模式 UI 框架，承载液态玻璃的光学实验与调试面板（`app/src/main/cpp`） | https://github.com/ocornut/imgui |
| **Google ADK for Kotlin** | Google | Apache-2.0 | AI 打印助手的工具协议内核：`@Tool` / `@Param` 注解 + KSP 处理器在编译期生成 `FunctionTool` 包装器，`core:agent` 与 `feature:ai` 的工具集全部建在其上 | https://github.com/google/adk-kotlin |
| **Gradle** | Gradle Inc. | Apache-2.0 | 构建系统本体；仓库自带 wrapper 8.13，克隆即可构建，无需预装 Gradle | https://github.com/gradle/gradle |
| **Hilt / Dagger** | Google / Square | Apache-2.0 | 全项目依赖注入：打印控制器、P 站会话、AI 引擎、各 ViewModel 均由它装配 | https://github.com/google/dagger |
| **ImageCorrection** | jtwws | 上游未声明 | 文档扫描的**双路径思路**直接来自这里：路径 A 用 Otsu 分离白纸与暗背景 + 形态学清理 + 最大外轮廓，路径 B 用 Otsu 派生双阈值 Canny + 轮廓四边形近似（`approxPolyDP`）。`core:scanner` 的 `PageDetector` 按这两条路径组织实现，缺了它就没有拍照扫描 | https://github.com/jtwws/ImageCorrection |
| **JNA (Java Native Access)** | JNA 社区 | Apache-2.0 / LGPL-2.1 双许可 | 桌面端绕过 Java 蓝牙栈的限制，直接调用 Winsock `AF_BTH` 做 RFCOMM 直连打印机 —— 这是 Windows 上「没有 COM 口也能打印」的关键 | https://github.com/java-native-access/jna |
| **javax.inject** | JSR-330 | Apache-2.0 | `@Inject` 标准注解；桌面端复用 Android 侧会话类时，靠它让两边构造器注入写法保持一致 | https://github.com/javax-inject/javax-inject |
| **JetBrains Annotations** | JetBrains | Apache-2.0 | `@NotNull` 等编译期注解，`core:backdrop` 与形状库的 API 契约标注 | https://github.com/JetBrains/java-annotations |
| **Jetpack Compose** | Google / JetBrains | Apache-2.0 | 全部声明式 UI（Foundation / UI / Graphics / Tooling，BOM 2024.12.01）。液态玻璃组件、打印预览、P 站图廊、AI 对话流全是 Compose | https://github.com/androidx/androidx |
| **jSerialComm** | Fazecast | LGPL-3.0 / Apache-2.0 双许可 | 桌面端读写「蓝牙链接上的标准串行 (COMx)」虚拟串口。Windows 会把打印机归到「其他设备」，不生成 COM 口时退回 RFCOMM 直连 —— 两条通道的串口一侧由它承担 | https://github.com/Fazecast/jSerialComm |
| **JUnit 4** | JUnit 团队 | EPL-1.0 | 扫描管线的单元测试（`androidTest`），用来验证文档边缘检测在斜拍、出界、暗光下的鲁棒性 | https://github.com/junit-team/junit4 |
| **Ketch** | khushpanchal | 上游未声明 | 图片下载管线的组织方式（队列、暂停/恢复、进度回调）。本项目 P 站模块移植自 Pix-EzViewer，而 Pix-EzViewer 以 `include(":ketch")` 源码子模块内建它；本项目的下载与缓存流程沿用了同一套分层（队列 → 进度 → 落盘 → 通知 UI） | https://github.com/khushpanchal/Ketch |
| **Kotlin** | JetBrains | Apache-2.0 | 全项目开发语言（Android + 桌面端 + Gradle 脚本） | https://github.com/JetBrains/kotlin |
| **kotlinx.coroutines** | JetBrains | Apache-2.0 | 协程与 Flow。打印任务的分包节流、AI 的 SSE 流式读取、P 站的分页加载全靠它 | https://github.com/Kotlin/kotlinx.coroutines |
| **kotlinx.serialization** | JetBrains | Apache-2.0 | JSON 序列化：P 站接口响应、AI 工具参数、会话与配置持久化 | https://github.com/Kotlin/kotlinx.serialization |
| **KSP (Kotlin Symbol Processing)** | Google / JetBrains | Apache-2.0 | 编译期代码生成：Hilt 的依赖图、ADK 的工具包装器都由它在构建期生成 | https://github.com/google/ksp |
| **Kyant0/shapes** | Kyant0 | Apache-2.0 | 圆角矩形与连续曲率形状 API，是液态玻璃透镜折射的几何基础。`core:kyant-shapes` 为其裁剪移植版，没有它玻璃边缘的折射就画不出来 | https://github.com/Kyant0/shapes |
| **LiquidGlass-Cpp** | 默檐 / SilentEaves | Apache-2.0 | Android 端 C++ 玻璃渲染管线（`app/src/main/cpp/lg`）的来源实现，提供完整的渲染顺序、采样与高光参数；其 `NOTICE` 亦声明包含 AndroidLiquidGlass 的代码 | https://github.com/SilentEaves/LiquidGlass-Cpp |
| **Material 3（含 Expressive）** | Google / JetBrains | Apache-2.0 | 组件与设计语言，Material 3 Expressive 1.3.1 提供动态形状与色彩过渡 | https://m3.material.io/ |
| **Material Components for Android** | Google | Apache-2.0 | 传统视图体系的组件基座。P 站模块承载 `MainHostFragment` 时，其公开超类型含本库的 `NavigationView.OnNavigationItemSelectedListener`，编译期必须可见 | https://github.com/material-components/material-components-android |
| **Material Icons Extended** | Google | Apache-2.0 | 扩展图标集，各页面的动作图标来源 | https://developer.android.com/jetpack/androidx/releases/compose-material |
| **MaterialKolor** | Jordond | MIT | 由壁纸/图片生成 Material 3 Expressive 动态配色，是「壁纸取色」与自适应主题的算法来源，也修好了调色盘里白色永远调不出来的问题 | https://github.com/jordond/MaterialKolor |
| **OkHttp** | Square | Apache-2.0 | HTTP/2、连接池与 SSE 流式读取。P 站接口、图片下载、AI 流式响应三条链路都跑在它上面 | https://github.com/square/okhttp |
| **OpenCV** | OpenCV 团队 | Apache-2.0 | 文档扫描的算法引擎：`adaptiveThreshold`、`Otsu`、`Canny`、`approxPolyDP`、透视变换与去阴影 | https://github.com/opencv/opencv |
| **Operit AI** | AAswordman | LGPL-3.0 | AI Agent 运行时的**架构范本**：工具调用的确认门（不可逆操作先问用户）、分档权限模式（总是询问 / 仅危险操作 / 放行）、上下文折叠与工具结果回灌。`core:agent` 的 `ToolConfirmationGate`、`AgentPermission`、`ContextFolder` 正是按这套模型实现的 | https://github.com/AAswordman/Operit |
| **PiPixiv** | darriousliu | Apache-2.0 | P 站 App API 的接口定义与请求签名方式。评论接口的必填参数与 header 组合即参照其实现对齐（否则会表现为「评论区永远显示暂无评论」） | https://github.com/darriousliu/PiPixiv |
| **Pix-EzViewer** | Ultranity / Perol_Notsfsssf | MIT | **P 站功能的直接来源**。① `PixivConfig.kt` 的 `client_id` / `client_secret` / UA / `X-Client-Hash` 盐值复刻自其 `RestClient`；② `Pkce.kt` 明确移植自其 `Pkce` 实现；③ `PixivApi.kt` 的接口清单与 `NovelWebTextParser` 的小说解析参照其 `PixivApiService` / `NovelViewModel.parseWebNovel`；④ 账号持久化替代了它的 Room 用户表；⑤ 页面信息架构与 UI 组织方式也以它为蓝本 | https://github.com/Ultranity/Pix-EzViewer |
| **Retrofit** | Square | Apache-2.0 | P 站 App API 的声明式 HTTP 封装，接口定义即文档 | https://github.com/square/retrofit |
| **Skia / Skiko** | Google / JetBrains | BSD-3-Clause | 桌面端的位图绘制与栅格化后端（随 Compose 提供）。文本打印的逐行位图、图片抖动与预览都经由它落到屏幕与打印机 | https://github.com/JetBrains/skiko |

### 共同鸣谢

以上 46 个项目，参与方式各不相同 —— 有的是被 vendor 进仓库的源码，有的是 Gradle 坐标引入的依赖，有的是被移植、仿写、参照过的实现。但它们对项目的贡献是**同等**的：少了任何一个，这个项目都不会是现在的样子。

- 没有 **AndroidLiquidGlass** 与 **Kyant0/shapes**，就没有液态玻璃；
- 没有 **LiquidGlass-Cpp** 与 **Dear ImGui**，C++ 侧的光学实验无从谈起；
- 没有 **Pix-EzViewer**，P 站模块不会存在 —— 连它的 `client_secret`、PKCE 流程、小说解析和页面结构都来自那里；
- 没有 **PiPixiv**，评论区至今仍是「暂无评论」；
- 没有 **BRVAH** 与 **Ketch**（经 Pix-EzViewer 内建），P 站列表与图片下载管线要另起炉灶；
- 没有 **Operit**，AI 助手的工具确认与权限模型不会成型；
- 没有 **ImageCorrection** 的双路径思路，拍照扫描只是拍一张照片；
- 没有 **OpenCV / CameraX / PDFBox / Coil / Retrofit / OkHttp**，图像与网络两块直接归零；
- 没有 **Kotlin / Compose / AndroidX / Hilt / Gradle / KSP** 这一整套基础设施，这个项目连编译都过不去。

向以上每一位作者与贡献者，致以同等的、诚挚的感谢。

> 如果你是上表中某个项目的作者，发现署名、链接或许可标注有误，或希望调整致谢方式（包括移除），请开 Issue，我会尽快处理。

## 许可

本项目以 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

这意味着你可以自由使用、修改、再发布本项目，但**衍生作品也必须以 GPL-3.0 开源**，并保留原作者署名与许可声明。

其中 **Pix-EzViewer**（MIT）、**PiPixiv**（Apache-2.0）、**AndroidLiquidGlass** / **Kyant0/shapes** / **LiquidGlass-Cpp**（Apache-2.0）等源码级来源的许可，与本项目的 GPL-3.0 兼容；**jSerialComm**、**JNA**、**Operit** 的 LGPL 条款通过「不改源码、动态链接」的方式满足。

**Ketch** 与 **ImageCorrection** 的上游仓库目前未附带许可文件。本项目对二者的使用方式为：Ketch 经由 Pix-EzViewer 的源码子模块关系沿用其管线分层，ImageCorrection 采用其双路径算法思路并由本项目代码独立实现。若两位作者希望补充许可或调整署名，请开 Issue。

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
