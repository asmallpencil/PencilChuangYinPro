// FILE: feature/scanner/build.gradle.kts
// 文稿扫描（FairScan 开源文稿扫描器，GPL-3.0）。
// 本模块近乎原样内嵌 FairScan v2.3.0 的 UI 与识别管线（LiteRT 分割 + OpenCV 透视校正），
// 仅做三处改动：去掉保存/导出设置、保存按钮改为打印、打印后带成品图回到图片打印页。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // FairScan 源码使用 org.fairscan.app.R 资源引用，保持其原始包名与命名空间。
    namespace = "org.fairscan.app"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
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

configurations.configureEach {
    // 与 feature:camera 相同：CameraX 需要真实的 ListenableFuture 接口。
    resolutionStrategy.force("com.google.guava:listenablefuture:1.0")
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:theme"))
    implementation(project(":core:ui"))
    implementation(project(":core:printer"))
    // FairScan 图像处理核心（GPL-3.0，见 third_party/fairscan-imageprocessing）。
    implementation(project(":third_party:fairscan-imageprocessing"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)

    // FairScan 原始依赖：LiteRT 分割模型、缩放/拖拽排序、不可变集合、DataStore。
    implementation(libs.litert)
    implementation(libs.litert.support)
    implementation(libs.litert.metadata)
    implementation(libs.zoomable)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}
