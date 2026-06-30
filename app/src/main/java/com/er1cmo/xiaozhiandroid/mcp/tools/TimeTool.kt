package com.er1cmo.xiaozhiandroid.mcp.tools

import com.er1cmo.xiaozhiandroid.mcp.McpTool
import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import com.er1cmo.xiaozhiandroid.mcp.emptyObjectSchema
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

class CurrentTimeTool : McpTool {
    override val name: String = "android.get_current_time"
    override val description: String = "获取手机本地当前时间、时区和 UTC 时间。"
    override val inputSchema: JSONObject = emptyObjectSchema()

    override fun call(arguments: JSONObject): McpToolCallResult {
        val now = Date()
        val localFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val utcFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return jsonTextResult(
            JSONObject()
                .put("localTime", localFormatter.format(now))
                .put("utcTime", utcFormatter.format(now))
                .put("timeZone", TimeZone.getDefault().id)
                .put("epochMillis", now.time),
        )
    }
}
