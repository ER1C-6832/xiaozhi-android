package com.er1cmo.xiaozhiandroid.mcp

import org.json.JSONArray
import org.json.JSONObject

interface McpTool {
    val name: String
    val description: String
    val inputSchema: JSONObject

    fun call(arguments: JSONObject): McpToolCallResult

    fun toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", inputSchema)
    }
}

data class McpToolCallResult(
    val content: JSONArray,
    val isError: Boolean = false,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("content", content)
            .put("isError", isError)
    }

    companion object {
        fun text(
            text: String,
            isError: Boolean = false,
        ): McpToolCallResult {
            val content = JSONArray()
                .put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", text),
                )
            return McpToolCallResult(content = content, isError = isError)
        }
    }
}

fun emptyObjectSchema(): JSONObject {
    return JSONObject()
        .put("type", "object")
        .put("properties", JSONObject())
        .put("additionalProperties", false)
}
