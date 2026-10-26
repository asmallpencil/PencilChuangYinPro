/*
 * LiquidGlass 宿主：把 LiquidGlass-Cpp 库接入 Android（GLSurfaceView + ImGui GLES3）。
 *
 * 职责：
 *  - 提供 GLSurfaceView 使用的 GLES3 上下文 + ImGui（imgui_impl_opengl3，无平台后端，
 *    全部绘制走 ImGui::GetBackgroundDrawList()）。
 *  - 实现 lgx::CanvasBackend：图形原语转发到背景 DrawList；玻璃面板的捕获与回绘转发到
 *    库自身的 GL 管线（lgx::runPanelCapture / runPanelPaint），忠实还原液态玻璃效果。
 *  - 提供 TextMeasurer：接入 ImGui 字体度量（含系统 CJK 字体）。
 *  - 每帧驱动：beginFrame -> ImGui_ImplOpenGL3_NewFrame -> 触摸入队 -> NewFrame ->
 *    setSurface/setPointer -> 组件绘制 -> Render + RenderDrawData。
 *  - 暴露 JNI 接口供 Kotlin 的 LiquidGlassView(GLSurfaceView) 调用。
 *
 * 控件模型（本文件核心）：
 *  Compose 侧把界面上的控件（按钮/开关/滑动条/分段选择）通过 onGloballyPositioned 上报
 *  屏幕坐标，原生层维护「动态控件表」，每帧按注册的 box + 类型绘制对应的液态玻璃控件，
 *  并自行消费触摸（与导航栏一致）。因此除导航栏外，绝大多数控件都能被替换为液态玻璃。
 *
 * 坐标空间：setScale(density) 使 lgx::px(dp) = dp * density（物理像素）；
 * Surface.pixelScale = 1，ImGui DisplayFramebufferScale = (1,1)，
 * 因此「1 库单位 = 1 物理像素」，Kotlin 传入的 View 像素可直接作为库坐标。
 */
#include <GLES3/gl3.h>
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "ColorAdapter.h"
#include "GlassUI.h"
#include "imgui.h"
#include "imgui_impl_opengl3.h"

#define LOG_TAG "LiquidGlass"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ---- 运行状态 ----
bool g_ready = false;
bool g_initializedGL = false;

std::atomic<bool> g_controlsEnabled{false};
std::atomic<bool> g_navbarEnabled{false};
std::atomic<bool> g_light{false};

// 模态弹窗（窗口内覆盖层）是否要求「即使控件玻璃关闭也继续绘制容器玻璃面」。
// 成因：弹窗必须由 Compose 在**窗口内**绘制，再经 Modifier.nativeGlassSurface 注册为 Panel，
// 借原生玻璃面「镜面」透出（见 core:ui 的 AppDialogHost）。而弹窗打开时用户往往还没开启控件
// 玻璃（例如「开启液态玻璃」的三级警告弹窗），若原生层因此停绘，弹窗就只剩 Compose 实心底
// ——即用户反馈的「伪液态玻璃弹窗」。此开关只放行 Panel，不影响按钮/开关等控件。
std::atomic<bool> g_glassPanelsAlwaysOn{false};

// 液态玻璃动画开关与相位：开启后按钮/芯片的透镜厚度与色散随时间做正弦呼吸，
// 让玻璃面不再静止单调。相位仅由 GL 线程推进（见 renderFrame）。
std::atomic<bool> g_animate{true};
float g_animPhase = 0.0f;

// 呼吸动画幅度：透镜厚度 ±0.8dp、色散 +0~0.06，均为观感可辨但不喧宾夺主的量级。
constexpr float kAnimLensSwingDp = 0.8f;
constexpr float kAnimSpectralSwing = 0.06f;

float g_width = 0.0f;
float g_height = 0.0f;
float g_density = 2.0f;

lgx::Box g_currentClip(0.0f, 0.0f, 1.0f, 1.0f);

// ---- 导航栏 ----
// 顶级页数量（首页 / 创作 / P站）。导航栏图标位图、TabStrip painter/tag 均按此维度组织。
constexpr int kNavCount = 3;

lgx::TabStrip s_tabs;
bool s_tabsInited = false;
// 导航栏图标纹理（Compose 下发的应用内图标，白字+alpha，原生着色）。0 = 未设置，回退内置矢量图标。
unsigned int s_navIconTex[kNavCount] = {0u, 0u, 0u};

std::atomic<int> g_navIndex{0};
std::atomic<int> g_pendingNavTap{-1};

// ---- 全局液态玻璃样式（设置页可调，实时作用于所有原生控件） ----
struct GlassStyle {
    float blurDp = lgx::config::Layout::BUTTON_BLUR_DP;
    float lensHeightDp = lgx::config::Layout::BUTTON_LENS_HEIGHT_DP;
    float lensAmountDp = lgx::config::Layout::BUTTON_LENS_AMOUNT_DP;
    float spectral = 0.0f;
    float tintAlpha = 1.0f;      // 控件整体不透明度（alphaScale）
    bool shadowOn = true;
    bool glassOn = true;         // 是否启用玻璃材质（关闭则退化为纯色面）
    bool contourEdge = false;    // 轮廓高光边缘
};
GlassStyle g_style;

// ---- 容器玻璃面（Panel）专属参数 ----
// 与 g_style 分离：按钮/开关/导航栏等自绘控件必须共用同一份模糊取值，才能把屏幕
// 邻域「擦干净、不漏字」；而容器玻璃面盖在实时 Compose 内容之上，模糊只会让压在
// 下面的文字变糊，因此容器面板需要自己的模糊与采样外扩。
struct PanelStyleParams {
    float blurDp = 0.0f;      // 0 = 不模糊，底层 Compose 内容保持锐利
    float padDp = 24.0f;      // 采样外扩：尽量取到面板四周的屏幕邻域
    float fillAlpha = 0.9f;   // 采样缺失处的兜底不透明度（真实玻璃非透明）
};
PanelStyleParams g_panelStyle;

// ---- App 真实主题色（由 Kotlin 下发，避免硬编码绿/蓝） ----
// accent ：colorScheme.primary，作为 Toggle/Slider/TabStrip 的强调色。
// surface：colorScheme.surface，作为玻璃底色基调（同时决定库的亮/暗判定）。
std::atomic<unsigned int> g_accentColor{0u};
std::atomic<unsigned int> g_surfaceColor{0u};

// ---- 动态控件表 ----
enum class WidgetType { Button = 0, Toggle = 1, Slider = 2, Segmented = 3, Panel = 4 };

// Button 通道的四种外观（共用 PillButton，避免重复实现注册/命中/事件链路）：
//  Plain  ：常规玻璃按钮（主/次/描边按钮）
//  Chip   ：芯片，选中时以强调色着色（tinted）
//  Icon   ：纯图标按钮，方形框 → 胶囊即圆形，图标来自 Compose 下发的位图
//  Text   ：纯文字按钮，不绘制玻璃面，仅文字（保留按压态）
enum class ButtonVariant { Plain = 0, Chip = 1, Icon = 2, Text = 3 };

struct Widget {
    int id = 0;
    WidgetType type = WidgetType::Button;
    lgx::Box box;
    // 层级：值越大越靠上。绘制按 layer 升序、命中按 layer 降序，
    // 使覆盖层（如侧边栏抽屉）能压住导航栏/子导航等低层控件。
    int layer = 0;

    std::string label;          // Button 文本 / Segmented 的 tab 文本集合
    std::vector<std::string> tabs;
    int iconId = -1;            // Button 图标（见 iconPainterFor，库内置矢量图标）
    std::vector<int> tabIconIds; // Segmented 每个 tab 的图标
    float textSizeSp = lgx::config::Layout::BUTTON_TEXT_SP;
    ButtonVariant variant = ButtonVariant::Plain;
    bool selected = false;      // Chip 选中态
    unsigned int iconTex = 0u;  // Button 图标位图纹理（Compose 下发，白字+alpha，原生着色）

    std::unique_ptr<lgx::PillButton> button;
    std::unique_ptr<lgx::Toggle> toggle;
    std::unique_ptr<lgx::Slider> slider;
    std::unique_ptr<lgx::TabStrip> strip;

    bool on = false;            // Toggle
    float minV = 0.0f;          // Slider
    float maxV = 1.0f;
    float value = 0.5f;
    int activeIndex = 0;        // Segmented

    float cornerRadius = 0.0f;  // Panel 圆角半径（px；0 = 直角）
    int panelIndex = -1;        // Panel 提交的原生玻璃面板索引

    // 是否参与交互（命中 + 事件）。false = 仅绘制玻璃外观（draw-only）：
    // 用于「落在 Compose 滚动容器内的芯片」——触摸交给 Compose 的 LazyRow 处理滑动，
    // 原生侧只负责玻璃面外观，避免吞掉横向滑动手势。Panel 恒为不可交互。
    bool interactive = true;

    // 命中盒（绘制期计算）。**不要**用本次绘制期的 box 去命中测试：列表滚动时
    // 同一帧内先画的控件 box 已更新、后画的还是上帧坐标，会造成半屏控件点不动。
    // 因此命中只用「上一帧完整绘制结束」时定格的坐标。
    lgx::Box hitBox;
    float hitRadius = 0.0f;
    bool hitInteractive = true;
    bool hitValid = false;
};

std::map<int, std::unique_ptr<Widget>> g_widgets;

// 命中盒快照是否需要在帧末重建。
bool g_hitDirty = false;

// ---- 命中测试快照（GL 线程写 / UI 线程读，需加锁） ----
struct HitRect {
    int id;
    lgx::Box box;
    int layer = 0;
    bool interactive = true;   // 仅绘制玻璃外观的控件不参与命中，但仍作为遮挡体发布
};
std::mutex g_hitMutex;
std::vector<HitRect> g_hitRects;

// ---- 当前绘制中的控件层级 / 交互性（仅 GL 线程访问） ----
// onProbe 由库在控件内部绘制时回调，需知道「正在绘制哪个控件」才能做遮挡判定：
// 若该控件在 pointer 位置被更高 layer 的控件压住，则抑制其指针探针，避免穿透激活。
int g_drawLayer = 0;
bool g_drawInteractive = true;

// ---- 控件事件队列（GL 线程内生产与消费） ----
std::mutex g_eventMutex;
std::deque<long long> g_widgetEvents;

// ---- 触摸事件队列（UI 线程写入 / GL 线程消费） ----
struct TouchEvent {
    int action;   // 0=DOWN 1=UP 2=MOVE
    float x;
    float y;
};
std::mutex g_touchMutex;
std::deque<TouchEvent> g_touchQueue;
std::atomic<bool> g_touchPending{false};

// ---- 帧计时 / 自刷新保护 ----
float g_lastFrame = 0.0f;
int g_autoFrames = 0;

// ---- backdrop（Kotlin PixelCopy 抓取的窗口内容） ----
unsigned int g_backdropTex = 0u;
std::vector<unsigned char> g_backdropScratch;

// ---- 字体 ----
ImFont* g_font = nullptr;
ImVector<ImWchar> g_glyphRanges;   // 必须长期存活（AddFontFromFileTTF 保存指针）

float nowSec() {
    using namespace std::chrono;
    return (float)duration_cast<duration<float>>(steady_clock::now().time_since_epoch()).count();
}

lgx::Box navbarBox() {
    const float margin = lgx::px(12.0f);
    const float barH = lgx::px(64.0f);
    const float gap = lgx::px(16.0f);
    return lgx::Box(margin, g_height - gap - barH, g_width - margin, g_height - gap);
}

