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
    override val description: String = "获取系统亮度诊断信息。当前设备无法通过公开 API 可靠读取系统亮度滑杆百分比时，拒绝返回猜测百分比。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val resolver = context.contentResolver
        val rawBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, UNKNOWN_INT_VALUE)
        val modeValue = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val mode = if (modeValue == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "automatic" else "manual"
        val settingsBrightnessFloat = readSettingsBrightnessFloat()

        return jsonTextResult(
            JSONObject()
                .put("mode", mode)
                .put("percent", JSONObject.NULL)
                .put("percentReliable", false)
                .put("percentStatus", "unsupported_on_this_device")
                .put(
                    "reason",
                    "当前设备/厂商系统没有通过 Android 公开 API 暴露可靠的用户可见亮度滑杆百分比；" +
                        "rawSystemBrightness 与系统亮度滑杆存在非线性映射，screen_brightness_float 也已验证不能稳定对应系统 UI。" +
                        "因此本工具拒绝返回猜测百分比。",
                )
                .put("canWriteSettings", canWriteSettings())
                .put(
                    "diagnostics",
                    JSONObject()
                        .put("settingsBrightnessFloat", settingsBrightnessFloat ?: JSONObject.NULL)
                        .put("rawSystemBrightness", if (rawBrightness >= 0) rawBrightness else JSONObject.NULL)
                        .put("rawSystemBrightnessRange", "OEM-dependent; do not convert to percent")
                        .put("rawLinearPercent", JSONObject.NULL)
                        .put("rawLinearPercentReliable", false),
                ),
        )
    }

    private fun readSettingsBrightnessFloat(): Float? {
        val value = runCatching {
            Settings.System.getFloat(context.contentResolver, SCREEN_BRIGHTNESS_FLOAT_KEY)
        }.getOrNull()
        return value?.takeIf { it >= 0.0f && it <= 1.0f }
    }

    private fun canWriteSettings(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
    }
}

class BrightnessSetTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.set_brightness"
    override val description: String = "尝试设置系统亮度。Android 6+ 的 WRITE_SETTINGS 是特殊权限，不能弹普通运行时权限框，缺少权限时会自动打开系统授权页。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("percent", numberProperty("请求设置的手动亮度百分比，0 到 100。mode=manual 时需要。注意：该值是请求值，不代表可可靠读取的系统滑杆真实值。"))
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
                        .put("requestedPercent", JSONObject.NULL)
                        .put("actualPercent", JSONObject.NULL)
                        .put("actualPercentReliable", false)
                        .put("verification", "not_available_public_api")
                        .put("note", "已请求开启自动亮度；实际亮度由系统根据环境光决定，本工具不回报不可靠百分比。"),
                )
            }

            if (!arguments.has("percent") || arguments.isNull("percent")) {
                return errorText("mode=manual 时必须提供 percent，范围 0 到 100。")
            }

            val requestedPercent = arguments.optDouble("percent").coerceIn(0.0, 100.0)
            val brightnessFloat = (requestedPercent / 100.0).toFloat().coerceIn(0.0f, 1.0f)
            val rawCompatibilityValue = (MAX_RAW_BRIGHTNESS_VALUE * requestedPercent / 100.0)
                .roundToInt()
                .coerceIn(MIN_RAW_BRIGHTNESS_VALUE, MAX_RAW_BRIGHTNESS_VALUE)

            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putFloat(resolver, SCREEN_BRIGHTNESS_FLOAT_KEY, brightnessFloat)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, rawCompatibilityValue)

            jsonTextResult(
                JSONObject()
                    .put("mode", "manual")
                    .put("requestedPercent", requestedPercent.roundToInt())
                    .put("actualPercent", JSONObject.NULL)
                    .put("actualPercentReliable", false)
                    .put("verification", "not_available_public_api")
                    .put("writtenSettingsBrightnessFloat", brightnessFloat)
                    .put("writtenRawCompatibilityBrightness", rawCompatibilityValue)
                    .put(
                        "note",
                        "已尝试写入请求亮度，但当前设备的公开 API 不能可靠读取用户可见亮度滑杆真实百分比；" +
                            "后续 get_brightness 不会返回猜测百分比。请以屏幕实际观感或系统设置界面为准。",
                    ),
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

private const val SCREEN_BRIGHTNESS_FLOAT_KEY = "screen_brightness_float"
private const val UNKNOWN_INT_VALUE = -1
private const val MIN_RAW_BRIGHTNESS_VALUE = 1
private const val MAX_RAW_BRIGHTNESS_VALUE = 255
