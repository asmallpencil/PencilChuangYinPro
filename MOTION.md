# Motion language — 铅笔创印Pro

## Design system
Material 3（`material3` 被 `gradle/libs.versions.toml` 固定在 `1.3.1`）上的 MD3 Expressive
Spatial / Effect 二分，由项目自有 token 对象 `AppMotion` 承载，不依赖
`MaterialTheme.motionScheme`（该 API 不在此版本中）。

## Fallback
`AppMotion` 未覆盖的场景按 Material 3 Expressive 默认处理。预设只通过主题设置
`LocalMotionPreset` 下发，不从系统动效设置推导。

## Specs

### 空间型：位置、尺寸、缩放
- **Intent** — 位移与缩放带轻微惯性（可过冲），容器形态变化用慢档；不用于表达数据的长度或位置。
- **Binding** — `AppMotion.spatialSpec()` / `spatialFastSpec()` / `spatialSlowSpec()` / `bouncySpec()`
- **Tag** — `OBSERVED`

### 效果型：颜色与透明度
- **Intent** — 颜色、透明度、淡入淡出永不过冲，避免闪烁。
- **Binding** — `AppMotion.effectSpec()` / `effectFastSpec()`
- **Tag** — `OBSERVED`

### 列表与网格的增删、重排
- **Intent** — 新增让位、删除合拢、重排滑入，带惯性；条目靠稳定 ID 而不是下标识别。
- **Binding** — `LazyItemScope.animateItemMotion(motion)`、`LazyGridItemScope.animateGridItemMotion(motion)`
- **Tag** — `OBSERVED`

### 按压反馈
- **Intent** — 按下 90ms 跟手缩小，松开弹性回弹；减弱预设下不缩放。
- **Binding** — `Modifier.pressScale(interactionSource, enabled, pressedScale)`
- **Tag** — `OBSERVED`

### 页面进入与退出
- **Intent** — 进入轻微上浮淡入，退出下沉淡出；NavHost 默认 700ms 交叉淡化已被覆盖。
- **Binding** — `AppMotion.pageEnter()` / `pageExit()`
- **Tag** — `OBSERVED`

### 弹窗、展开区块、横向标签行
- **Intent** — 弹窗轻微放大淡入；区块竖向撑开、标签行横向撑开，收起时先收形再收场。
- **Binding** — `AppMotion.dialogEnter()` / `dialogExit()`、`expandEnter()` / `shrinkExit()`、
  `expandHorizontalEnter()` / `shrinkHorizontalExit()`
- **Tag** — `OBSERVED`

### 加载与骨架
- **Intent** — 加载不用转圈：七形状每 650ms 形变、4.65s 转一圈；长等待用 Shimmer 骨架。
- **Binding** — `AppLoadingIndicator`、`ContainedLoadingIndicator`、`AppSkeletonBox`
- **Tag** — `OBSERVED`

### 区块级错峰入场

- Intent — 仅区块级错峰，最多两段（0ms / 100ms），总窗口 ≤300ms；同一会话内只播一次，从二级页返回不得重播。
- Binding — `rememberSaveable` 标记 + `AppMotion.expandEnter()`
- Tag — DECIDED
- 已知例外：`SettingsGroup`（`feature/settings/SettingsScreen.kt`）仍用 `tween(240/320)` 承担延迟，因 spring 无 delay 参数。

### Material 主题动效方案
- **Intent** — 是否改用 `MaterialTheme.motionScheme` 未决。
- **Binding** — `MaterialTheme.motionScheme`
- **Tag** — `OPEN`

### 数据数值变化

- Intent — 长度、百分比、计数等「数值即信息」的变化不做过冲，避免读数在动画中被误读。
- Binding — `AppMotion.effectSpec()` / `effectFastSpec()`
- Tag — DECIDED
- 界定 — 本条只约束「数值即信息」——进度、百分比、计数、数据长度（如进度条、电量、下载比例）。结构性位移与尺寸变化（指示条移动、抽屉滑入、容器高度撑开、页面转场）不在此列，仍用 `spatialSpec()`，其过冲是预期手感。

## Exceptions

- `ConnectingPulseDot`（`feature/dashboard/HomeScreen.kt`）：连接中呼吸点用 `infiniteRepeatable` + `tween`，属循环指示，非转场，不受 token 约束。
- `PixivBrowseScreen.DRAWER_SLIDE_SPEC`：抽屉滑入用裸 `spring(DampingRatioNoBouncy, StiffnessMediumLow)`，未走 `AppMotion`；待抽屉交互重构时一并收敛。

## Frequency map
- **Never animate** — 打印进度、电量比例等以位置或长度表达数据的元素（只能用零过冲规格）。
- **Standard** — 首页瓦片、设置分组、列表/网格行、底部导航项。
- **Delight allowed** — 首次引导、连接成功、空态页。

## Gestures and haptics
底部导航条按下拖动直接 `dispatchRawDelta` 驱动 Pager，松手用 `animateScrollToPage` 吸附；
未发现自定义 `Animatable` 速度传递或触觉反馈约定。

## Reduced motion
应用内有 `MotionPreset.Reduced`（90–160ms tween、禁用 `pressScale`），由主题设置持久化。
系统「移除动画」会让 Compose 时钟归零，但自计时指示器会停在单一静态形状。

## Not in this codebase
- 未使用共享元素转场（`sharedElement` / `sharedBounds`）、`MaterialTheme.motionScheme`、自写 `withFrameNanos` 时间轴。