lgx::TextSize measureText(const char* content, float size) {
    lgx::TextSize span;
    if (content == nullptr) return span;
    ImFont* font = ImGui::GetFont();
    if (font == nullptr) return span;
    const ImVec2 measured = font->CalcTextSizeA(size, 1.0e6f, 0.0f, content);
    span.width = measured.x;
    span.height = measured.y;
    return span;
}

// 按可用换行宽度测量文本：与 measureText 同源，但把 wrap_width 交给字体排版，
// 从而得到「按容器宽度折行后的真实高度」。wrapWidth <= 0 时退化为单行测量。
lgx::TextSize measureTextWrapped(const char* content, float size, float wrapWidth) {
    lgx::TextSize span;
    if (content == nullptr) return span;
    ImFont* font = ImGui::GetFont();
    if (font == nullptr) return span;
    const ImVec2 measured =
        font->CalcTextSizeA(size, 1.0e6f, wrapWidth > 0.0f ? wrapWidth : 0.0f, content);
    span.width = measured.x;
    span.height = measured.y;
    return span;
}

// ---- 图标 id → 库内置图标（-1 表示无图标） ----
lgx::IconPainter iconPainterFor(int id) {
    switch (id) {
        case 0: return lgx::iconHome;
        case 1: return lgx::iconSearch;
        case 2: return lgx::iconMessage;
        case 3: return lgx::iconProfile;
        case 4: return lgx::iconHeart;
        case 5: return lgx::iconStar;
        case 6: return lgx::iconBell;
        case 7: return lgx::iconGear;
        case 8: return lgx::iconClose;
        case 9: return lgx::iconCheck;
        case 10: return lgx::iconChevron;
        default: return nullptr;
    }
}

// ---- 图标主题色（Kotlin 下发：亮色模式为深色、暗色模式为浅色） ----
std::atomic<unsigned int> g_iconColor{0u};

// ---- Compose 下发的图标位图（白字 + alpha），绘制时按控件文字色着色 ----
// tag 指向 Widget::iconTex，因此纹理 id 变化会即时生效，无需重建 painter。
void texIconPainter(lgx::Canvas& canvas, const lgx::Point& center, float size,
                    unsigned int color, void* tag) {
    if (tag == nullptr) return;
    const unsigned int tex = *static_cast<const unsigned int*>(tag);
    if (tex == 0u || size <= 0.0f) return;
    // 位图是「白字 + alpha」：以传入色直接着色即可。纹理 RGB=白 × 传入色 = 传入色，
    // 纹理 alpha 与传入色 alpha 相乘，图标因此跟随主题前景色（亮色模式为深色）。
    const float half = size * 0.5f;
    canvas.image(tex, lgx::Box(center.x - half, center.y - half, center.x + half, center.y + half),
                 lgx::Point(0.0f, 0.0f), lgx::Point(1.0f, 1.0f), color);
}

// ---- Button 变体 → PillButton 外观 ----
void applyButtonVariant(lgx::PillButton& button, const Widget& w) {
    button.glassOn = true;
    button.textOn = true;
    button.tinted = false;
    button.icon = iconPainterFor(w.iconId);
    button.iconTag = nullptr;

    // 位图图标（Compose 侧离屏渲染的「白字 + alpha」）优先于库内置矢量图标。
    // PillButton::draw 在 icon != nullptr 时会把图标与 label 并排居中绘制（图标在左、
    // 文字在右），这正是「带前置图标的 M3 按钮」的原生等价实现 —— 因此 Plain/Chip/Text
    // 变体只要拿到位图就一并挂上，无需再回退到 Compose 仿玻璃。
    // iconTag 指向 Widget::iconTex，纹理 id 变化会即时生效，无需重建 painter。
    if (w.iconTex != 0u) {
        button.icon = texIconPainter;
        button.iconTag = const_cast<unsigned int*>(&w.iconTex);
    }

    switch (w.variant) {
        case ButtonVariant::Chip:
            // 选中：强调色实心（文字自动转白）；未选中：常规玻璃胶囊。
            if (w.selected) {
                const unsigned int accent = g_accentColor.load();
                if (accent != 0u) {
                    button.tinted = true;
                    button.tint = lgx::rgbaOf(accent);
                }
            }
            break;
        case ButtonVariant::Icon: {
            // 纯图标：方形占位框经 capsule 圆角即为圆形；图标由位图绘制（见上）。
            button.label = nullptr;
            // 图标随主题前景色（亮色模式深色 / 暗色模式浅色），不随选中态漂白。
            const unsigned int iconColor = g_iconColor.load();
            if (iconColor != 0u) button.textColor = iconColor;
            break;
        }
        case ButtonVariant::Text:
            // 纯文字按钮：不画玻璃面，只保留文字与按压反馈。
            button.glassOn = false;
            break;
        case ButtonVariant::Plain:
        default:
            break;
    }
}

// ---- 事件打包：高 32 位 id / 次 8 位 kind / 低 24 位 value ----
long long packEvent(int id, int kind, int value24) {
    return ((long long)id << 32) | ((long long)(kind & 0xFF) << 24) |
           (long long)(value24 & 0xFFFFFF);
}

void enqueueEvent(int id, int kind, int value24) {
    std::lock_guard<std::mutex> lock(g_eventMutex);
    if (g_widgetEvents.size() > 256) g_widgetEvents.pop_front();
    g_widgetEvents.push_back(packEvent(id, kind, value24));
}

void publishHitRects() {
    std::vector<HitRect> rects;
    rects.reserve(g_widgets.size());
    for (const auto& entry : g_widgets) {
        const Widget& w = *entry.second;
        // 只发布「上一帧完整绘制」定格下来的命中盒；尚未绘制过的控件不参与命中，
        // 以免把半成品坐标暴露给触摸线程。
        if (!w.hitValid) continue;
        rects.push_back(HitRect{entry.first, w.hitBox, entry.second->layer, w.hitInteractive});
    }
    std::lock_guard<std::mutex> lock(g_hitMutex);
    g_hitRects.swap(rects);
}

// 绘制期记录命中盒，帧末统一发布（见 endWidgetFrame）。
void collectHitRect(const Widget& w, const lgx::Box& box, float radius) {
    // Panel 恒为不可交互（只渲染玻璃底），其余控件遵循其 interactive 标记。
    const bool interactive = w.interactive && w.type != WidgetType::Panel;
    if (w.hitValid && w.hitBox.left == box.left && w.hitBox.top == box.top &&
        w.hitBox.right == box.right && w.hitBox.bottom == box.bottom &&
        w.hitRadius == radius && w.hitInteractive == interactive) {
        return;
    }
    Widget& mutableWidget = const_cast<Widget&>(w);
    mutableWidget.hitBox = box;
    mutableWidget.hitRadius = radius;
    mutableWidget.hitInteractive = interactive;
    mutableWidget.hitValid = true;
    g_hitDirty = true;
}

// 遮挡判定：pointer 位置是否存在「严格更高 layer」的已绘制控件。
// 用于抑制被覆盖控件的指针探针，避免抽屉下的芯片/导航栏仍被激活（触控穿透）。
// 只在 GL 线程调用（读 g_hitRects 快照，加锁）。
bool pointerOccluded(float x, float y, int layer) {
    std::lock_guard<std::mutex> lock(g_hitMutex);
    for (const HitRect& rect : g_hitRects) {
        if (rect.layer > layer && rect.box.holds(x, y)) return true;
    }
    return false;
}

// 帧末调用：命中盒有变化才重建快照（避免每帧加锁 + 重建 vector）。
void endWidgetFrame() {
    if (!g_hitDirty) return;
    g_hitDirty = false;
    publishHitRects();
}

// ---- 原生液态玻璃模态弹窗（lgx::Modal 自绘） ----
// 真液态玻璃弹窗由库整块自绘：全屏遮罩 + 玻璃卡片 + 标题/正文 + 玻璃按钮，并提供两种弹出动画：
//   open()            → 从屏幕中心长出；
//   openFrom(origin)  → 从触发控件的位置长出（源点吸附）。
// 状态只在 GL 线程读写（Kotlin 经 GLSurfaceView.queueEvent 下发）。
std::unique_ptr<lgx::Modal> s_dialog;
std::string s_dialogTitle;
std::string s_dialogMessage;
std::string s_dialogCancel;
std::string s_dialogConfirm;

// 弹窗这一帧是否正在绘制遮罩。Modal::draw 的遮罩走 fillRound(整屏, radius=0)，
// 若照旧进 ImGui draw list，会在帧末回放时盖住「立即 GL 绘制」的玻璃面板（玻璃被压暗、
// 呈现为伪玻璃）。因此把它抢到立即通道（见 GlassBackend::onFillRect / paintScrimImmediate）。
bool g_dialogScrimActive = false;
bool g_dialogScrimDrawn = false;
// 关闭动画播完那一帧写入；Kotlin 用 nativePollDialogResult 取走（未取时为 0）。
std::atomic<int> g_dialogResultPending{0};

std::string jstringToUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars != nullptr ? std::string(chars) : std::string();
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return out;
}

// ---- 遮罩的立即 GL 绘制：全屏三角形带 + 纯色着色器 ----
// 只负责「遮罩在下、玻璃在上」的层序；颜色语义与库一致（0xAARRGGBB）。
GLuint g_scrimProgram = 0;
GLint g_scrimColorLoc = -1;
GLuint g_scrimVao = 0;
GLuint g_scrimVbo = 0;

bool ensureScrimProgram() {
    if (g_scrimProgram == 0) {
        static const char* kVertexShader =
            "#version 300 es\n"
            "layout(location = 0) in vec2 aPos;\n"
            "void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n";
        static const char* kFragmentShader =
            "#version 300 es\n"
            "precision mediump float;\n"
            "uniform vec4 uColor;\n"
            "out vec4 fragColor;\n"
            "void main() { fragColor = uColor; }\n";
        GLuint vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, 1, &kVertexShader, nullptr);
        glCompileShader(vs);
        GLint ok = 0;
        glGetShaderiv(vs, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            LOGE("scrim: vertex shader compile failed");
            glDeleteShader(vs);
            return false;
        }
        GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, 1, &kFragmentShader, nullptr);
        glCompileShader(fs);
        glGetShaderiv(fs, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            LOGE("scrim: fragment shader compile failed");
            glDeleteShader(vs);
            glDeleteShader(fs);
            return false;
        }
        const GLuint program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glGetProgramiv(program, GL_LINK_STATUS, &ok);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (!ok) {
            LOGE("scrim: program link failed");
            glDeleteProgram(program);
            return false;
        }
        g_scrimProgram = program;
        g_scrimColorLoc = glGetUniformLocation(program, "uColor");
    }
    if (g_scrimVao == 0) {
        static const float kQuad[8] = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f};
        glGenVertexArrays(1, &g_scrimVao);
        glGenBuffers(1, &g_scrimVbo);
        glBindVertexArray(g_scrimVao);
        glBindBuffer(GL_ARRAY_BUFFER, g_scrimVbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(kQuad), kQuad, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), (const void*)0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    return true;
}

