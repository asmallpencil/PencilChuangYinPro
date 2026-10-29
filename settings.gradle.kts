// FILE: settings.gradle.kts
pluginManagement {
    repositories {
        // 国内镜像优先（构建机网络对 Maven Central/GitHub 的 TLS 重定向不稳定）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // compose-settings (com.github.alorma) 托管在 JitPack
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

rootProject.name = "PencilChuangYinPro"

include(":app")
include(":core:resources")
include(":core:theme")
include(":core:ui")
include(":core:kyant-shapes")
include(":core:backdrop")
include(":core:printer")
include(":core:scanner")
include(":core:agent")
include(":core:proxy")
include(":feature:onboarding")
include(":feature:settings")
include(":feature:dashboard")
include(":feature:text")
include(":feature:image")
include(":feature:camera")
include(":feature:pixiv")
include(":feature:ai")
// Windows 桌面端（Compose Multiplatform / JVM），产出可运行的 exe
include(":desktopApp")
