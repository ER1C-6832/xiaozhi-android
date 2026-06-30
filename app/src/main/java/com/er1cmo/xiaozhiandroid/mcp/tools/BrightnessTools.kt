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
    override val description: String = "获取系统屏幕亮度和自动亮度模式。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val resolver = context.contentResolver
        val brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        return jsonTextResult(
            JSONObject()
                .put("brightness", brightness)
                .put("percent", if (brightness >= 0) (brightness * 100.0 / 255).roundToInt() else JSONObject.NULL)
                .put("mode", if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "automatic" else "manual")
                .put("canWriteSettings", canWriteSettings()),
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
    override val description: String = "设置系统屏幕亮度百分比。Android 6+ 需要用户授权修改系统设置。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("percent", numberProperty("亮度百分比，0 到 100。"))
            .put(
                "mode",
                stringProperty(
                    description = "亮度模式：manual 手动，automatic 自动。默认 manual。",
                    enumValues = listOf("manual", "automatic"),
                ),
            )
            .put("openGrantPage", booleanProperty("没有修改系统设置权限时，是否打开授权页。默认 false。")),
        required = JSONArray().put("percent"),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        if (!canWriteSettings()) {
            if (arguments.optBoolean("openGrantPage", false)) {
                openWriteSettingsPage()
            }
            return errorText("缺少修改系统设置权限，请先授权 WRITE_SETTINGS；可调用 android.open_settings target=write_settings 打开授权页。")
        }

        val percent = arguments.optDouble("percent", 50.0).coerceIn(0.0, 100.0)
        val value = (255 * percent / 100.0).roundToInt().coerceIn(1, 255)
        val mode = arguments.optString("mode", "manual")
        val resolver = context.contentResolver

        return try {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (mode == "automatic") Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            jsonTextResult(
                JSONObject()
                    .put("brightness", value)
                    .put("percent", percent.roundToInt())
                    .put("mode", if (mode == "automatic") "automatic" else "manual"),
            )
        } catch (exception: Exception) {
            errorText("设置亮度失败：${exception.message ?: exception::class.java.simpleName}")
        }
    }

    private fun canWriteSettings(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)
    }

    private fun openWriteSettingsPage() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