void paintScrimImmediate(unsigned int color) {
    GLint prevProgram = 0;
    GLint prevVao = 0;
    glGetIntegerv(GL_CURRENT_PROGRAM, &prevProgram);
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &prevVao);
    if (!ensureScrimProgram()) return;
    const float a = (float)((color >> 24) & 0xFFu) / 255.0f;
    const float r = (float)((color >> 16) & 0xFFu) / 255.0f;
    const float g = (float)((color >> 8) & 0xFFu) / 255.0f;
    const float b = (float)(color & 0xFFu) / 255.0f;
    glUseProgram(g_scrimProgram);
    glUniform4f(g_scrimColorLoc, r, g, b, a);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_SCISSOR_TEST);
    glEnable(GL_BLEND);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glBindVertexArray(g_scrimVao);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray((GLuint)prevVao);
    glUseProgram((GLuint)prevProgram);
}

} // namespace

lgx::TextSize lgxMeasure(const char* content, float size) { return measureText(content, size); }

lgx::TextSize lgxMeasureWrapped(const char* content, float size, float wrapWidth) {
    return measureTextWrapped(content, size, wrapWidth);
}

// ---- CanvasBackend：把库原语转发到 ImGui 背景 DrawList ----
namespace {

class GlassBackend final : public lgx::CanvasBackend {
public:
    static ImDrawList* list() { return ImGui::GetBackgroundDrawList(); }
    static ImU32 col(unsigned int color) { return (ImU32)color; }

    lgx::Box clipRect() override { return g_currentClip; }

