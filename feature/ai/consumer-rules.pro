# feature:ai —— 消费者混淆规则
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pencil.chuangyin.feature.ai.**$$serializer { *; }
-keepclasseswithmembers class com.pencil.chuangyin.feature.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}
