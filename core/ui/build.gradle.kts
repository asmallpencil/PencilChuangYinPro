// FILE: core/ui/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pencil.chuangyin.core.ui"
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
    // 液态玻璃 Kotlin/Compose 实现（Kyant0/AndroidLiquidGlass）：core:ui 直接使用 Modifier.drawBackdrop。
    implementation(project(":core:backdrop"))

    implementation(platform(libs.androidx.compose.bom))
    // 窗口内弹窗宿主需要 BackHandler（返回键关闭弹窗，与 DialogProperties.dismissOnBackPress 对齐）。
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.icons.extended)
    // RoundedPolygon + Morph：M3 Expressive 风格的形状形变加载指示器
    implementation(libs.androidx.graphics.shapes)
    // 全屏图片查看器（缩放 + 双击 + 超大图子采样）与位图解码：
    // 收敛到 core:ui，使 P 站、AI 预览等所有模块共用同一套查看器与编辑能力。
    implementation(libs.coil.compose)
    implementation(libs.zoomimage.compose.coil2)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.datastore.preferences)
    // SAF 目录写入：用户授权输出目录后，按 DocumentFile 逐级创建子目录与文件
    implementation(libs.androidx.documentfile)
}