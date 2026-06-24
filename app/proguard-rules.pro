# Xposed API - 不要混淆Xposed相关类
-keep class de.robv.android.xposed.** { *; }
-keep class com.houvven.guisev2.xposed.** { *; }
-keepclassmembers class com.houvven.guisev2.xposed.** { *; }

# 保留ModuleConfig数据类的序列化
-keep class com.houvven.guisev2.xposed.config.ModuleConfig { *; }
-keepclassmembers class com.houvven.guisev2.xposed.config.ModuleConfig {
    public <fields>;
    public <methods>;
}

# Gson 序列化
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 百度地图SDK
-keep class com.baidu.** { *; }
-keep class vi.com.** { *; }
-dontwarn com.baidu.**

# AndroidX / Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# 保留Application入口
-keep class com.houvven.guisev2.MainActivity { *; }

# 保留所有public API
-keepclassmembers class * {
    public <methods>;
    public <fields>;
}

# 移除Log调用（Release构建时）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}