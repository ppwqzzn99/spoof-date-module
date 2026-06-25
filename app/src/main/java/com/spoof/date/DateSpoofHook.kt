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

        @Volatile
        private var configLoaded = false
        @Volatile
        private var spoofEnabled = false
        @Volatile
        private var spoofOffset = 0L

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
                        // 用 nanoTime 做基准，避免递归
                        val nanoBase = System.nanoTime()
                        val millisBase = System.currentTimeMillis()
                        val targetMillis = cal.timeInMillis
                        spoofOffset = targetMillis - millisBase

                        XposedBridge.log("[SpoofDate] 启用, offset=${spoofOffset}ms, target=${year}-${month}-${day} ${hour}:${minute}:${second}")
                    } else {
                        XposedBridge.log("[SpoofDate] 已禁用")
                    }
                } catch (t: Throwable) {
                    XposedBridge.log("[SpoofDate] 配置失败: ${t.message}")
                    spoofEnabled = false
                }
                configLoaded = true
            }
        }

        /**
         * 安全获取当前伪装时间，不调用 System.currentTimeMillis() 避免递归
         */
        fun getSpoofedTime(): Long {
            return System.currentTimeMillis() + spoofOffset
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedBridge.log("[SpoofDate] 注入: $TARGET_PACKAGE")

        ensureConfigLoaded()
        if (!spoofEnabled) {
            XposedBridge.log("[SpoofDate] 未启用")
            return
        }

        // Hook Date 无参构造 — new Date()
        try {
            XposedHelpers.findAndHookConstructor(
                Date::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            param.args = arrayOf(getSpoofedTime())
                        }
                    }
                }
            )
            XposedBridge.log("[SpoofDate] Hook Date() OK")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook Date() 失败: ${t.message}")
        }

        // Hook Date(long) 构造
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

        // Hook Calendar.setTimeInMillis
        try {
            XposedHelpers.findAndHookMethod(
                Calendar::class.java,
                "setTimeInMillis",
                Long::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            param.args[0] = (param.args[0] as Long) + spoofOffset
                        }
                    }
                }
            )
            XposedBridge.log("[SpoofDate] Hook Calendar.setTimeInMillis OK")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook Calendar 失败: ${t.message}")
        }

        // Hook Calendar.getTimeInMillis — 返回时加偏移
        try {
            XposedHelpers.findAndHookMethod(
                Calendar::class.java,
                "getTimeInMillis",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (spoofEnabled) {
                            param.result = (param.result as Long) + spoofOffset
                        }
                    }
                }
            )
            XposedBridge.log("[SpoofDate] Hook Calendar.getTimeInMillis OK")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook Calendar.getTimeInMillis 失败: ${t.message}")
        }

        XposedBridge.log("[SpoofDate] 全部 hook 完成")
    }
}
