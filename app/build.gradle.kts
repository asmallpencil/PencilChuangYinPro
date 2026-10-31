plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 发布三套安装包：ABI 拆分后一次构建即产出三个 APK ——
// 32 位（armeabi-v7a）、64 位（arm64-v8a）与通用包（两种架构都含）。
// 通用包体积大但兼容所有设备；单架构包更小，按设备架构分发可显著节省流量。
// 产物位于 app/build/outputs/apk/debug（或 release）下，文件名带架构后缀。

android {
    namespace = "com.pencil.chuangyin.pro"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.pencil.chuangyin.pro"
        minSdk = 24
        targetSdk = 35
        versionCode = 2000009
        versionName = "2.0.0.9"

        // OpenCV 与内置代理内核均只打包真机 ARM 架构，控制包体。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    // 按 ABI 拆分 + 额外产出通用包：三套 APK 由同一次构建生成。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    packaging {
        // 内置的 sing-box 内核以可执行文件形式放在 jniLibs 里：必须解压到 nativeLibraryDir
        // 才能 exec（Android 不允许执行应用私有目录里的文件），因此开启传统打包模式。
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libsingbox.so"
        }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Google ADK 依赖链（google-genai / google-auth / api-common）多个 jar 携带同一索引文件
        resources.excludes += setOf(
            "META-INF/INDEX.LIST",
            "META-INF/DEPENDENCIES",
            "META-INF/NOTICE",
            "META-INF/NOTICE.md",
            "META-INF/LICENSE",
            "META-INF/LICENSE.md",
        )
    }
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
    implementation(project(":core:scanner"))
    implementation(project(":core:agent"))
    implementation(project(":core:proxy"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:text"))
    implementation(project(":feature:image"))
    implementation(project(":feature:camera"))
    implementation(project(":feature:scanner"))
    implementation(project(":feature:pixiv"))
    implementation(project(":feature:ai"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.icons.extended)

    implementation(libs.androidx.core.ktx)
    // Baseline Profile：Compose / Activity 等依赖自带的 baseline-prof.txt 由 AGP 自动合并，
    // 该库负责在 API 24~30 上于安装后编译这些 profile，让首帧与滚动直接命中 AOT。
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // P站「直接结合」：宿主 Activity 继承 FragmentActivity，以 FragmentContainerView
    // 承载 Pix-EzViewer 的 MainHostFragment。
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    // MainHostFragment 的公开超类型含 Material Components 的
    // NavigationView.OnNavigationItemSelectedListener，编译期需在 classpath 可见。
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.accompanist.permissions)
}