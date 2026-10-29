plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pencil.chuangyin.feature.camera"
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

configurations.configureEach {
    // google-adk 会把 com.google.guava:listenablefuture 约束到 9999.0 的空壳包，
    // 而 CameraX 的 ProcessCameraProvider 依赖真实的 ListenableFuture 接口。
    // 强制回真正的 1.0 接口包，否则 CameraPreview 无法解析该类型。
    resolutionStrategy.force("com.google.guava:listenablefuture:1.0")
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:theme"))
    implementation(project(":core:ui"))
    implementation(project(":core:scanner"))
    implementation(project(":core:printer"))
    // AI 图片处理：复用 AI 打印的模型配置与实时对话客户端（不做聊天，只做一轮决策）
    implementation(project(":core:agent"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.accompanist.permissions)
}
