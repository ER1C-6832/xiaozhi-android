package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.Context
import android.os.Build
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import org.json.JSONArray
import org.json.JSONObject

class DeviceInfoTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.get_device_info"
    override val description: String = "获取 Android 设备和当前 App 的基础信息，不包含隐私标识。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        return jsonTextResult(
            JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("brand", Build.BRAND)
                .put("model", Build.MODEL)
                .put("device", Build.DEVICE)
                .put("product", Build.PRODUCT)
                .put("hardware", Build.HARDWARE)
                .put("androidRelease", Build.VERSION.RELEASE)
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("appPackage", context.packageName)
                .put("appVersionName", packageInfo?.versionName ?: "unknown")
                .put(
                    "appVersionCode",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo?.longVersionCode ?: 0L
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo?.versionCode ?: 0
                    },
                ),
        )
    }
}
