# core:agent —— 消费者混淆规则
# kotlinx.serialization 生成的序列化器通过反射查找，需保留。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pencil.chuangyin.core.agent.**$$serializer { *; }
-keepclasseswithmembers class com.pencil.chuangyin.core.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
