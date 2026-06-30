package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class BrightnessGetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.get_brightness"
    override val description: String = "获取系统亮度设置。自动亮度或 OEM 非标准亮度范围下，不返回不可靠的百分比。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val resolver = context.contentResolver
        val rawBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val modeValue = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val mode = if (modeValue == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "automatic" else "manual"
        val isRawInAospRange = rawBrightness in MIN_BRIGHTNESS_VALUE..MAX_BRIGHTNESS_VALUE
        val percent = if (mode == "manual" && isRawInAospRange) {
            (rawBrightness * 100.0 / MAX_BRIGHTNESS_VALUE).roundToInt()
        } else {
            null
        }
        val note = when {
            mode == "automatic" -> "当前处于自动亮度模式，系统设置值不等于屏幕此刻实际亮度，因此 percent 返回 null，避免乱报。"
            rawBrightness < 0 -> "无法读取系统亮度设置值。"
            !isRawInAospRange -> "当前设备亮度原始值不在 Android 标准 0..255 范围内，可能是厂商自定义范围，因此 percent 返回 null。"
            else -> "percent 为基于系统手动亮度设置值计算的百分比。"
        }

        return jsonTextResult(
            JSONObject()
                .put("mode", mode)
                .put("rawSystemBrightness", rawBrightness)
                .put("rawRange", "0..255 when supported by device")
                .put("percent", percent ?: JSONObject.NULL)
                .put("percentReliable", percent != null)
                .put("canWriteSettings", canWriteSettings())
                .put("note", note),
        )
    }

    private fun canWriteSettings(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
    }
}

class BrightnessSetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.set_brightness"
    override val description: String = "设置系统亮度。Android 6+ 的 WRITE_SETTINGS 是特殊权限，不能弹普通运行时权限框，缺少权限时会自动打开系统授权页。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("percent", numberProperty("手动亮度百分比，0 到 100。mode=manual 时需要。"))
            .put(
                "mode",
                stringProperty(
                    description = "亮度模式：manual 手动，automatic 自动。默认 manual。",
                    enumValues = listOf("manual", "automatic"),
                ),
            )
            .put("openGrantPage", booleanProperty("没有修改系统设置权限时是否打开授权页。默认 true。")),
        required = JSONArray(),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        if (!canWriteSettings()) {
            val shouldOpenGrantPage = arguments.optBoolean("openGrantPage", true)
            val opened = if (shouldOpenGrantPage) openWriteSettingsPage() else false
            return errorText(
                if (opened) {
                    "缺少 WRITE_SETTINGS 特殊权限，已打开修改系统设置授权页；授权后请再次调用 android.set_brightness。"
                } else {
                    "缺少 WRITE_SETTINGS 特殊权限，无法直接弹窗授权；请调用 android.open_settings target=write_settings 或在系统设置中允许修改系统设置。"
                },
            )
        }

        val mode = arguments.optString("mode", "manual").ifBlank { "manual" }
        val resolver = context.contentResolver

        return try {
            if (mode == "automatic") {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                )
                return jsonTextResult(
                    JSONObject()
                        .put("mode", "automatic")
                        .put("percent", JSONObject.NULL)
                        .put("note", "已开启自动亮度，实际亮度由系统根据环境光决定，不回报不可靠百分比。"),
                )
            }

            if (!arguments.has("percent") || arguments.isNull("percent")) {
                return errorText("mode=manual 时必须提供 percent，范围 0 到 100。")
            }

            val percent = arguments.optDouble("percent").coerceIn(0.0, 100.0)
            val value = (MAX_BRIGHTNESS_VALUE * percent / 100.0)
                .roundToInt()
                .coerceIn(MIN_BRIGHTNESS_VALUE, MAX_BRIGHTNESS_VALUE)

            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            jsonTextResult(
                JSONObject()
                    .put("mode", "manual")
                    .put("requestedPercent", percent.roundToInt())
                    .put("writtenRawSystemBrightness", value)
                    .put("note", "已写入系统手动亮度设置。部分厂商系统可能仍会由系统 UI 或自动亮度策略二次调整。"),
            )
        } catch (exception: SecurityException) {
            errorText("设置亮度失败：WRITE_SETTINGS 权限不可用，请重新授权后重试。")
        } catch (exception: Exception) {
            errorText("设置亮度失败：${exception.message ?: exception::class.java.simpleName}")
        }
    }

    private fun canWriteSettings(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
    }

    private fun openWriteSettingsPage(): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

private const val MIN_BRIGHTNESS_VALUE = 1
private const val MAX_BRIGHTNESS_VALUE = 255
