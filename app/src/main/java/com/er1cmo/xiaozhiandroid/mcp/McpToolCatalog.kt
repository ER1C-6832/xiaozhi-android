package com.er1cmo.xiaozhiandroid.mcp

import com.er1cmo.xiaozhiandroid.domain.McpToolListItemUiState
import org.json.JSONObject

object McpToolCatalog {
    /**
     * Current Android native tools are treated as normal user-facing controls.
     *
     * The confirmation mechanism is intentionally kept in AndroidMcpServer and
     * decorateToolJson(); future tools that can delete data, write files,
     * uninstall apps, send messages, make payments, or perform other destructive
     * actions can be added to this set to require confirmed=true.
     */
    private val highRiskToolNames = emptySet<String>()

    private val categoryByTool = mapOf(
        "android.ping" to "测试",
        "android.echo" to "测试",
        "android.get_device_info" to "设备",
        "android.get_battery_status" to "设备",
        "android.get_network_status" to "设备",
        "android.get_current_time" to "系统",
        "android.get_volume" to "音频",
        "android.set_volume" to "音频",
        "android.get_ringer_mode" to "系统",
        "android.set_ringer_mode" to "系统",
        "android.open_app" to "应用",
        "android.get_brightness" to "屏幕",
        "android.set_brightness" to "屏幕",
        "android.set_flashlight" to "硬件",
        "android.set_clipboard_text" to "剪贴板",
        "android.open_settings" to "系统设置",
    )

    private val permissionHintByTool = mapOf(
        "android.set_flashlight" to "需要 CAMERA 权限；未授权时会拉起授权页。",
        "android.set_brightness" to "需要 WRITE_SETTINGS 特殊授权；只能打开系统授权页。",
        "android.open_settings" to "会打开系统设置页。",
        "android.open_app" to "会跳转到其他应用。",
        "android.set_clipboard_text" to "会写入系统剪贴板。",
        "android.set_volume" to "会修改系统媒体音量。",
        "android.set_ringer_mode" to "会修改铃声模式，静音/勿扰受系统限制。",
    )

    fun requiresConfirmation(toolName: String): Boolean = toolName in highRiskToolNames

    fun uiInfo(toolName: String): McpToolListItemUiState {
        return McpToolListItemUiState(
            name = toolName,
            category = categoryByTool[toolName] ?: "其他",
            riskLevel = if (requiresConfirmation(toolName)) "高风险：需二次确认" else "普通工具",
            permissionHint = permissionHintByTool[toolName] ?: "无特殊权限要求。",
            enabled = true,
        )
    }

    fun decorateToolJson(toolName: String, toolJson: JSONObject): JSONObject {
        if (!requiresConfirmation(toolName)) return toolJson
        val inputSchema = toolJson.optJSONObject("inputSchema") ?: JSONObject().put("type", "object")
        val properties = inputSchema.optJSONObject("properties") ?: JSONObject()
        properties.put(
            "confirmed",
            JSONObject()
                .put("type", "boolean")
                .put("description", "高风险 Android 本机工具二次确认。用户明确确认后传 true。"),
        )
        inputSchema.put("properties", properties)
        inputSchema.put("additionalProperties", false)
        toolJson.put("inputSchema", inputSchema)
        toolJson.put("x-android-risk", "high")
        toolJson.put("x-android-confirmation", "required")
        return toolJson
    }
}
