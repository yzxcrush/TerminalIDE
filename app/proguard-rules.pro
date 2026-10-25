# Chaquopy（嵌入式 Python 反射/动态代理所需）
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# 本项目 JNI 桥接
-keep class com.terminalide.** { *; }

# OkHttp（DeepSeek API 客户端）
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# security-crypto 所需的注解
-dontwarn androidx.security.**
