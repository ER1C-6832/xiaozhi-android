package com.er1cmo.xiaozhiandroid.mcp.tools

import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import org.json.JSONObject

class AndroidPingTool : McpTool {
    override val name: String = "android.ping"
    override val description: String = "测试 Android 本机 MCP 通道是否可用。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        return McpToolCallResult.text("pong from xiaozhi-android")
    }
}
