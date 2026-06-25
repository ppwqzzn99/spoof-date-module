package com.spoof.date

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.Calendar
import java.util.Date

class DateSpoofHook : IXposedHookLoadPackage {

    companion object {
        const val TARGET_PACKAGE = "com.zyyad.game"
        const val PREFS_NAME = "spoof_date_prefs"

        // 配置加载标志
        @Volatile
        private var configLoaded = false
        @Volatile
        private var spoofEnabled = false
        @Volatile
        private var spoofOffset = 0L

        /**
         * 启动时加载一次配置，后续使用缓存
         */
        private fun ensureConfigLoaded() {
            if (configLoaded) return
            synchronized(this) {
                if (configLoaded) return
                try {
                    val prefs = XSharedPreferences("com.spoof.date", PREFS_NAME)
                    spoofEnabled = prefs.getBoolean("enabled", true)
                    if (spoofEnabled) {
                        val year = prefs.getString("spoof_year", "2025")?.toIntOrNull() ?: 2025
                        val month = prefs.getString("spoof_month", "1")?.toIntOrNull() ?: 1
                        val day = prefs.getString("spoof_day", "1")?.toIntOrNull() ?: 1
                        val hour = prefs.getString("spoof_hour", "12")?.toIntOrNull() ?: 12
                        val minute = prefs.getString("spoof_minute", "0")?.toIntOrNull() ?: 0
                        val second = prefs.getString("spoof_second", "0")?.toIntOrNull() ?: 0

                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month - 1)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, second)
                            set(Calendar.MILLISECOND, 0)
                        }
                        spoofOffset = cal.timeInMillis - System.currentTimeMillis()
                        XposedBridge.log("[SpoofDate] 伪装已启用, 偏移量=${spoofOffset}ms")
                    } else {
                        XposedBridge.log("[SpoofDate] 伪装已禁用")
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("[SpoofDate] 配置加载失败: ${t.message}")
                    spoofEnabled = false
                }
                configLoaded = true
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedBridge.log("[SpoofDate] 已注入: $TARGET_PACKAGE")

        // 加载配置
        ensureConfigLoaded()
        if (!spoofEnabled) {
            XposedBridge.log("[SpoofDate] 未启用，跳过 hook")
            return
        }

        // 只 hook System.currentTimeMillis，这是最底层的
        // 大多数时间 API 最终都调用它
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System",
                lpparam.classLoader,
                "currentTimeMillis",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            param.result = System.currentTimeMillis() + spoofOffset
                        }
                    }
                }
            )
            XposedBridge.log("[SpoofDate] Hook System.currentTimeMillis OK")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook System.currentTimeMillis 失败: ${t.message}")
        }

        // Hook Date 构造函数
        try {
            XposedHelpers.findAndHookConstructor(
                Date::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            param.args = arrayOf(System.currentTimeMillis() + spoofOffset)
                        }
                    }
                }
            )
            XposedBridge.log("[SpoofDate] Hook Date() OK")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook Date() 失败: ${t.message}")
        }

        // Hook Date(long)
        try {
            XposedHelpers.findAndHookConstructor(
                Date::class.java,
                Long::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            param.args[0] = (param.args[0] as Long) + spoofOffset
                        }
                    }
                }
            )
            XposedBridge.log("[SpoofDate] Hook Date(long) OK")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook Date(long) 失败: ${t.message}")
        }

        XposedBridge.log("[SpoofDate] 所有 hook 安装完成")
    }
}