    void onFillRect(const lgx::Box& box, float radius, unsigned int color) override {
        // 模态弹窗的全屏遮罩：必须「立即」绘制。库把遮罩与文字一起写进 draw list，而玻璃卡片
        // 与按钮是立即 GL；若遮罩也延到帧末回放，就会盖在玻璃之上把玻璃压暗（伪玻璃观感成因）。
        // 判据：弹窗绘制中 + 首个「整屏直角」填充（Dialog.cpp 的 fillRound(full, 0.0f, scrim)）。
        if (g_dialogScrimActive && !g_dialogScrimDrawn && radius <= 0.5f &&
            box.left <= 0.5f && box.top <= 0.5f &&
            box.right >= g_width - 0.5f && box.bottom >= g_height - 0.5f) {
            g_dialogScrimDrawn = true;
            paintScrimImmediate(color);
            return;
        }
        list()->AddRectFilled(ImVec2(box.left, box.top), ImVec2(box.right, box.bottom),
                              col(color), radius);
    }
    void onStrokeRect(const lgx::Box& box, float radius, float thickness,
                      unsigned int color) override {
        list()->AddRect(ImVec2(box.left, box.top), ImVec2(box.right, box.bottom), col(color),
                        radius, ImDrawFlags_None, thickness);
    }
    void onFillPoly(const lgx::Point* points, int count, unsigned int color) override {
        ImDrawList* dl = list();
        dl->PathClear();
        for (int i = 0; i < count; ++i) dl->PathLineTo(ImVec2(points[i].x, points[i].y));
        dl->PathFillConvex(col(color));
    }
    void onFillConcave(const lgx::Point* points, int count, unsigned int color) override {
        ImDrawList* dl = list();
        dl->PathClear();
        for (int i = 0; i < count; ++i) dl->PathLineTo(ImVec2(points[i].x, points[i].y));
        dl->PathFillConvex(col(color));
    }
    void onStrokePoly(const lgx::Point* points, int count, float thickness,
                      unsigned int color) override {
        ImDrawList* dl = list();
        dl->PathClear();
        for (int i = 0; i < count; ++i) dl->PathLineTo(ImVec2(points[i].x, points[i].y));
        dl->PathStroke(col(color), ImDrawFlags_Closed, thickness);
    }
    void onLine(const lgx::Point& from, const lgx::Point& to, unsigned int color,
                float thickness) override {
        list()->AddLine(ImVec2(from.x, from.y), ImVec2(to.x, to.y), col(color), thickness);
    }
    void onFillCircle(const lgx::Point& center, float radius, unsigned int color,
                      int segments) override {
        list()->AddCircleFilled(ImVec2(center.x, center.y), radius, col(color), segments);
    }
    void onFillTriangle(const lgx::Point& a, const lgx::Point& b, const lgx::Point& c,
                        unsigned int color) override {
        list()->AddTriangleFilled(ImVec2(a.x, a.y), ImVec2(b.x, b.y), ImVec2(c.x, c.y),
                                  col(color));
    }
    void onFillArc(const lgx::Point& center, float radius, float fromRadians,
                   float toRadians, unsigned int color, int segments) override {
        ImDrawList* dl = list();
        dl->PathClear();
        if (segments < 3) segments = 24;
        for (int i = 0; i <= segments; ++i) {
            const float a = fromRadians + (toRadians - fromRadians) * ((float)i / segments);
            dl->PathLineTo(ImVec2(center.x + cosf(a) * radius, center.y + sinf(a) * radius));
        }
        dl->PathFillConvex(col(color));
    }
    void onText(const lgx::Point& position, float size, unsigned int color,
                const char* content, float wrapWidth) override {
        if (content == nullptr) return;
        ImFont* font = ImGui::GetFont();
        if (font == nullptr) return;
        if (wrapWidth > 0.0f) {
            list()->AddText(font, size, ImVec2(position.x, position.y), col(color), content,
                            nullptr, wrapWidth);
        } else {
            list()->AddText(font, size, ImVec2(position.x, position.y), col(color), content);
        }
    }
    void onImage(unsigned int texture, const lgx::Box& box, const lgx::Point& uvMin,
                 const lgx::Point& uvMax, unsigned int color) override {
        if (texture == 0u) return;
        list()->AddImageQuad(
            (ImTextureID)(intptr_t)texture,
            ImVec2(box.left, box.top), ImVec2(box.right, box.top),
            ImVec2(box.right, box.bottom), ImVec2(box.left, box.bottom),
            ImVec2(uvMin.x, uvMin.y), ImVec2(uvMax.x, uvMin.y),
            ImVec2(uvMax.x, uvMax.y), ImVec2(uvMin.x, uvMax.y), col(color));
    }
    void onPushClip(const lgx::Box& box) override {
        g_currentClip = box;
        list()->PushClipRect(ImVec2(box.left, box.top), ImVec2(box.right, box.bottom), true);
    }
    void onPopClip() override {
        list()->PopClipRect();
        g_currentClip = lgx::Box(0.0f, 0.0f, g_width, g_height);
    }
    void onPanelCapture(int index) override {
        lgx::runPanelCapture(index, g_currentClip);
    }
    void onPanelPaint(int index) override {
        lgx::runPanelPaint(index, g_currentClip);
    }
    lgx::PointerProbe onProbe(const lgx::Box& box, const void* owner, bool interactive) override {
        (void)owner;
        lgx::PointerProbe probe;
        // 库的指针模型是纯几何命中，不做遮挡处理。此处统一收敛：
        // 1) 非交互控件（draw-only）不产生任何探针；
        // 2) 被更高 layer 控件压住时不产生探针，避免穿透激活。
        if (!interactive || !g_drawInteractive) return probe;
        ImGuiIO& io = ImGui::GetIO();
        if (pointerOccluded(io.MousePos.x, io.MousePos.y, g_drawLayer)) return probe;
        const bool inside = box.holds(io.MousePos.x, io.MousePos.y);
        probe.held = inside && io.MouseDown[0];
        probe.activated = inside && io.MouseClicked[0];
        probe.clicked = inside && io.MouseClicked[0];
        return probe;
    }
    void onBlockPointer(const lgx::Box& box) override { (void)box; }
    void onClearPointerBlock() override {}
};

GlassBackend g_backend;

void applyThemeColors(bool light) {
    lgx::GlassColors colors;
    const unsigned int surface = g_surfaceColor.load();
    if (surface != 0u) {
        // Kotlin 已下发真实主题色：以 surface 作为玻璃底色基调，
        // 由它向黑/白极点偏移出一个 container 作为 secondary，供库内部调和色阶使用。
        const unsigned int opaque = (surface >> 24) == 0u ? (surface | 0xFF000000u) : surface;
        colors.primary = lgx::rgbaOf(opaque);
        const unsigned int pole = light ? 0xFF000000u : 0xFFFFFFFFu;
        colors.secondary = lgx::rgbaOf(lgx::mixRgb(opaque, pole, 0.12f));
    } else if (light) {
        colors.primary = lgx::Rgba(0.97f, 0.97f, 0.99f, 1.0f);
        colors.secondary = lgx::Rgba(0.89f, 0.91f, 0.95f, 1.0f);
    } else {
        colors.primary = lgx::Rgba(0.06f, 0.07f, 0.10f, 1.0f);
        colors.secondary = lgx::Rgba(0.12f, 0.13f, 0.17f, 1.0f);
    }
    lgx::setGlassColors(colors, false);
    // 未显式下发图标前景色时，跟随库的自适应结果（亮底深色 / 暗底浅色）。
    if (g_iconColor.load() == 0u) {
        g_iconColor.store(0xFF000000u | lgx::detail::tintTextRgb());
    }
}

// ---- 系统 CJK 字体（含常用简体字，供任意控件文案使用） ----
bool loadFont() {
    ImGuiIO& io = ImGui::GetIO();
    static const char* const kCandidates[] = {
        "/system/fonts/NotoSansSC-Regular.otf",
        "/system/fonts/NotoSansSC-Regular.ttf",
        "/system/fonts/NotoSansCJKsc-Regular.otf",
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansCJK-VF.otf.ttc",
        "/system/fonts/DroidSansFallbackFull.ttf",
        "/system/fonts/DroidSansFallback.ttf",
    };
    const char* path = nullptr;
    for (const char* candidate : kCandidates) {
        FILE* probe = fopen(candidate, "rb");
        if (probe != nullptr) {
            fclose(probe);
            path = candidate;
            break;
        }
    }
    if (path == nullptr) {
        LOGE("no CJK font found in /system/fonts");
        return false;
    }

    ImFontGlyphRangesBuilder builder;
    builder.AddRanges(io.Fonts->GetGlyphRangesDefault());
    builder.AddRanges(io.Fonts->GetGlyphRangesChineseSimplifiedCommon());
    builder.BuildRanges(&g_glyphRanges);

    ImFontConfig cfg;
    cfg.OversampleH = 1;
    cfg.OversampleV = 1;
    cfg.PixelSnapH = true;
    // base 40px：兼顾文字清晰度与图集体积（常用简体字约 2500 个）。
    g_font = io.Fonts->AddFontFromFileTTF(path, 40.0f, &cfg, g_glyphRanges.Data);
    if (g_font == nullptr) {
        LOGE("AddFontFromFileTTF failed: %s", path);
        return false;
    }
    io.Fonts->Build();
    io.FontDefault = g_font;
    LOGI("CJK font loaded: %s", path);
    return true;
}

bool glInit() {
    if (g_initializedGL) return true;
    if (ImGui::GetCurrentContext() == nullptr) ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.ConfigFlags &= ~ImGuiConfigFlags_NavEnableKeyboard;
    io.IniFilename = nullptr;
    if (!ImGui_ImplOpenGL3_Init(nullptr)) {
        LOGE("ImGui_ImplOpenGL3_Init failed");
        return false;
    }
    if (!loadFont()) LOGE("falling back to default ImGui font (no CJK)");
    g_lastFrame = nowSec();
    g_initializedGL = true;
    return true;
}

bool lgStart() {
    if (g_ready) return true;
    lgx::attachCanvasBackend(&g_backend);
    lgx::setScale(g_density);
    lgx::setBackdropEnabled(false);   // 纹理绑定后再开启
    lgx::setTextMeasurer(lgxMeasure);
    // 供弹窗等需要「按宽度预排」的场景测量多行高度（见 lg/Dialog.cpp 自适应高度）。
    lgx::setWrappedTextMeasurer(lgxMeasureWrapped);
    if (!lgx::startup()) {
        LOGE("lgx::startup failed (shaders)");
        return false;
    }
    applyThemeColors(g_light.load());
    g_ready = true;
    return true;
}

void lgShutdown() {
    if (!g_ready) return;
    lgx::shutdown();
    g_ready = false;
}

void initNavbar() {
    if (s_tabsInited) return;
    s_tabs.resetTabs();
    s_tabs.appendTab(u8"首页");
    s_tabs.appendTab(u8"创作");
    s_tabs.appendTab(u8"P站");
    // 优先使用 Compose 下发的应用内图标（位图纹理），未下发时回退到库内置矢量图标。
    s_tabs.painters[0] = s_navIconTex[0] ? texIconPainter : lgx::iconHome;
    s_tabs.tags[0] = s_navIconTex[0] ? &s_navIconTex[0] : nullptr;
    s_tabs.painters[1] = s_navIconTex[1] ? texIconPainter : lgx::iconHeart;
    s_tabs.tags[1] = s_navIconTex[1] ? &s_navIconTex[1] : nullptr;
    s_tabs.painters[2] = s_navIconTex[2] ? texIconPainter : lgx::iconSearch;
    s_tabs.tags[2] = s_navIconTex[2] ? &s_navIconTex[2] : nullptr;
    s_tabs.select(g_navIndex.load(), false);
    s_tabsInited = true;
}

// ---- 样式下发（每帧刷新，保证设置页调参实时生效） ----
/**
 * 把全局样式应用到自绘玻璃控件。
 *
 * @param phase 该控件当前的呼吸相位（弧度）。调用方按控件 id 施加相位偏移，
 *        使同屏多个控件不同频——否则整屏玻璃会像同一个「心跳」一起明灭，
 *        观感僵硬且暴露了「所有控件共用一条动画曲线」这一实现细节。
 */
void applyStyle(lgx::PillButton& button, float phase) {
    float lensAmountDp = g_style.lensAmountDp;
    float spectral = g_style.spectral;
    // 呼吸动画：以正弦调制透镜厚度与色散，让玻璃面呈现缓慢的「液态流动」质感。
    // 只作用于按钮/芯片等自绘控件；容器面板（Panel）不参与，以保底层文字锐度。
    if (g_animate.load()) {
        const float breath = sinf(phase);
        lensAmountDp += breath * kAnimLensSwingDp;
        spectral += (0.5f + 0.5f * breath) * kAnimSpectralSwing;
    }
    button.blurDp = g_style.blurDp;
    button.lensHeightDp = g_style.lensHeightDp;
    button.lensAmountDp = lensAmountDp;
    button.spectral = spectral;
    button.alphaScale = g_style.tintAlpha;
    button.shadowOn = g_style.shadowOn;
    button.glassOn = g_style.glassOn;
    button.contourEdge = g_style.contourEdge;
}

/** 逐控件呼吸相位偏移：把 id 均匀铺开到一整个周期（12 档 ≈ 30°）。 */
constexpr int kAnimPhaseSlots = 12;
constexpr float kAnimPhaseStep = 6.2831853f / (float)kAnimPhaseSlots;

inline float widgetPhase(int id) {
    // id 恒为正（从 1 递增），取模即可；保留绝对值兜底，避免未来出现负数 id 时相位反向。
    const int slot = (id < 0 ? -id : id) % kAnimPhaseSlots;
    return g_animPhase + (float)slot * kAnimPhaseStep;
}

// ---- 容器类玻璃面样式（Card / Drawer / TextField / 侧边栏 / 底栏等容器共用） ----
// 材质取自库内弹窗卡片（lg/Dialog.cpp 的 `Modal::draw`）：同一套「透镜 + 色散 + 渐变描边 +
// 投影 + 叠加面色」组合，使全部容器玻璃面与弹窗卡片观感一致（需求：用弹窗玻璃背景统一替换）。
// 与弹窗的差别仅在「静态化」：弹窗的透镜/色散/描边强度由弹出动画曲线驱动，
// 容器面板没有动画阶段，故取曲线到达态（`_TO`）的强度常量。
lgx::PanelStyle makePanelStyle(const Widget& w) {
    lgx::PanelStyle style;
    // 圆角按「短边一半」截断：滚动/翻页动画中控件被压扁时，若圆角保持原值会超过短边一半，
    // 玻璃面会被挤成胶囊/圆形（用户反馈的「挤压形变 / 卡成圆形」），这里强制夹取。
    const float shortSide = w.box.width() < w.box.height() ? w.box.width() : w.box.height();
    const float maxRadius = shortSide > 0.0f ? shortSide * 0.5f : 0.0f;
    float radius = w.cornerRadius;
    if (radius > maxRadius) radius = maxRadius;
    // cornerRadius <= 0 表示「直角矩形」（全宽顶栏/底栏等；Kotlin 侧 NativeGlassSurface
    // 对非 RoundedCornerShape 一律下发 0）。早期实现回退胶囊，于是全宽窄栏被画成左右半圆
    // 的「体育场形」，栏端产生强烈透镜折射与色散——即用户反馈的「神秘扭曲态」。
    // 此处只在 radius > 0 时倒角，0 按直角矩形处理，边缘扭曲随之消失。
    style.corners = radius > 0.0f ? lgx::Corners::rounded(radius) : lgx::Corners(0.0f);
    // 容器面板盖在实时 Compose 内容之上：其输出 = 采样到的 backdrop（窗口内容，含其下的
    // 文字/图标）经色调映射后绘制。因此面板的模糊必须可关（默认 0），否则压在下面的文字
    // 会被抹糊。真正的锐度来源是 Kotlin 侧 backdrop 的全分辨率采集（见 LiquidGlassView），
    // 而非此处的 blurPx。此处模糊量走 g_panelStyle.blurDp，由设置页统一控制。
    style.optics.blurPx = lgx::px(g_panelStyle.blurDp);
    // 透镜/色散：只恢复**边缘窄带**折射（PANEL_LENS_*），不做整面透镜。
    // 历史：早期把面板透镜整体置零，原因是整面透镜会在边缘处把压在面板下的文字拉变形；
    // 但置零后容器退化为一块无厚度的半透明色片，与带折射的按钮/弹窗观感割裂
    //（用户反馈的「透视混乱 / 整体表现不佳」）。着色器的 lensT 随离边距离衰减，
    // 带宽之外恒为平坦采样，故这里用「窄带 + 小幅」同时拿到厚度感与文字锐利度。
    // 注意：采样源是 PixelCopy 的窗口快照，无法区分文字与背景；带宽内若恰好压着正文，
    // 仍会有轻微拉伸，因此带宽必须小于面板内边距（PANEL_PAD_DP）。
    style.optics.colorBoost = false;
    style.optics.depthAmount = 1.0f;
    style.optics.lensHeightPx = lgx::px(lgx::config::Layout::PANEL_LENS_HEIGHT_DP);
    style.optics.lensAmountPx = lgx::px(lgx::config::Layout::PANEL_LENS_AMOUNT_DP);
    style.optics.spectral = lgx::config::Layout::PANEL_SPECTRAL;
    // 渐变描边：与弹窗卡片同源（弹窗在 flare≈0 时即此形态）。
    style.edge = lgx::EdgeLight(
        lgx::EdgeMode::Gradient,
        lgx::rgba(lgx::config::Layout::DIALOG_EDGE_ALPHA,
                  lgx::detail::tintEdgeArgb(lgx::config::Render::EDGE_TINT_DIALOG)) & 0x00FFFFFFu,
        lgx::config::Render::EDGE_ANGLE_DEFAULT,
        lgx::config::Render::EDGE_FALLOFF_DEFAULT,
        lgx::config::Render::EDGE_WIDTH_MIN,
        lgx::config::Render::EDGE_SOFTNESS_MIN,
        lgx::config::Layout::DIALOG_EDGE_ALPHA);
    style.edgeOn = true;
    // 投影：同样取弹窗卡片的到达态。
    style.shadowOn = g_style.shadowOn;
    if (style.shadowOn) {
        style.shadow = lgx::DropShadow(
            lgx::config::Layout::DIALOG_SHADOW_DP, 0.0f, lgx::config::Layout::DIALOG_SHADOW_DP / 6.0f,
            lgx::rgba(lgx::config::Layout::DIALOG_SHADOW_ALPHA,
                      lgx::config::Render::SHADOW_TINT_DIALOG),
            1.0f);
    }
    // 面色：与弹窗卡片同一套 sheet 色，但容器使用更低的 PANEL_SHEET_ALPHA_SCALE——
    // 面板主体必须透出下方实时页面（透明玻璃），玻璃边界由 edge/lens/shadow 刻画；
    // 若沿用弹窗的 0.72 厚面色，容器会变成一块乳白不透明底板（用户反馈的「MD3 底没去掉」）。
    style.surface = lgx::detail::tintSheet();
    style.surface.a = lgx::detail::tintSheetAlpha() * lgx::config::Layout::PANEL_SHEET_ALPHA_SCALE;
    style.surfaceMode = lgx::SurfaceMode::Overlay;
    style.backdropOn = g_style.glassOn;
    style.alpha = 1.0f;
    // 面色下限仍走设置页的填充不透明度：sheet 透明度过低（暗色）时保证可读性。
    style.surfaceAlphaFloor = g_panelStyle.fillAlpha;
    // 基调色取自 App 主题表面色，保证亮/暗主题下玻璃面色一致。
    style.fallback = lgx::rgbaOf(g_surfaceColor.load() != 0u ? g_surfaceColor.load()
                                                             : (g_light.load() ? 0xFFFFFFFFu
                                                                               : 0xFF000000u));
    return style;
}

// ---- 本帧待绘制的玻璃面板（帧内登记，分两批执行） ----
// 库内绘制直接发 GL（glUseProgram + glDrawArrays），不碰 ImGui draw list，因此其**执行时机**
// 决定了它在最终画面里的前后关系。宿主把所有 ImGui 控件（芯片/导航栏/按钮…）统一在
// ImGui_ImplOpenGL3_RenderDrawData 里一次flush，于是面板只能落在两个位置：
//   • below：ImGui 渲染**之前**执行 → 面板在全部 ImGui 控件之下（Card / 输入框 / 按钮底等
//     常规容器玻璃，必须被其上文字图标盖住）；
//   • above：ImGui 渲染**之后**执行 → 面板在全部 ImGui 控件之上（抽屉 / 弹层等
//     OVERLAY 及更高层，必须压住其下的芯片与导航栏）。
// 早期版本只有 below，导致抽屉被 layer=0 的芯片/导航栏视觉反盖（触控按 layer 判定却
// 是正确的，于是出现「看得见却按不中 / 按得中却看不见」的层级错乱）。此处按控件 layer 分流。
constexpr int kPanelPendingLimit = 64;
/** 面板进入 above 队列的 layer 门槛（对齐 LiquidGlassLayer.OVERLAY）。 */
constexpr int kPanelAboveLayerThreshold = 100;

/**
 * 待绘制的玻璃面板条目。
 *
 * 面板绘制是「替换」而非「叠加」：同帧内后画的面板会用自己那份「模糊后的窗口内容 + 面色」
 * 覆盖掉重叠区域里先画的一切。因此队列**必须保序**，且顺序必须是画家算法意义上的
 * 「父先子后」——否则位于容器玻璃面（底栏 / 卡片 / 侧边栏）内部的开关圆头、按钮玻璃
 * 会被容器那一层抹掉，呈现用户反馈的「轨道可见、圆头消失」与「按钮整体隐形」。
 */
struct PendingPanel {
    int index = -1;
    int layer = 0;
    float area = 0.0f;
    int order = 0;
};

PendingPanel g_panelPendingBelow[kPanelPendingLimit] = {};
int g_panelPendingBelowCount = 0;
PendingPanel g_panelPendingAbove[kPanelPendingLimit] = {};
int g_panelPendingAboveCount = 0;
int g_panelSubmitOrder = 0;
bool g_panelHookInstalled = false;

void enqueuePendingPanel(int index) {
    if (index < 0) return;
    // 队列归属由「当前正在绘制控件的 layer」唯一决定：g_drawLayer 由 drawWidgets / 导航栏
    // 在每个控件（含容器面板、开关、滑条、按钮、分段行）绘制前写入，是层级判定的单一真源。
    // OVERLAY 及以上进 above（压在 ImGui 之上），其余进 below（在 ImGui 控件之下）。
    const lgx::Box frame = lgx::panelFrame(index);
    PendingPanel entry;
    entry.index = index;
    entry.layer = g_drawLayer;
    entry.area = frame.width() * frame.height();
    entry.order = g_panelSubmitOrder++;
    if (g_drawLayer >= kPanelAboveLayerThreshold) {
        if (g_panelPendingAboveCount < kPanelPendingLimit) {
            g_panelPendingAbove[g_panelPendingAboveCount++] = entry;
        }
    } else if (g_panelPendingBelowCount < kPanelPendingLimit) {
        g_panelPendingBelow[g_panelPendingBelowCount++] = entry;
    }
}

/** 拼装顺序：layer 升序 → 面积降序（父先子后）→ 提交序（稳定兜底）。 */
bool pendingBefore(const PendingPanel& a, const PendingPanel& b) {
    if (a.layer != b.layer) return a.layer < b.layer;
    if (a.area != b.area) return a.area > b.area;
    return a.order < b.order;
}

void renderPendingPanels(bool above) {
    // 顺序上「全部抓取 → 全部绘制」，避免后画的面板采样到本帧已绘制的玻璃。
    //
    // clip 取整屏宿主框：面板在绘制期只是被登记（此刻 ImGui 裁剪栈可能停在某控件上），
    // 若沿用 g_currentClip 会把面板裁掉导致整块玻璃不显示。面板自身坐标已在 frame 内，
    // 用整屏裁剪即可，不会溢出到别的区域。
    const lgx::Box clip(0.0f, 0.0f, g_width, g_height);
    PendingPanel* queue = above ? g_panelPendingAbove : g_panelPendingBelow;
    const int count = above ? g_panelPendingAboveCount : g_panelPendingBelowCount;
    if (count <= 0) return;
    if (above) g_panelPendingAboveCount = 0;
    else g_panelPendingBelowCount = 0;
    // 同层内面积大的先画：容器玻璃面必然大于其内部控件，于是父先子后 =
    // 容器先落地、控件玻璃后覆盖，圆头/按钮不再被容器抹掉（不依赖任何硬编码的层级表）。
    std::sort(queue, queue + count, pendingBefore);
    for (int i = 0; i < count; ++i) lgx::runPanelCapture(queue[i].index, clip);
    for (int i = 0; i < count; ++i) lgx::runPanelPaint(queue[i].index, clip);
}

// ---- 模态弹窗：真液态玻璃弹窗绘制入口 ----
// 由库 lgx::Modal 整块自绘（全屏遮罩 + 玻璃卡片 + 标题/正文 + 玻璃按钮），提供两种弹出动画：
//   无 origin → 从屏幕中心长出；带 origin → 从触发按钮位置长出（见 nativeDialogShow）。
// 调用时机必须落在 renderPendingPanels(false) 之后、ImGui::Render() 之前：
//   · 遮罩被 GlassBackend::onFillRect 拦截为「立即 GL」，先于玻璃卡片落到帧缓冲；
//   · 玻璃卡片由库内部以「立即 GL」出图；
//   · 玻璃按钮走统一延后队列，绘制后由 drawDialog 之后的 renderPendingPanels(false) 刷新；
//   · 文字仍写 ImGui draw list，在帧末 RenderDrawData 回放时压在玻璃之上，层级正确。
// 返回结果码（1=取消 / 2=确定）在关闭动画播完那一帧由 Modal::draw 给出。
void drawDialog() {
    if (!s_dialog || !s_dialog->visible) return;
    g_dialogScrimActive = true;
    g_dialogScrimDrawn = false;
    g_drawLayer = 0;
    g_drawInteractive = true;
    const int dialogResult = s_dialog->draw(lgx::Box(0.0f, 0.0f, g_width, g_height));
    g_dialogScrimActive = false;
    g_dialogScrimDrawn = false;
    // 弹窗自身按钮的探针走 Modal 内部（lgx::PointerState），不受 g_drawInteractive 影响。
    if (dialogResult == 1 || dialogResult == 2) g_dialogResultPending.store(dialogResult);
}

void drawWidgets() {
    const bool controlsOn = g_controlsEnabled.load();
    const bool panelsOn = g_glassPanelsAlwaysOn.load();
    // 控件玻璃与「弹窗容器玻璃面」都关闭时才完全停绘；否则至少放行 Panel（见 g_glassPanelsAlwaysOn）。
    if (!controlsOn && !panelsOn) return;
    // 下发 App 真实强调色，替代库内置的硬编码绿(Toggle)/蓝(Slider/Tab)。
    const unsigned int accent = g_accentColor.load();
    // 按 layer 升序绘制：高层控件后画，从而覆盖低层（如抽屉压住导航栏）。
    // layer 相同时按 id 升序，保持既有稳定顺序。
    std::vector<Widget*> ordered;
    ordered.reserve(g_widgets.size());
    for (auto& entry : g_widgets) ordered.push_back(entry.second.get());
    std::stable_sort(ordered.begin(), ordered.end(),
                     [](const Widget* a, const Widget* b) { return a->layer < b->layer; });
    for (Widget* wp : ordered) {
        Widget& w = *wp;
        // 控件玻璃关闭时不绘制自绘控件（按钮/开关/滑条/分段行），只保留容器玻璃面。
        if (!controlsOn && w.type != WidgetType::Panel) continue;
        // 记录「当前正在绘制哪个控件」，供 onProbe 做遮挡/交互性判定。
        g_drawLayer = w.layer;
        g_drawInteractive = w.interactive;
        switch (w.type) {
            case WidgetType::Button: {
                if (!w.button) break;
                // 先赋文本，再由 variant 覆盖（Icon 变体会把 label 置 nullptr）。
                w.button->label = w.label.empty() ? nullptr : w.label.c_str();
                w.button->textSizeSp = w.textSizeSp;
                // draw-only 芯片：库内 PillButton 自带 interactive 开关，直接透传。
                w.button->interactive = w.interactive;
                applyButtonVariant(*w.button, w);
                applyStyle(*w.button, widgetPhase(w.id));
                // 交叉淡入淡出会短时间让命中盒与玻璃面不一致，取下限保证不出现「按不中」。
                collectHitRect(w, w.box, w.cornerRadius);
                if (w.button->draw(w.box)) enqueueEvent(w.id, 0, 0);
                break;
            }
            case WidgetType::Toggle: {
                if (!w.toggle) break;
                if (accent != 0u) w.toggle->accentColor = accent;
                collectHitRect(w, w.box, 0.0f);
                if (w.toggle->draw(w.box)) {
                    w.on = w.toggle->on;
                    enqueueEvent(w.id, 1, w.on ? 1 : 0);
                }
                break;
            }
            case WidgetType::Slider: {
                if (!w.slider) break;
                if (accent != 0u) w.slider->accentColor = accent;
                collectHitRect(w, w.box, 0.0f);
                if (w.slider->draw(w.box)) {
                    w.value = w.slider->value;
                    const float span = w.maxV - w.minV;
                    const float norm = span > 1.0e-6f ? (w.value - w.minV) / span : 0.0f;
                    const int q = (int)(norm * 16777215.0f + 0.5f);
                    enqueueEvent(w.id, 2, q < 0 ? 0 : (q > 16777215 ? 16777215 : q));
                }
                break;
            }
            case WidgetType::Segmented: {
                if (!w.strip) break;
                if (accent != 0u) w.strip->accentColor = accent;
                collectHitRect(w, w.box, 0.0f);
                if (w.strip->draw(w.box)) {
                    w.activeIndex = w.strip->activeIndex;
                    enqueueEvent(w.id, 3, w.activeIndex);
                }
                break;
            }
            case WidgetType::Panel: {
                // 容器类玻璃面（Card / Drawer / TextField 等）：只登记任务，绘制交给渲染器
                // 在绘制命令回放时执行（此时才允许动 draw list 写指针）。所有 Panel 的
                // capture 都排在所有 paint 之前，避免互相污染采样源。
                // 面板不产生交互事件，但需作为「遮挡体」进入命中快照：其下更低 layer 的
                // 控件在面板范围内应被抑制，避免触控穿透到被抽屉盖住的芯片/导航栏。
                collectHitRect(w, w.box, w.cornerRadius);
                const int index = lgx::submitPanel(makePanelStyle(w), w.box);
                if (index >= 0) {
                    w.panelIndex = index;
                    // 队列归属由 enqueuePendingPanel 依据 g_drawLayer（此刻即 w.layer）判定。
                    lgx::enqueuePanel(index);
                }
                break;
            }
        }
    }
    g_drawLayer = 0;
    g_drawInteractive = true;
}

bool widgetsBusy() {
    if (!g_controlsEnabled.load()) return false;
    for (const auto& entry : g_widgets) {
        const Widget& w = *entry.second;
        switch (w.type) {
            case WidgetType::Button: if (w.button && w.button->busy()) return true; break;
            case WidgetType::Toggle: if (w.toggle && w.toggle->busy()) return true; break;
            case WidgetType::Slider: if (w.slider && w.slider->busy()) return true; break;
            case WidgetType::Segmented: if (w.strip && w.strip->busy()) return true; break;
            case WidgetType::Panel: break;   // 面板无动画，恒不忙
        }
    }
    return false;
}

void drainTouch(ImGuiIO& io) {
    std::lock_guard<std::mutex> lock(g_touchMutex);
    bool buttonApplied = false;
    while (!g_touchQueue.empty()) {
        const TouchEvent ev = g_touchQueue.front();
        if (ev.action == 2) {                 // MOVE：位置事件可全部消费
            io.AddMousePosEvent(ev.x, ev.y);
            g_touchQueue.pop_front();
            continue;
        }
        if (buttonApplied) break;             // 每帧至多一次按键状态转换
        io.AddMousePosEvent(ev.x, ev.y);
        io.AddMouseButtonEvent(0, ev.action == 0);
        buttonApplied = true;
        g_touchQueue.pop_front();
    }
    g_touchPending.store(!g_touchQueue.empty());
}

/**
 * 宿主 GL 状态守卫。
 *
 * 面板绘制使用库自己的 VAO / program / 纹理单元，并会开启 scissor，与 ImGui 的渲染状态
 * 互不兼容。每次 flush 前后都必须成对保存/恢复，否则 ImGui 在下一帧会拿到失效的 program
 * 或 VAO（表现为 UI 全黑、错位、偶发花屏）。
 *
 * 这里用 RAII 把这段样板收敛成构造/析构，新增 flush 点时不会再漏写恢复逻辑；
 * 同时保证中途 `return` 也不会破坏状态。
 */
class GlStateGuard {
public:
    GlStateGuard() {
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao_);
        glGetIntegerv(GL_CURRENT_PROGRAM, &program_);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex0_);
        // 进入即关闭 scissor：面板捕获/绘制会自行按面板框设置裁剪，若沿用上一条遗留裁剪框
        //（ImGui 回放或上一个 flush 留下），整个面板会被裁掉导致「玻璃不显示」。
        glDisable(GL_SCISSOR_TEST);
    }
    ~GlStateGuard() {
        glBindVertexArray((GLuint)vao_);
        glUseProgram((GLuint)program_);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, (GLuint)tex0_);
        // 库绘制与 ImGui 回放都会设置 scissor；统一关闭，交由下一次 RenderDrawData
        // 按各自 draw cmd 重新开启。留着上一条裁剪框会把后续面板整块裁掉。
        glDisable(GL_SCISSOR_TEST);
    }
    GlStateGuard(const GlStateGuard&) = delete;
    GlStateGuard& operator=(const GlStateGuard&) = delete;

