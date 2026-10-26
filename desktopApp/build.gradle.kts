// FILE: desktopApp/build.gradle.kts
// 铅笔创印 Pro —— Windows 桌面端（Compose Multiplatform / JVM）
// 与 Android 主工程共用一套 P 站协议实现，UI 为桌面原生 Compose 版本。

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.12.1"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.runtime)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // 与 Android 侧复用的会话类保留 javax.inject 注解
    implementation("javax.inject:javax.inject:1")

    // 图片打印的 PDF 导入：桌面端没有 Android 的 PdfRenderer，用 PDFBox 取页数并栅格化
    implementation("org.apache.pdfbox:pdfbox:2.0.31")

    // 蓝牙热敏打印：JVM 没有内置蓝牙 API，用 JNA 直接调用 Winsock（AF_BTH / RFCOMM）
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // 备选通道：已配对蓝牙 SPP 会生成「蓝牙链接上的标准串行 (COMx)」虚拟串口
    implementation("com.fazecast:jSerialComm:2.11.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

compose.desktop {
    application {
        mainClass = "com.pencil.chuangyin.desktop.MainKt"

        nativeDistributions {
            // 说明：本机到 github.com 的连接被阻断，WiX 无法下载，
            // 因此不走 msi/exe 安装包（packageExe 需要 WiX），
            // 改用 createDistributable 产出可直接运行的 app-image（内含 exe 启动器）。
            packageName = "PencilChuangYinPro"
            packageVersion = "2.0.0"
            description = "Pencil ChuangYin Pro Desktop" // 必须是 ASCII：jpackage 按系统代码页读取 args 文件，中文会报 "Input length = 1"
            vendor = "Pencil"
            windows {
                // upgradeUuid 固定，日后能出安装包时保证升级复用同一安装标识
                upgradeUuid = "8f0f3f8a-5c1e-4f4e-9c2b-7b1f0a6d1c11"
            }
        }
    }
}

// WiX 只用于生成 msi/exe 安装包；app-image 由 JDK 自带的 jpackage 直接产出，不需要 WiX。
// 这里把「下载 / 解压 WiX」两个任务禁用，避免构建被 github 连接超时卡住。
// 同时预先创建 WiX 目录，满足插件对 createDistributable.wixToolsetDir 的输入校验。
val wixDir = rootProject.layout.buildDirectory.dir("wix311").get().asFile
wixDir.mkdirs()

rootProject.tasks.matching { it.name == "downloadWix" || it.name == "unzipWix" }.configureEach {
    enabled = false
}
