package com.er1cmo.xiaozhiandroid.mcp.tools

import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject

class AndroidEchoTool : McpTool {
    override val name: String = "android.echo"
    override val description: String = "回显传入文本，用于验证 tools/call 参数解析与返回链路。"
    override val inputSchema: JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put(
                    "text",
                    JSONObject()
                        .put("type", "string")
                        .put("description", "需要回显的文本。"),
                ),
        )
        .put("required", JSONArray().put("text"))
        .put("additionalProperties", false)

    override fun call(arguments: JSONObject): McpToolCallResult {
        val text = arguments.optString("text", "")
        if (text.isBlank()) {
            return McpToolCallResult.text("missing text", isError = true)
        }
        return McpToolCallResult.text(text)
    }
}
