package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject

class OpenSettingsTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.open_settings"
    override val description: String = "打开常用 Android 系统设置页，例如 Wi-Fi、蓝牙、应用详情、亮度写入授权、通知策略授权。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put(
                "target",
                stringProperty(
                    description = "设置页：wifi、bluetooth、app、write_settings、notification_policy、display、sound、accessibility。默认 app。",
                    enumValues = listOf(
                        "wifi",
                        "bluetooth",
                        "app",
                        "write_settings",
                        "notification_policy",
                        "display",
                        "sound",
                        "accessibility",
                    ),
                ),
            ),
        required = JSONArray(),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        val target = arguments.optString("target", "app").ifBlank { "app" }
        val intent = when (target) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "write_settings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            "notification_policy" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            jsonTextResult(
                JSONObject()
                    .put("opened", true)
                    .put("target", target),
            )
        } catch (exception: Exception) {
            errorText("打开设置失败：${exception.message ?: exception::class.java.simpleName}")
        }
    }
}
