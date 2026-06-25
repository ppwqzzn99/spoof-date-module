package com.spoof.date

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val PREFS_NAME = "spoof_date_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val dp = resources.displayMetrics.density
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }

        // 标题
        layout.addView(TextView(this).apply {
            text = "SpoofDate 模块设置"
            textSize = 22f
            setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
        })

        // 启用开关
        val switchEnabled = Switch(this).apply {
            text = "启用伪装"
            isChecked = prefs.getBoolean("enabled", true)
        }
        layout.addView(switchEnabled)

        // 日期输入
        val fields = listOf(
            "spoof_year" to "年 (如 2025)",
            "spoof_month" to "月 (1-12)",
            "spoof_day" to "日 (1-31)",
            "spoof_hour" to "时 (0-23)",
            "spoof_minute" to "分 (0-59)",
            "spoof_second" to "秒 (0-59)"
        )

        val editTexts = mutableMapOf<String, EditText>()

        for ((key, hint) in fields) {
            layout.addView(TextView(this).apply {
                text = hint
                textSize = 14f
                setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
            })

            val et = EditText(this).apply {
                setText(prefs.getString(key, when (key) {
                    "spoof_year" -> "2025"
                    "spoof_month" -> "1"
                    "spoof_day" -> "1"
                    "spoof_hour" -> "12"
                    "spoof_minute" -> "0"
                    "spoof_second" -> "0"
                    else -> ""
                }))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            layout.addView(et)
            editTexts[key] = et
        }

        // 保存按钮
        layout.addView(Button(this).apply {
            text = "保存"
            setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            setOnClickListener {
                val editor = prefs.edit()
                editor.putBoolean("enabled", switchEnabled.isChecked)
                for ((key, et) in editTexts) {
                    editor.putString(key, et.text.toString())
                }
                editor.apply()
                Toast.makeText(this@SettingsActivity, "已保存，重启目标应用生效", Toast.LENGTH_SHORT).show()
            }
        })

        // 提示
        layout.addView(TextView(this).apply {
            text = "目标包名: com.zyyad.game\n修改后需重启目标应用生效"
            textSize = 12f
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        })

        setContentView(layout)
    }
}
