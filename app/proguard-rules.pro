# 保留 Xposed 入口类
-keep class com.spoof.date.DateSpoofHook { *; }
-keep class com.spoof.date.BuildConfig { *; }

# 不混淆 Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
