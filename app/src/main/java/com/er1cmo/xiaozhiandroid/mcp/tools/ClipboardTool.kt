package com.er1cmo.xiaozhiandroid.mcp.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject

class ClipboardSetTextTool(
    private val context: Context,
) : McpTool {
    override val name: String = "android.set_clipboard_text"
    override val description: String = "把指定文本复制到 Android 剪贴板。"
    override val inputSchema: JSONObject = objectSchema(
        properties = JSONObject()
            .put("text", stringProperty("需要复制到剪贴板的文本。"))
            .put("label", stringProperty("剪贴板标签，默认 xiaozhi。")),
        required = JSONArray().put("text"),
    )

    override fun call(arguments: JSONObject): McpToolCallResult {
        val text = arguments.optString("text", "")
        if (text.isBlank()) return errorText("missing text")
        val label = arguments.optString("label", "xiaozhi").ifBlank { "xiaozhi" }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return jsonTextResult(
            JSONObject()
                .put("copied", true)
                .put("length", text.length)
                .put("label", label),
        )
    }
}
