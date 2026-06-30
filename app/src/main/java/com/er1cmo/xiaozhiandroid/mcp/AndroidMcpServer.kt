package com.er1cmo.xiaozhiandroid.mcp

import org.json.JSONObject

class AndroidMcpServer(
    val registry: McpToolRegistry,
) {
    fun handleProtocolMessage(
        protocolMessage: String,
        sendResponse: (JSONObject) -> Boolean,
        onLog: (String) -> Unit,
        onToolStarted: (toolName: String, requestId: String) -> Unit,
        onToolFinished: (toolName: String, requestId: String, success: Boolean, message: String) -> Unit,
    ): Boolean {
        val payload = extractPayload(protocolMessage) ?: return false
        handleJsonRpc(
            payload = payload,
            sendResponse = sendResponse,
            onLog = onLog,
            onToolStarted = onToolStarted,
            onToolFinished = onToolFinished,
        )
        return true
    }

    private fun handleJsonRpc(
        payload: JSONObject,
        sendResponse: (JSONObject) -> Boolean,
        onLog: (String) -> Unit,
        onToolStarted: (toolName: String, requestId: String) -> Unit,
        onToolFinished: (toolName: String, requestId: String, success: Boolean, message: String) -> Unit,
    ) {
        val requestId = payload.requestIdOrNull()
        val method = payload.optString("method", "")

        if (payload.optString("jsonrpc", "") != JSON_RPC_VERSION) {
            onLog("MCP 请求 JSON-RPC 版本无效")
            requestId?.let { replyError(it, ERROR_INVALID_REQUEST, "Invalid JSON-RPC version", sendResponse) }
            return
        }

        if (method.startsWith("notifications")) {
            onLog("MCP 通知已忽略：$method")
            return
        }

        if (method.isBlank()) {
            onLog("MCP 请求缺少 method")
            requestId?.let { replyError(it, ERROR_INVALID_REQUEST, "Missing method", sendResponse) }
            return
        }

        if (requestId == null) {
            onLog("MCP 请求缺少 id，已忽略：$method")
            return
        }

        onLog("MCP 请求：method=$method, id=$requestId")
        when (method) {
            "initialize" -> handleInitialize(requestId, sendResponse)
            "tools/list" -> handleToolsList(requestId, payload.paramsObject(), sendResponse)
            "tools/call" -> handleToolsCall(
                requestId = requestId,
                params = payload.paramsObject(),
                sendResponse = sendResponse,
                onToolStarted = onToolStarted,
                onToolFinished = onToolFinished,
            )
            else -> replyError(requestId, ERROR_METHOD_NOT_FOUND, "Method not implemented: $method", sendResponse)
        }
    }

    private fun handleInitialize(
        requestId: Any,
        sendResponse: (JSONObject) -> Boolean,
    ) {
        val result = JSONObject()
            .put("protocolVersion", MCP_PROTOCOL_VERSION)
            .put(
                "capabilities",
                JSONObject()
                    .put("tools", JSONObject()),
            )
            .put(
                "serverInfo",
                JSONObject()
                    .put("name", SERVER_NAME)
                    .put("version", SERVER_VERSION),
            )
        replyResult(requestId, result, sendResponse)
    }

    private fun handleToolsList(
        requestId: Any,
        params: JSONObject,
        sendResponse: (JSONObject) -> Boolean,
    ) {
        val cursor = params.optString("cursor", "")
        replyResult(requestId, registry.listToolsPage(cursor), sendResponse)
    }

    private fun handleToolsCall(
        requestId: Any,
        params: JSONObject,
        sendResponse: (JSONObject) -> Boolean,
        onToolStarted: (toolName: String, requestId: String) -> Unit,
        onToolFinished: (toolName: String, requestId: String, success: Boolean, message: String) -> Unit,
    ) {
        val toolName = params.optString("name", "")
        if (toolName.isBlank()) {
            replyError(requestId, ERROR_INVALID_PARAMS, "Missing tool name", sendResponse)
            return
        }

        val tool = registry.find(toolName)
        if (tool == null) {
            replyError(requestId, ERROR_METHOD_NOT_FOUND, "Unknown tool: $toolName", sendResponse)
            return
        }

        val requestIdText = requestId.toString()
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        onToolStarted(toolName, requestIdText)
        try {
            val result = tool.call(arguments)
            replyResult(requestId, result.toJson(), sendResponse)
            onToolFinished(toolName, requestIdText, true, "success")
        } catch (exception: Exception) {
            val message = exception.message ?: exception::class.java.simpleName
            replyError(requestId, ERROR_INTERNAL, message, sendResponse)
            onToolFinished(toolName, requestIdText, false, message)
        }
    }

    private fun replyResult(
        requestId: Any,
        result: JSONObject,
        sendResponse: (JSONObject) -> Boolean,
    ) {
        val payload = JSONObject()
            .put("jsonrpc", JSON_RPC_VERSION)
            .put("id", requestId)
            .put("result", result)
        sendResponse(payload)
    }

    private fun replyError(
        requestId: Any,
        code: Int,
        message: String,
        sendResponse: (JSONObject) -> Boolean,
    ) {
        val payload = JSONObject()
            .put("jsonrpc", JSON_RPC_VERSION)
            .put("id", requestId)
            .put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", message),
            )
        sendResponse(payload)
    }

    private fun extractPayload(protocolMessage: String): JSONObject? {
        val wrapper = runCatching { JSONObject(protocolMessage) }.getOrNull() ?: return null
        if (wrapper.optString("type", "") != "mcp") return null
        val payloadValue = wrapper.opt("payload") ?: return null
        return when (payloadValue) {
            is JSONObject -> payloadValue
            is String -> runCatching { JSONObject(payloadValue) }.getOrNull()
            else -> null
        }
    }

    private fun JSONObject.paramsObject(): JSONObject {
        return optJSONObject("params") ?: JSONObject()
    }

    private fun JSONObject.requestIdOrNull(): Any? {
        if (!has("id") || isNull("id")) return null
        return opt("id")
    }

    companion object {
        private const val JSON_RPC_VERSION = "2.0"
        private const val MCP_PROTOCOL_VERSION = "2024-11-05"
        private const val SERVER_NAME = "xiaozhi-android"
        private const val SERVER_VERSION = "0.8B"
        private const val ERROR_INVALID_REQUEST = -32600
        private const val ERROR_METHOD_NOT_FOUND = -32601
        private const val ERROR_INVALID_PARAMS = -32602
        private const val ERROR_INTERNAL = -32603
    }
}
