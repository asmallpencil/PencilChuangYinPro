// FILE: feature/ai/build.gradle.kts
// feature:ai —— 「AI 打印」功能：会话持久化、Android 侧工具（存储/打印/绘图）、聊天与任务 UI
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pencil.chuangyin.feature.ai"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:theme"))
    implementation(project(":core:ui"))
    implementation(project(":core:printer"))
    implementation(project(":core:agent"))

    // Google ADK for Kotlin 官方 KSP processor：为 @Tool/@Param 注解函数生成 FunctionTool 包装器
    ksp(libs.google.adk.processor)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 会话持久化 / 工具参数
    implementation(libs.kotlinx.serialization.json)

    // 权限模式持久化
    implementation(libs.androidx.datastore.preferences)

    // 联网检索 / 终端下载使用的 HTTP 客户端
    implementation(libs.okhttp)

    // 聊天页媒体查看器：图片加载与手势缩放 / 超大图子采样，与 P 站全屏查看器同源
    implementation(libs.coil.compose)
    implementation(libs.zoomimage.compose.coil2)
}