private:
    GLint vao_ = 0;
    GLint program_ = 0;
    GLint tex0_ = 0;
};

void renderFrame() {
    if (!g_ready || !g_initializedGL) return;
    if (g_width <= 0.0f || g_height <= 0.0f) return;
    static int s_frameLog = 0;
    if ((s_frameLog++ % 120) == 0) {
        LOGI("renderFrame widgets=%zu controls=%d navbar=%d panelBelow=%d panelAbove=%d",
             g_widgets.size(), g_controlsEnabled.load() ? 1 : 0,
             g_navbarEnabled.load() ? 1 : 0, g_panelPendingBelowCount,
             g_panelPendingAboveCount);
    }

    const float now = nowSec();
    float dt = now - g_lastFrame;
    if (!(dt > 0.0f)) dt = 1.0f / 60.0f;
    if (dt > 0.1f) dt = 0.1f;
    g_lastFrame = now;

    const int vw = (int)(g_width + 0.5f);
    const int vh = (int)(g_height + 0.5f);
    glViewport(0, 0, vw, vh);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    ImGuiIO& io = ImGui::GetIO();
    io.DisplaySize = ImVec2(g_width, g_height);
    io.DisplayFramebufferScale = ImVec2(1.0f, 1.0f);

    lgx::beginFrame(dt);
    // 面板提交序每帧归零：它是「同层同面积」时的稳定兜底，只需帧内单调，不必跨帧累积。
    g_panelSubmitOrder = 0;
    ImGui_ImplOpenGL3_NewFrame();
    drainTouch(io);
    ImGui::NewFrame();

    // 面板延后执行钩子：绘制期只登记面板任务，帧内在 ImGui::Render() 之前统一绘制。
    if (!g_panelHookInstalled) {
        lgx::setEnqueuePanelHook([](int index) { enqueuePendingPanel(index); });
        g_panelHookInstalled = true;
    }

    lgx::Surface surface;
    surface.width = g_width;
    surface.height = g_height;
    surface.pixelScaleX = 1.0f;
    surface.pixelScaleY = 1.0f;
    lgx::setSurface(surface);
    lgx::setHostBounds(lgx::Box(0.0f, 0.0f, g_width, g_height));

    lgx::PointerState pointer;
    pointer.x = io.MousePos.x;
    pointer.y = io.MousePos.y;
    pointer.down = io.MouseDown[0];
    pointer.pressed = io.MouseClicked[0];
    pointer.released = io.MouseReleased[0];
    lgx::setPointer(pointer);

    g_currentClip = lgx::Box(0.0f, 0.0f, g_width, g_height);

    // 动画相位推进：仅用于按钮/芯片等自绘控件的呼吸/高光调制（见 applyStyle）。
    if (g_animate.load()) g_animPhase += dt;
    if (g_animPhase > 6.2831853f) g_animPhase -= 6.2831853f;

    initNavbar();

    // 模态弹窗可见时，底层控件与导航栏一律停绘：它们的文字会进 ImGui draw list，
    // 帧末回放时会浮在「立即绘制」的遮罩之上，导致遮罩失效、玻璃被压暗（伪玻璃观感）。
    // 同时清空命中快照：弹窗期间触摸全部交由 Modal 内部探针处理，底层控件不得抢占事件。
    const bool dialogUp = (s_dialog != nullptr && s_dialog->visible);
    if (dialogUp) {
        {
            std::lock_guard<std::mutex> lock(g_hitMutex);
            g_hitRects.clear();
        }
        g_hitDirty = false;
    }

    if (!dialogUp && g_navbarEnabled.load()) {
        const unsigned int navAccent = g_accentColor.load();
        if (navAccent != 0u) s_tabs.accentColor = navAccent;
        // 导航栏容器玻璃面色取库默认色（TAB_CONTAINER(_LIGHT)，alpha≈0.40）的淡化版：
        // 首页内容较短，导航栏背后常为纯色背景，0.40 的白/黑叠在纯色上就是一块
        // 不透明底板；降到约 0.20 后面板中心几乎与背景融为一体，玻璃感由边缘高光与
        // 模糊（背后有内容时）承担。只作用于导航栏；库内 Segmented 分段行仍用默认常量。
        s_tabs.containerColor = g_light.load() ? 0x33FAFAFAu : 0x33121212u;
        // 导航栏位于 BASE 层：若被更高 layer 的抽屉/弹层覆盖，其探针应被抑制。
        g_drawLayer = 0;
        g_drawInteractive = true;
        if (s_tabs.draw(navbarBox())) {
            g_navIndex.store(s_tabs.activeIndex);
            g_pendingNavTap.store(s_tabs.activeIndex);
        }
    }

    if (!dialogUp) drawWidgets();

    // below 队列：常规容器玻璃，须在 ImGui 控件之下（早于 RenderDrawData）。
    // 守卫负责把库绘制期间的 VAO / program / 纹理 / scissor 恢复成 ImGui 期望的状态。
    {
        GlStateGuard guard;
        renderPendingPanels(false);
    }

    // ---- 真液态玻璃模态弹窗：整块由库原生自绘 ----
    // 此刻底层 ImGui 背景已定格、玻璃卡片经立即 GL 出图，遮罩在下、玻璃在上；
    // 弹窗文字写入 draw list，随下方 ImGui::Render() 在帧末回放，压在玻璃之上。
    if (dialogUp) {
        GlStateGuard guard;
        drawDialog();
        // 弹窗自身的玻璃按钮同样走延后队列（与页面控件共用同一套绘制管线），而弹窗绘制
        // 晚于本帧的 below flush，故在此补一次刷新，否则按钮玻璃不会出图。
        renderPendingPanels(false);
    }

    ImGui::Render();
    // 帧末统一发布命中盒快照：此刻坐标已定格，不会把半帧坐标暴露给触摸线程。
    endWidgetFrame();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());

    // above 队列：抽屉/弹层等 OVERLAY 面板，须压在全部 ImGui 控件之上（晚于 RenderDrawData）。
    // 此刻 ImGui 已在默认帧缓冲里画完，面板据此采样即可把其下的芯片/导航栏一并纳入玻璃。
    // 同样需保存/恢复 ImGui 遗留的 GL 状态，避免影响下一帧。
    if (g_panelPendingAboveCount > 0) {
        // ImGui 回放结束可能遗留裁剪框；守卫内部同样以「关闭 scissor」收尾。
        GlStateGuard guard;
        renderPendingPanels(true);
    }
}

