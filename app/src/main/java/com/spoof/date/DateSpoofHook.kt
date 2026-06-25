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

        // 缓存伪装时间戳，避免重复计算
        @Volatile
        private var cachedSpoofTime: Long = 0L
        @Volatile
        private var cachedRealBase: Long = 0L
        @Volatile
        private var cachedOffset: Long = 0L

        /**
         * 读取 XSharedPreferences 配置
         */
        private fun loadConfig(): Triple<Boolean, Long, Long> {
            return try {
                val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_NAME)
                prefs.makeWorldReadable()

                val enabled = prefs.getBoolean("enabled", true)
                if (!enabled) return Triple(false, 0L, 0L)

                val year = prefs.getString("spoof_year", "2025")?.toIntOrNull() ?: 2025
                val month = prefs.getString("spoof_month", "1")?.toIntOrNull() ?: 1
                val day = prefs.getString("spoof_day", "1")?.toIntOrNull() ?: 1
                val hour = prefs.getString("spoof_hour", "12")?.toIntOrNull() ?: 12
                val minute = prefs.getString("spoof_minute", "0")?.toIntOrNull() ?: 0
                val second = prefs.getString("spoof_second", "0")?.toIntOrNull() ?: 0

                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1) // Calendar.MONTH 从 0 开始
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, second)
                    set(Calendar.MILLISECOND, 0)
                }

                val targetTime = cal.timeInMillis
                val realNow = System.currentTimeMillis()
                val offset = targetTime - realNow

                // 缓存
                cachedSpoofTime = targetTime
                cachedRealBase = realNow
                cachedOffset = offset

                Triple(true, targetTime, offset)
            } catch (t: Throwable) {
                XposedBridge.log("[SpoofDate] 读取配置失败: ${t.message}")
                Triple(false, 0L, 0L)
            }
        }

        /**
         * 获取伪装后的时间戳
         */
        fun getSpoofedMillis(): Long {
            val (enabled, _, offset) = loadConfig()
            if (!enabled) return System.currentTimeMillis()
            return System.currentTimeMillis() + offset
        }

        /**
         * 读取配置并刷新缓存
         */
        fun refreshAndGetConfig(): Triple<Boolean, Long, Long> {
            return loadConfig()
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedBridge.log("[SpoofDate] 已注入目标应用: $TARGET_PACKAGE")

        try {
            hookSystemCurrentTimeMillis(lpparam)
            hookDateConstructor(lpparam)
            hookCalendarGetInstance(lpparam)
            hookSystemClock(lpparam)
            hookSimpleDateFormat(lpparam)
            XposedBridge.log("[SpoofDate] 所有 hook 已安装完成")
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] Hook 安装失败: ${t.message}")
        }
    }

    /**
     * Hook System.currentTimeMillis()
     * 这是最核心的 hook，大部分时间 API 最终都调用它
     */
    private fun hookSystemCurrentTimeMillis(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "java.lang.System",
            lpparam.classLoader,
            "currentTimeMillis",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val (enabled, _, _) = loadConfig()
                    if (enabled) {
                        param.result = getSpoofedMillis()
                    }
                }
            }
        )
        XposedBridge.log("[SpoofDate] Hook: System.currentTimeMillis()")
    }

    /**
     * Hook java.util.Date 构造函数
     */
    private fun hookDateConstructor(lpparam: XC_LoadPackage.LoadPackageParam) {
        // new Date() — 无参构造
        XposedHelpers.findAndHookConstructor(
            Date::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val (enabled, _, _) = loadConfig()
                    if (enabled) {
                        // 替换为带时间戳的构造
                        param.args = arrayOf(getSpoofedMillis())
                    }
                }
            }
        )

        // new Date(long millis)
        XposedHelpers.findAndHookConstructor(
            Date::class.java,
            Long::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val (enabled, _, offset) = loadConfig()
                    if (enabled) {
                        val originalMillis = param.args[0] as Long
                        param.args[0] = originalMillis + offset
                    }
                }
            }
        )

        XposedBridge.log("[SpoofDate] Hook: Date constructors")
    }

    /**
     * Hook Calendar.getInstance()
     */
    private fun hookCalendarGetInstance(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 返回后修改 Calendar 内部时间
        XposedHelpers.findAndHookMethod(
            Calendar::class.java,
            "setTimeInMillis",
            Long::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val (enabled, _, offset) = loadConfig()
                    if (enabled) {
                        val originalMillis = param.args[0] as Long
                        param.args[0] = originalMillis + offset
                    }
                }
            }
        )

        // Calendar.getTime() 返回 Date，已被 Date hook 覆盖
        // 额外 hook getTimeInMillis
        XposedHelpers.findAndHookMethod(
            Calendar::class.java,
            "getTimeInMillis",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val (enabled, _, offset) = loadConfig()
                    if (enabled) {
                        val original = param.result as Long
                        param.result = original + offset
                    }
                }
            }
        )

        XposedBridge.log("[SpoofDate] Hook: Calendar")
    }

    /**
     * Hook SystemClock（部分应用用它获取时间）
     */
    private fun hookSystemClock(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemClock",
                lpparam.classLoader,
                "currentThreadTimeMillis",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val (enabled, _, _) = loadConfig()
                        if (enabled) {
                            param.result = getSpoofedMillis()
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[SpoofDate] SystemClock hook 跳过 (非关键): ${t.message}")
        }
    }

    /**
     * Hook SimpleDateFormat.format() — 拦截格式化输出
     * 确保通过 format() 得到的字符串也是伪装后的
     */
    private fun hookSimpleDateFormat(lpparam: XC_LoadPackage.LoadPackageParam) {
        // SimpleDateFormat.format(Date) — Date 已被 hook，这里无需额外处理
        // 但有些应用直接传 long 给 format
        XposedHelpers.findAndHookMethod(
            "java.text.SimpleDateFormat",
            lpparam.classLoader,
            "format",
            java.util.Date::class.java,
            object : XC_MethodHook() {
                // Date 对象已经包含伪装时间，无需修改
            }
        )

        XposedBridge.log("[SpoofDate] Hook: SimpleDateFormat")
    }
}
