package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Display
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
    override val description: String = "获取系统亮度设置。优先读取现代 Android 的屏幕亮度 float 值；无法可靠换算时不会乱报百分比。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val resolver = context.contentResolver
        val rawBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val rawLinearPercent = rawBrightnessPercentOrNull(rawBrightness)
        val modeValue = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val mode = if (modeValue == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "automatic" else "manual"

        val settingsFloat = readSettingsBrightnessFloat()
        val displayReading = readDisplayBrightnessInfo()
        val selectedPercent = when {
            mode == "automatic" -> null
            settingsFloat != null -> settingsFloat.percent
            displayReading != null -> displayReading.percent
            else -> null
        }
        val selectedSource = when {
            mode == "automatic" -> "unavailable"
            settingsFloat != null -> "settings_system_float"
            displayReading != null -> "display_brightness_info"
            else -> "unavailable"
        }
        val note = when {
            mode == "automatic" -> "当前处于自动亮度模式，系统亮度会随环境光变化；为避免误导，percent 返回 null。"
            settingsFloat != null -> "percent 来源于 Android 现代亮度 float 设置值，通常与系统亮度滑杆更一致。rawSystemBrightness 仅作诊断，不再用它推断 UI 百分比。"
            displayReading != null -> "percent 来源于 Display.BrightnessInfo 当前显示亮度；rawSystemBrightness 仅作诊断，不再用它推断 UI 百分比。"
            else -> "当前设备没有暴露可靠亮度百分比来源，因此 percent 返回 null；rawSystemBrightness 仅作诊断。"
        }

        return jsonTextResult(
            JSONObject()
                .put("mode", mode)
                .put("percent", selectedPercent ?: JSONObject.NULL)
                .put("percentReliable", selectedPercent != null)
                .put("percentSource", selectedSource)
                .put("settingsBrightnessFloat", settingsFloat?.value ?: JSONObject.NULL)
                .put("displayBrightness", displayReading?.brightness ?: JSONObject.NULL)
                .put("displayBrightnessMin", displayReading?.min ?: JSONObject.NULL)
                .put("displayBrightnessMax", displayReading?.max ?: JSONObject.NULL)
                .put("displayAdjustedBrightness", displayReading?.adjustedBrightness ?: JSONObject.NULL)
                .put("rawSystemBrightness", rawBrightness)
                .put("rawSystemBrightnessRange", "0..255 compatibility value")
                .put("rawLinearPercent", rawLinearPercent ?: JSONObject.NULL)
                .put("rawLinearPercentReliable", false)
                .put("canWriteSettings", canWriteSettings())
                .put("note", note),
        )
    }

    private fun readSettingsBrightnessFloat(): SettingsFloatBrightness? {
        val value = Settings.System.getFloat(
            context.contentResolver,
            SCREEN_BRIGHTNESS_FLOAT_KEY,
            Float.NaN,
        )
        if (!value.isUsableFinite() || value !in 0.0f..1.0f) return null
        return SettingsFloatBrightness(
            value = value,
            percent = (value * 100.0f).roundToInt().coerceIn(0, 100),
        )
    }

    private fun readDisplayBrightnessInfo(): DisplayBrightnessReading? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val info = display.brightnessInfo ?: return null
        val brightness = info.brightness
        val min = info.brightnessMinimum
        val max = info.brightnessMaximum
        if (!brightness.isUsableFinite() || !min.isUsableFinite() || !max.isUsableFinite() || max <= min) return null
        val percent = (((brightness - min) * 100.0f) / (max - min))
            .roundToInt()
            .coerceIn(0, 100)
        val adjusted = info.adjustedBrightness.takeIf { it.isUsableFinite() }
        return DisplayBrightnessReading(
            brightness = brightness,
            min = min,
            max = max,
            adjustedBrightness = adjusted,
            percent = percent,
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
            val normalized = (percent / 100.0).toFloat().coerceIn(0.0f, 1.0f)
            val compatibilityRaw = (MAX_BRIGHTNESS_VALUE * percent / 100.0)
                .roundToInt()
                .coerceIn(MIN_BRIGHTNESS_VALUE, MAX_BRIGHTNESS_VALUE)

            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val wroteFloat = Settings.System.putFloat(
                resolver,
                SCREEN_BRIGHTNESS_FLOAT_KEY,
                normalized,
            )
            val wroteRaw = Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                compatibilityRaw,
            )

            jsonTextResult(
                JSONObject()
                    .put("mode", "manual")
                    .put("requestedPercent", percent.roundToInt())
                    .put("writtenSettingsBrightnessFloat", normalized)
                    .put("wroteSettingsBrightnessFloat", wroteFloat)
                    .put("writtenRawSystemBrightness", compatibilityRaw)
                    .put("wroteRawSystemBrightness", wroteRaw)
                    .put("note", "已优先写入 screen_brightness_float，通常更接近系统亮度滑杆；rawSystemBrightness 仅作兼容写入。"),
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

private data class SettingsFloatBrightness(
    val value: Float,
    val percent: Int,
)

private data class DisplayBrightnessReading(
    val brightness: Float,
    val min: Float,
    val max: Float,
    val adjustedBrightness: Float?,
    val percent: Int,
)

private fun rawBrightnessPercentOrNull(rawBrightness: Int): Int? {
    if (rawBrightness !in MIN_BRIGHTNESS_VALUE..MAX_BRIGHTNESS_VALUE) return null
    return (rawBrightness * 100.0 / MAX_BRIGHTNESS_VALUE).roundToInt().coerceIn(0, 100)
}

private fun Float.isUsableFinite(): Boolean = !isNaN() && !isInfinite()

private const val SCREEN_BRIGHTNESS_FLOAT_KEY = "screen_brightness_float"
private const val MIN_BRIGHTNESS_VALUE = 1
private const val MAX_BRIGHTNESS_VALUE = 255
