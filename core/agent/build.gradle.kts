// FILE: core/agent/build.gradle.kts
// core:agent —— OpenAI 兼容的 Agent 运行时内核（网络 SSE 客户端、Agent 循环、工具协议、上下文折叠、模型配置仓库）
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pencil.chuangyin.core.agent"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Google ADK for Kotlin 核心（@Tool/@Param 注解、FunctionTool、ToolContext）
    // 以 api 暴露：工具集类（feature:ai）使用官方注解并由官方 KSP processor 生成 FunctionTool，
    // 适配器层在 core:agent 内消费这些类型。
    api(libs.google.adk.core)
    // 流式 HTTP（SSE）
    implementation(libs.okhttp)
    // 请求/响应与工具参数 JSON
    implementation(libs.kotlinx.serialization.json)
    // 模型配置持久化
    implementation(libs.androidx.datastore.preferences)
    // 协程
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