bool needsFrame() {
    if (!g_ready) return false;
    // 模态弹窗可见时必须持续出帧：弹出/关闭动画与 backdrop 刷新都依赖逐帧推进，
    // RENDERMODE_WHEN_DIRTY 下一旦停帧，弹窗会卡在动画中途。
    if (s_dialog != nullptr && s_dialog->visible) return true;
    if (g_touchPending.load()) {
        g_autoFrames = 0;
        return true;
    }
    // 动画开启且**存在会动的玻璃**时必须持续出帧：RENDERMODE_WHEN_DIRTY 下一旦停帧，
    // 呼吸动画会永久停滞（needsFrame 返回 false 后 Kotlin 不再 requestRender）。
    //
    // 只把「自绘控件 / 导航栏」算作动画源：容器面板（Panel）是静态材质，自身不做呼吸
    //（见 makePanelStyle），其内容跟随 backdrop 更新——而 backdrop 采集成功后会主动
    // requestRender（LiquidGlassView.captureBackdrop）。早先把 g_glassPanelsAlwaysOn
    // 也计入动画源，导致「只开容器玻璃」时每秒空转 60 帧，纯耗电无视觉收益。
    if (g_animate.load() && (g_controlsEnabled.load() || g_navbarEnabled.load())) {
        return true;
    }
    bool busy = widgetsBusy();
    if (g_navbarEnabled.load()) busy = busy || s_tabs.busy();
    if (!busy) {
        g_autoFrames = 0;
        return false;
    }
    if (g_autoFrames > 1200) return false;   // 安全阀：避免无限自刷新
    ++g_autoFrames;
    return true;
}

// ---- 控件注册/更新（GL 线程） ----
Widget* upsert(int id, WidgetType type) {
    auto it = g_widgets.find(id);
    if (it != g_widgets.end() && it->second->type == type) return it->second.get();
    // 类型变化会重建控件，旧图标纹理需先释放，避免泄漏。
    if (it != g_widgets.end() && it->second->iconTex != 0u) {
        glDeleteTextures(1, &it->second->iconTex);
    }
    auto widget = std::make_unique<Widget>();
    widget->id = id;
    widget->type = type;
    Widget* raw = widget.get();
    g_widgets[id] = std::move(widget);
    return raw;
}

// 把 Compose 下发的 ARGB_8888 位图（白色字形 + alpha）上传为 GL 纹理。
//  - 保持 top-down 行序：ImGui 的纹理原点在左上（uv(0,0) 对应 quad 左上角、
//    采样的是 GL 数据第 0 行），与 Bitmap 的行序一致，不得再翻转，否则图标上下颠倒；
//  - 反预乘：Android ARGB_8888 为预乘 alpha，而 ImGui 用直通 alpha 混合，
//    不反预乘会让图标边缘发暗。
// 成功返回新纹理 id（>0），失败返回 0；调用方负责释放旧纹理。
unsigned int uploadArgbBitmapTexture(JNIEnv* env, jobject bitmap) {
    if (bitmap == nullptr) return 0u;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("icon: getInfo failed");
        return 0u;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("icon: unexpected format %d", info.format);
        return 0u;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        pixels == nullptr) {
        LOGE("icon: lockPixels failed");
        return 0u;
    }

    const int iw = (int)info.width;
    const int ih = (int)info.height;
    const size_t rowBytes = (size_t)iw * 4u;
    std::vector<unsigned char> scratch(rowBytes * (size_t)ih);
    const unsigned char* src = (const unsigned char*)pixels;
    for (int y = 0; y < ih; ++y) {
        const unsigned char* srcRow = src + (size_t)y * rowBytes;
        unsigned char* dstRow = scratch.data() + (size_t)y * rowBytes;
        memcpy(dstRow, srcRow, rowBytes);
        for (int x = 0; x < iw; ++x) {
            unsigned char* px = dstRow + (size_t)x * 4u;
            const unsigned int a = px[3];
            if (a != 0u && a != 255u) {
                px[0] = (unsigned char)((px[0] * 255u + a / 2u) / a);
                px[1] = (unsigned char)((px[1] * 255u + a / 2u) / a);
                px[2] = (unsigned char)((px[2] * 255u + a / 2u) / a);
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    unsigned int tex = 0u;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, iw, ih, 0, GL_RGBA, GL_UNSIGNED_BYTE, scratch.data());
    glBindTexture(GL_TEXTURE_2D, 0);
    return tex;
}

} // namespace

