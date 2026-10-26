// FILE: core/backdrop/build.gradle.kts
// 液态玻璃 Kotlin/Compose 实现（vendor 自开源项目 Kyant0/AndroidLiquidGlass，Apache-2.0）
// 原库为 Kotlin Multiplatform 模块，此处仅保留 Android 目标并合并 expect/actual。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kyant.backdrop"
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
        // 上游 Kyant0/AndroidLiquidGlass 使用 Kotlin 上下文参数（context parameters），需显式开启
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    // Kyant0 的形状库（液态玻璃透镜折射依赖 RoundedRectangularShape）
    implementation(project(":core:kyant-shapes"))
    implementation(libs.jetbrains.annotations)
}
