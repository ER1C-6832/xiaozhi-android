package com.er1cmo.xiaozhiandroid.mcp.tools

import com.er1cmo.xiaozhiandroid.mcp.McpToolCallResult
import org.json.JSONArray
import org.json.JSONObject

internal fun objectSchema(
    properties: JSONObject = JSONObject(),
    required: JSONArray = JSONArray(),
    additionalProperties: Boolean = false,
): JSONObject {
    return JSONObject()
        .put("type", "object")
        .put("properties", properties)
        .also { schema ->
            if (required.length() > 0) {
                schema.put("required", required)
            }
        }
        .put("additionalProperties", additionalProperties)
}

internal fun stringProperty(
    description: String,
    enumValues: List<String> = emptyList(),
): JSONObject {
    return JSONObject()
        .put("type", "string")
        .put("description", description)
        .also { property ->
            if (enumValues.isNotEmpty()) {
                property.put("enum", JSONArray(enumValues))
            }
        }
}

internal fun numberProperty(description: String): JSONObject {
    return JSONObject()
        .put("type", "number")
        .put("description", description)
}

internal fun integerProperty(description: String): JSONObject {
    return JSONObject()
        .put("type", "integer")
        .put("description", description)
}

internal fun booleanProperty(description: String): JSONObject {
    return JSONObject()
        .put("type", "boolean")
        .put("description", description)
}

internal fun jsonTextResult(json: JSONObject): McpToolCallResult {
    return McpToolCallResult.text(json.toString(2))
}

internal fun jsonArrayTextResult(json: JSONArray): McpToolCallResult {
    return McpToolCallResult.text(json.toString(2))
}

internal fun errorText(message: String): McpToolCallResult {
    return McpToolCallResult.text(message, isError = true)
}
