package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.content.Intent
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject

class AppOpenTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.open_app"
    override val description: String = "按包名打开手机上的 App。适合打开微信、浏览器、地图等已安装应用。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("packageName", stringProperty("需要打开的 Android 应用包名，例如 com.tencent.mm。")),
        required = JSONArray().put("packageName"),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        val packageName = arguments.optString("packageName", "").trim()
        if (packageName.isBlank()) return errorText("missing packageName")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return errorText("未找到可启动应用：$packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(launchIntent)
            jsonTextResult(
                JSONObject()
                    .put("opened", true)
                    .put("packageName", packageName),
            )
        } catch (exception: Exception) {
            errorText("打开应用失败：${exception.message ?: exception::class.java.simpleName}")
        }
    }
}