// ---- JNI ----
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeInit(
        JNIEnv*, jobject, jfloat width, jfloat height, jfloat density) {
    g_width = width;
    g_height = height;
    g_density = density > 0.0f ? density : 2.0f;
    if (!glInit()) return JNI_FALSE;
    if (!lgStart()) return JNI_FALSE;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeResize(
        JNIEnv*, jobject, jfloat width, jfloat height) {
    g_width = width;
    g_height = height;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRender(JNIEnv*, jobject) {
    renderFrame();
}

JNIEXPORT jboolean JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeNeedsFrame(JNIEnv*, jobject) {
    return needsFrame() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetEnabled(
        JNIEnv*, jobject, jboolean controls, jboolean navbar) {
    g_controlsEnabled.store(controls != JNI_FALSE);
    g_navbarEnabled.store(navbar != JNI_FALSE);
    LOGI("setEnabled controls=%d navbar=%d", controls ? 1 : 0, navbar ? 1 : 0);
}

// 模态弹窗开关：置 true 后，即使控件玻璃关闭，原生层仍绘制容器玻璃面（Panel）。
// 与 nativeSetEnabled 分离，避免把「用户是否开启控件玻璃」与「弹窗是否需要玻璃底」混为一谈。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetGlassPanelsAlwaysOn(
        JNIEnv*, jobject, jboolean on) {
    g_glassPanelsAlwaysOn.store(on != JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetColorMode(
        JNIEnv*, jobject, jboolean light) {
    const bool value = light != JNI_FALSE;
    g_light.store(value);
    if (g_ready) applyThemeColors(value);
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetThemeColors(
        JNIEnv*, jobject, jint accentArgb, jint surfaceArgb, jint iconArgb) {
    g_accentColor.store((unsigned int)accentArgb);
    g_surfaceColor.store((unsigned int)surfaceArgb);
    // App 侧按主题前景色（onSurface）下发，未下发时由库按亮暗自适应回退。
    g_iconColor.store((unsigned int)iconArgb);
    if (g_ready) applyThemeColors(g_light.load());
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetNavIndex(
        JNIEnv*, jobject, jint index) {
    const int value = index < 0 ? 0 : (index > kNavCount - 1 ? kNavCount - 1 : index);
    g_navIndex.store(value);
    if (s_tabsInited && s_tabs.activeIndex != value) s_tabs.select(value, true);
}

JNIEXPORT jint JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativePollNavTap(JNIEnv*, jobject) {
    return g_pendingNavTap.exchange(-1);
}

// ---- 全局样式 ----
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetStyle(
        JNIEnv*, jobject, jfloat blurDp, jfloat lensHeightDp, jfloat lensAmountDp,
        jfloat spectral, jfloat tintAlpha, jboolean shadowOn, jboolean glassOn,
        jboolean contourEdge) {
    g_style.blurDp = blurDp;
    g_style.lensHeightDp = lensHeightDp;
    g_style.lensAmountDp = lensAmountDp;
    g_style.spectral = spectral;
    g_style.tintAlpha = tintAlpha;
    g_style.shadowOn = shadowOn != JNI_FALSE;
    g_style.glassOn = glassOn != JNI_FALSE;
    g_style.contourEdge = contourEdge != JNI_FALSE;
}

// ---- 容器玻璃面（Panel）专属参数 ----
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetPanelStyle(
        JNIEnv*, jobject, jfloat blurDp, jfloat padDp, jfloat fillAlpha) {
    g_panelStyle.blurDp = blurDp;
    g_panelStyle.padDp = padDp;
    g_panelStyle.fillAlpha = fillAlpha;
    lgx::setPanelPaddingPx(lgx::px(padDp));
}

// ---- 动态控件注册 ----
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRegisterButton(
        JNIEnv* env, jobject, jint id, jfloat left, jfloat top, jfloat right, jfloat bottom,
        jstring label, jint iconId, jfloat textSizeSp, jint variant, jboolean selected,
        jfloat cornerRadius) {
    Widget* w = upsert(id, WidgetType::Button);
    w->box = lgx::Box(left, top, right, bottom);
    w->iconId = iconId;
    w->textSizeSp = textSizeSp > 0.0f ? textSizeSp : lgx::config::Layout::BUTTON_TEXT_SP;
    w->variant = variant >= 0 && variant <= static_cast<int>(ButtonVariant::Text)
                     ? static_cast<ButtonVariant>(variant)
                     : ButtonVariant::Plain;
    w->selected = selected != JNI_FALSE;
    // 圆角半径（px）：>0 时芯片/按钮按该半径绘制，0 时回退胶囊（PillButton 默认）。
    w->cornerRadius = cornerRadius > 0.0f ? cornerRadius : 0.0f;
    if (label != nullptr) {
        const char* chars = env->GetStringUTFChars(label, nullptr);
        w->label.assign(chars != nullptr ? chars : "");
        if (chars != nullptr) env->ReleaseStringUTFChars(label, chars);
    } else {
        w->label.clear();
    }
    if (!w->button) w->button = std::make_unique<lgx::PillButton>();
    // 注册/更新只标记脏：命中快照统一在帧末以「已绘制过的控件」为准发布，
    // 避免此刻（控件尚未绘制、hitValid 全为 false）把快照清成空导致整屏点不中。
    g_hitDirty = true;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRegisterToggle(
        JNIEnv*, jobject, jint id, jfloat left, jfloat top, jfloat right, jfloat bottom,
        jboolean on) {
    Widget* w = upsert(id, WidgetType::Toggle);
    w->box = lgx::Box(left, top, right, bottom);
    w->on = on != JNI_FALSE;
    if (!w->toggle) w->toggle = std::make_unique<lgx::Toggle>();
    w->toggle->setOn(w->on, false);
    g_hitDirty = true;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRegisterSlider(
        JNIEnv*, jobject, jint id, jfloat left, jfloat top, jfloat right, jfloat bottom,
        jfloat value, jfloat minValue, jfloat maxValue) {
    Widget* w = upsert(id, WidgetType::Slider);
    w->box = lgx::Box(left, top, right, bottom);
    w->minV = minValue;
    w->maxV = maxValue > minValue ? maxValue : minValue + 1.0f;
    w->value = value;
    if (!w->slider) w->slider = std::make_unique<lgx::Slider>();
    w->slider->setRange(w->minV, w->maxV);
    w->slider->value = w->value;
    g_hitDirty = true;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRegisterSegmented(
        JNIEnv* env, jobject, jint id, jfloat left, jfloat top, jfloat right, jfloat bottom,
        jobjectArray labels, jintArray iconIds, jint activeIndex) {
    Widget* w = upsert(id, WidgetType::Segmented);
    w->box = lgx::Box(left, top, right, bottom);

    w->tabs.clear();
    w->tabIconIds.clear();
    const int count = labels != nullptr ? env->GetArrayLength(labels) : 0;
    for (int i = 0; i < count; ++i) {
        jstring item = (jstring)env->GetObjectArrayElement(labels, i);
        std::string text;
        if (item != nullptr) {
            const char* chars = env->GetStringUTFChars(item, nullptr);
            text.assign(chars != nullptr ? chars : "");
            if (chars != nullptr) env->ReleaseStringUTFChars(item, chars);
            env->DeleteLocalRef(item);
        }
        w->tabs.push_back(text);
    }
    if (iconIds != nullptr && env->GetArrayLength(iconIds) == count) {
        std::vector<jint> tmp((size_t)count);
        env->GetIntArrayRegion(iconIds, 0, count, tmp.data());
        w->tabIconIds.assign(tmp.begin(), tmp.end());
    } else {
        w->tabIconIds.assign((size_t)count, -1);
    }

    if (!w->strip) w->strip = std::make_unique<lgx::TabStrip>();
    w->strip->resetTabs();
    const int limit = count > lgx::TAB_LIMIT ? lgx::TAB_LIMIT : count;
    for (int i = 0; i < limit; ++i) {
        w->strip->appendTab(w->tabs[(size_t)i].c_str());
        w->strip->painters[i] = iconPainterFor(w->tabIconIds[(size_t)i]);
    }
    w->activeIndex = activeIndex < 0 ? 0 : (activeIndex >= limit ? limit - 1 : activeIndex);
    w->strip->select(w->activeIndex, false);
    g_hitDirty = true;
}

// 注册容器类玻璃面（Card / Drawer / TextField 等）：只绘制玻璃底，不产生交互事件。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRegisterPanel(
        JNIEnv*, jobject, jint id, jfloat left, jfloat top, jfloat right, jfloat bottom,
        jfloat cornerRadius) {
    Widget* w = upsert(id, WidgetType::Panel);
    w->box = lgx::Box(left, top, right, bottom);
    w->cornerRadius = cornerRadius > 0.0f ? cornerRadius : 0.0f;
    w->panelIndex = -1;
    g_hitDirty = true;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeUpdateBox(
        JNIEnv*, jobject, jint id, jfloat left, jfloat top, jfloat right, jfloat bottom,
        jfloat cornerRadius) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    Widget& w = *it->second;
    w.box = lgx::Box(left, top, right, bottom);
    // 容器玻璃面（Panel）的圆角随几何一并更新；其余控件忽略该参数。
    if (w.type == WidgetType::Panel) w.cornerRadius = cornerRadius > 0.0f ? cornerRadius : 0.0f;
    g_hitDirty = true;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeUpdateButton(
        JNIEnv* env, jobject, jint id, jstring label) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    Widget& w = *it->second;
    if (label != nullptr) {
        const char* chars = env->GetStringUTFChars(label, nullptr);
        w.label.assign(chars != nullptr ? chars : "");
        if (chars != nullptr) env->ReleaseStringUTFChars(label, chars);
    } else {
        w.label.clear();
    }
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeUpdateToggle(
        JNIEnv*, jobject, jint id, jboolean on) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    Widget& w = *it->second;
    w.on = on != JNI_FALSE;
    if (w.toggle) w.toggle->setOn(w.on, true);
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeUpdateSlider(
        JNIEnv*, jobject, jint id, jfloat value) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    Widget& w = *it->second;
    w.value = value;
    if (w.slider) w.slider->value = value;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeUpdateSegmented(
        JNIEnv*, jobject, jint id, jint activeIndex) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    Widget& w = *it->second;
    w.activeIndex = activeIndex;
    if (w.strip) w.strip->select(activeIndex, true);
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetButtonVariant(
        JNIEnv*, jobject, jint id, jint variant) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    it->second->variant = variant >= 0 && variant <= static_cast<int>(ButtonVariant::Text)
                              ? static_cast<ButtonVariant>(variant)
                              : ButtonVariant::Plain;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetButtonSelected(
        JNIEnv*, jobject, jint id, jboolean selected) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    it->second->selected = selected != JNI_FALSE;
}

// 设置控件层级（值越大越靠上）：高层控件绘制在后、命中优先，
// 使覆盖层（抽屉等）能压住并拦截导航栏/子导航等低层控件。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetWidgetLayer(
        JNIEnv*, jobject, jint id, jint layer) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    it->second->layer = (int)layer;
    g_hitDirty = true;
}

// 设置控件是否参与交互。false = draw-only：只绘制玻璃外观，命中与事件全部交给 Compose
// （用于落在 LazyRow 等滚动容器内的芯片，避免原生吞掉横向滑动手势）。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetWidgetInteractive(
        JNIEnv*, jobject, jint id, jboolean interactive) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    it->second->interactive = interactive != JNI_FALSE;
    g_hitDirty = true;
}

// 开关液态玻璃呼吸动画（默认开）。关闭后 needsFrame 不再为动画持续出帧，降低功耗。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetAnimate(
        JNIEnv*, jobject, jboolean animate) {
    g_animate.store(animate != JNI_FALSE);
}

// Compose 下发的图标位图：白色字形 + alpha（已由 Compose 渲染成最终字形）。
// 上传为 GL 纹理（top-down + 反预乘），供 texIconPainter 以文字色着色绘制。
JNIEXPORT jboolean JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetButtonIconBitmap(
        JNIEnv* env, jobject, jint id, jobject bitmap) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end() || bitmap == nullptr) return JNI_FALSE;
    Widget& w = *it->second;
    const unsigned int tex = uploadArgbBitmapTexture(env, bitmap);
    if (tex == 0u) return JNI_FALSE;
    if (w.iconTex != 0u) glDeleteTextures(1, &w.iconTex);
    w.iconTex = tex;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeClearButtonIcon(
        JNIEnv*, jobject, jint id) {
    auto it = g_widgets.find(id);
    if (it == g_widgets.end()) return;
    Widget& w = *it->second;
    if (w.iconTex != 0u) {
        glDeleteTextures(1, &w.iconTex);
        w.iconTex = 0u;
    }
}

// ---- 导航栏图标：上传应用内图标位图（白字+alpha），供 texIconPainter 着色绘制。----
// index: 0=首页 1=创作 2=P站。已有的同名纹理会先释放，避免泄漏。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeSetNavIconBitmap(
        JNIEnv* env, jobject, jint index, jobject bitmap) {
    if (index < 0 || index >= kNavCount) return;
    const unsigned int tex = uploadArgbBitmapTexture(env, bitmap);
    if (tex == 0u) return;
    unsigned int& slot = s_navIconTex[index];
    if (slot != 0u) glDeleteTextures(1, &slot);
    slot = tex;
    // 已初始化的导航栏需刷新 painter/tag，使其立即使用新图标。
    if (s_tabsInited) {
        s_tabs.painters[index] = texIconPainter;
        s_tabs.tags[index] = &slot;
    }
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeUnregisterWidget(
        JNIEnv*, jobject, jint id) {
    auto it = g_widgets.find(id);
    if (it != g_widgets.end()) {
        if (it->second->iconTex != 0u) glDeleteTextures(1, &it->second->iconTex);
        g_widgets.erase(it);
        g_hitDirty = true;
    }
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeClearWidgets(JNIEnv*, jobject) {
    for (auto& entry : g_widgets) {
        if (entry.second->iconTex != 0u) glDeleteTextures(1, &entry.second->iconTex);
    }
    g_widgets.clear();
    // 清空需立即生效：此刻直接发布空快照，避免清空后到下一帧之间仍能点到已删除控件。
    publishHitRects();
    g_hitDirty = false;
}

JNIEXPORT jlong JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativePollWidgetEvent(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_eventMutex);
    if (g_widgetEvents.empty()) return -1LL;
    const long long value = g_widgetEvents.front();
    g_widgetEvents.pop_front();
    return (jlong)value;
}

JNIEXPORT jboolean JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeHitTest(
        JNIEnv*, jobject, jfloat x, jfloat y) {
    if (g_width <= 0.0f || g_height <= 0.0f) return JNI_FALSE;
    // 命中规则：取该点「最上层」的已绘制元素（layer 最大；同层以可交互者优先），
    // 只有最上层元素可交互时才消费触摸，否则交给 Compose。
    // 这样被抽屉（高 layer Panel）盖住的芯片/导航栏不会再吞掉触摸（触控穿透修复）。
    bool found = false;
    bool topInteractive = false;
    int topLayer = 0;
    {
        std::lock_guard<std::mutex> lock(g_hitMutex);
        for (const HitRect& rect : g_hitRects) {
            if (!rect.box.holds(x, y)) continue;
            const bool better = !found || rect.layer > topLayer ||
                                (rect.layer == topLayer && rect.interactive && !topInteractive);
            if (!better) continue;
            found = true;
            topLayer = rect.layer;
            topInteractive = rect.interactive;
        }
    }
    // 导航栏视为 BASE(0) 层、可交互元素，一并参与 topmost 判定。
    if (g_navbarEnabled.load() && navbarBox().holds(x, y)) {
        const bool better = !found || 0 > topLayer || (0 == topLayer && !topInteractive);
        if (better) {
            found = true;
            topLayer = 0;
            topInteractive = true;
        }
    }
    return found && topInteractive ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeTouch(
        JNIEnv*, jobject, jint action, jfloat x, jfloat y) {
    {
        std::lock_guard<std::mutex> lock(g_touchMutex);
        g_touchQueue.push_back(TouchEvent{action, x, y});
    }
    g_touchPending.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeBindBackdrop(
        JNIEnv* env, jobject, jobject bitmap) {
    if (bitmap == nullptr || g_width <= 0.0f || g_height <= 0.0f) return JNI_FALSE;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("backdrop: getInfo failed");
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("backdrop: unexpected format %d", info.format);
        return JNI_FALSE;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS ||
        pixels == nullptr) {
        LOGE("backdrop: lockPixels failed");
        return JNI_FALSE;
    }

    const int w = (int)info.width;
    const int h = (int)info.height;
    const size_t rowBytes = (size_t)w * 4u;
    g_backdropScratch.resize(rowBytes * (size_t)h);
    const unsigned char* src = (const unsigned char*)pixels;
    // PixelCopy 得到 top-down 位图，而 GL 纹理坐标原点在左下 → 逐行倒序翻转。
    for (int y = 0; y < h; ++y) {
        const unsigned char* srcRow = src + (size_t)y * rowBytes;
        unsigned char* dstRow = g_backdropScratch.data() + (size_t)(h - 1 - y) * rowBytes;
        memcpy(dstRow, srcRow, rowBytes);
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    if (g_backdropTex == 0u) glGenTextures(1, &g_backdropTex);
    glBindTexture(GL_TEXTURE_2D, g_backdropTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                 g_backdropScratch.data());
    glBindTexture(GL_TEXTURE_2D, 0);

    // 库要求 backdrop 纹理尺寸为视图全尺寸（物理像素）。
    lgx::bindBackdropTexture(g_backdropTex, (int)g_width, (int)g_height);
    lgx::setBackdropEnabled(true);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeRelease(JNIEnv*, jobject) {
    if (g_backdropTex != 0u) {
        glDeleteTextures(1, &g_backdropTex);
        g_backdropTex = 0u;
    }
    g_backdropScratch.clear();

    {
        std::lock_guard<std::mutex> lock(g_hitMutex);
        g_hitRects.clear();
    }
    {
        std::lock_guard<std::mutex> lock(g_eventMutex);
        g_widgetEvents.clear();
    }
    {
        std::lock_guard<std::mutex> lock(g_touchMutex);
        g_touchQueue.clear();
    }
    g_touchPending.store(false);

    for (auto& entry : g_widgets) {
        if (entry.second->iconTex != 0u) glDeleteTextures(1, &entry.second->iconTex);
    }
    g_widgets.clear();
    s_tabsInited = false;

    // 弹窗资源回收：Modal 由智能指针析构，文本与结果码一并清零，
    // 避免 GLSurfaceView 重建后残留上一会话的弹窗状态。
    s_dialog.reset();
    s_dialogTitle.clear();
    s_dialogMessage.clear();
    s_dialogCancel.clear();
    s_dialogConfirm.clear();
    g_dialogScrimActive = false;
    g_dialogScrimDrawn = false;
    g_dialogResultPending.store(0);
    if (g_scrimProgram != 0u) {
        glDeleteProgram(g_scrimProgram);
        g_scrimProgram = 0u;
        g_scrimColorLoc = -1;
    }
    if (g_scrimVao != 0u) {
        glDeleteVertexArrays(1, &g_scrimVao);
        g_scrimVao = 0u;
    }
    if (g_scrimVbo != 0u) {
        glDeleteBuffers(1, &g_scrimVbo);
        g_scrimVbo = 0u;
    }

    lgShutdown();
    if (g_initializedGL) {
        ImGui_ImplOpenGL3_Shutdown();
        ImGui::DestroyContext();
        g_initializedGL = false;
    }
    g_font = nullptr;
    g_glyphRanges.clear();
}

// ---- 模态弹窗 JNI：真液态玻璃弹窗的显示/关闭/查询/取结果 ----
// 标题/正文/按钮文案由 Kotlin 下发。库只保存字符指针（Modal::open 内部仅赋值指针，
// 不复制内容），因此必须以长期存活的 std::string 持有，见 s_dialogTitle 等全局量。
//   showCancel=false → 单按钮弹窗（仅「确定」，取消键不参与布局与命中）；
//   fromOrigin=true  → 从 (originLeft,originTop)-(originRight,originBottom) 处长出（源点动画）；
//   fromOrigin=false → 从屏幕中心长出。
JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeDialogShow(
        JNIEnv* env, jobject, jstring title, jstring message, jstring cancelLabel,
        jstring confirmLabel, jboolean showCancel, jboolean fromOrigin,
        jfloat originLeft, jfloat originTop, jfloat originRight, jfloat originBottom) {
    if (!g_ready) return;
    if (!s_dialog) s_dialog.reset(new lgx::Modal());
    s_dialogTitle = jstringToUtf8(env, title);
    s_dialogMessage = jstringToUtf8(env, message);
    s_dialogCancel = jstringToUtf8(env, cancelLabel);
    s_dialogConfirm = jstringToUtf8(env, confirmLabel);
    s_dialog->title = s_dialogTitle.empty() ? nullptr : s_dialogTitle.c_str();
    s_dialog->message = s_dialogMessage.empty() ? nullptr : s_dialogMessage.c_str();
    s_dialog->cancelLabel = s_dialogCancel.empty() ? "Cancel" : s_dialogCancel.c_str();
    s_dialog->confirmLabel = s_dialogConfirm.empty() ? "OK" : s_dialogConfirm.c_str();
    s_dialog->cancelVisible = (showCancel != JNI_FALSE);
    g_dialogResultPending.store(0);
    const float ow = originRight - originLeft;
    const float oh = originBottom - originTop;
    if (fromOrigin != JNI_FALSE && ow > 1.0f && oh > 1.0f) {
        s_dialog->openFrom(s_dialog->title, s_dialog->message,
                           lgx::Box(originLeft, originTop, originRight, originBottom));
    } else {
        s_dialog->open(s_dialog->title, s_dialog->message);
    }
}

JNIEXPORT void JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeDialogClose(JNIEnv*, jobject, jint code) {
    if (!s_dialog) return;
    s_dialog->close((code == 1 || code == 2) ? (int)code : 1);
}

JNIEXPORT jboolean JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativeDialogVisible(JNIEnv*, jobject) {
    return (s_dialog != nullptr && s_dialog->visible) ? JNI_TRUE : JNI_FALSE;
}

// 取走「关闭动画播完」产生的结果码：1=取消 / 2=确定；当前无结果返回 -1。
JNIEXPORT jint JNICALL
Java_com_pencil_chuangyin_pro_liquid_LiquidGlassView_nativePollDialogResult(JNIEnv*, jobject) {
    const int value = g_dialogResultPending.load();
    if (value == 0) return -1;
    g_dialogResultPending.store(0);
    return (jint)value;
}

} // extern "C"