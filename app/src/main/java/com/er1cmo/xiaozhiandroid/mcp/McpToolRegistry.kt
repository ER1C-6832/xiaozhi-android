package com.er1cmo.xiaozhiandroid.mcp

import org.json.JSONArray
import org.json.JSONObject

class McpToolRegistry {
    private val tools = linkedMapOf<String, McpTool>()

    fun register(tool: McpTool): Boolean {
        if (tools.containsKey(tool.name)) return false
        tools[tool.name] = tool
        return true
    }

    fun find(name: String): McpTool? = tools[name]

    fun count(): Int = tools.size

    fun names(): List<String> = tools.keys.toList()

    fun listToolsPage(
        cursor: String = "",
        maxPayloadSize: Int = MAX_PAYLOAD_SIZE,
    ): JSONObject {
        val resultTools = JSONArray()
        var totalSize = 0
        var foundCursor = cursor.isBlank()
        var nextCursor = ""

        for (tool in tools.values) {
            if (!foundCursor) {
                if (tool.name == cursor) {
                    foundCursor = true
                } else {
                    continue
                }
            }

            val toolJson = tool.toJson()
            val toolSize = toolJson.toString().length
            if (totalSize + toolSize + PAYLOAD_HEADROOM > maxPayloadSize) {
                nextCursor = tool.name
                break
            }

            resultTools.put(toolJson)
            totalSize += toolSize
        }

        return JSONObject()
            .put("tools", resultTools)
            .also { result ->
                if (nextCursor.isNotBlank()) {
                    result.put("nextCursor", nextCursor)
                }
            }
    }

    companion object {
        private const val MAX_PAYLOAD_SIZE = 20_000
        private const val PAYLOAD_HEADROOM = 100
    }
}
