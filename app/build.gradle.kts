plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pencil.chuangyin.pro"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.pencil.chuangyin.pro"
        minSdk = 24
        targetSdk = 35
        versionCode = 2000006
        versionName = "2.0.0.6"

        // OpenCV 仅打包真机 ARM 架构，控制包体。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
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