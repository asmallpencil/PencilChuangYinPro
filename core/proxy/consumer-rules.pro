# core:proxy 消费方混淆规则
# 配置模型使用 kotlinx.serialization 生成的序列化器，保留序列化元数据即可。
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.pencil.chuangyin.core.proxy.** {
    *** Companion;
}
-keepclasseswithmembers class com.pencil.chuangyin.core.proxy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
