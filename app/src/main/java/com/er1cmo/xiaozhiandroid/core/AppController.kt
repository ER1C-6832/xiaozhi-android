package com.er1cmo.xiaozhiandroid.core

import android.content.Context
import com.er1cmo.xiaozhiandroid.audio.AudioEngine
import com.er1cmo.xiaozhiandroid.conversation.ConversationSession
import com.er1cmo.xiaozhiandroid.conversation.ConversationStateMachine
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.mcp.AndroidMcpServer
import com.er1cmo.xiaozhiandroid.mcp.McpToolRegistry
import com.er1cmo.xiaozhiandroid.mcp.tools.AndroidEchoTool
import com.er1cmo.xiaozhiandroid.mcp.tools.AndroidPingTool
import com.er1cmo.xiaozhiandroid.mcp.tools.AppOpenTool
import com.er1cmo.xiaozhiandroid.mcp.tools.BatteryStatusTool
import com.er1cmo.xiaozhiandroid.mcp.tools.BrightnessGetTool
import com.er1cmo.xiaozhiandroid.mcp.tools.BrightnessSetTool
import com.er1cmo.xiaozhiandroid.mcp.tools.ClipboardSetTextTool
import com.er1cmo.xiaozhiandroid.mcp.tools.CurrentTimeTool
import com.er1cmo.xiaozhiandroid.mcp.tools.DeviceInfoTool
import com.er1cmo.xiaozhiandroid.mcp.tools.FlashlightSetTool
import com.er1cmo.xiaozhiandroid.mcp.tools.NetworkStatusTool
import com.er1cmo.xiaozhiandroid.mcp.tools.OpenSettingsTool
import com.er1cmo.xiaozhiandroid.mcp.tools.RingerModeGetTool
import com.er1cmo.xiaozhiandroid.mcp.tools.RingerModeSetTool
import com.er1cmo.xiaozhiandroid.mcp.tools.VolumeGetTool
import com.er1cmo.xiaozhiandroid.mcp.tools.VolumeSetTool
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
import com.er1cmo.xiaozhiandroid.protocol.ProtocolEvent
import com.er1cmo.xiaozhiandroid.protocol.WebSocketXiaozhiProtocolClient
import com.er1cmo.xiaozhiandroid.protocol.XiaozhiMessageRouter
import com.er1cmo.xiaozhiandroid.protocol.XiaozhiProtocolClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Android app core container.
 *
 * Phase 8A moved runtime ownership, protocol callbacks and conversation state
 * into this controller. Phase 8B adds the Android MCP protocol layer here so
 * type=mcp JSON-RPC messages can be handled without growing MainViewModel.
 * Phase 8B-1 makes MCP request/call/response flow observable in debug logs.
 * Phase 8C registers the first batch of Android native tools.
 */
class AppController private constructor(
    val appContext: Context,
    val appScope: CoroutineScope,
    val eventBus: AppEventBus,
    val stateStore: AppStateStore,
    val moduleManager: ModuleManager,
    val resourceRegistry: ResourceRegistry,
    val configRepository: ConfigRepository,
    val deviceIdentityManager: DeviceIdentityManager,
    val otaActivationClient: OtaActivationClient,
    val xiaozhiWebSocketClient: XiaozhiWebSocketClient,
    val protocolClient: XiaozhiProtocolClient,
    val messageRouter: XiaozhiMessageRouter,
    val mcpServer: AndroidMcpServer,
    val audioEngine: AudioEngine,
    val conversationStateMachine: ConversationStateMachine,
    val conversationSession: ConversationSession,
) {
    suspend fun start() {
        eventBus.emit(AppEvent.AppStarted)
        moduleManager.startAll()
    }

    suspend fun shutdown() {
        moduleManager.stopAll()
        resourceRegistry.shutdown()
        moduleManager.disposeAll()
        eventBus.emit(AppEvent.AppStopped)
    }

    suspend fun connectProtocol(
        onEvent: (ProtocolEvent) -> Unit,
    ): Boolean {
        return protocolClient.connect(
            callbacks = messageRouter.buildCallbacks { event ->
                publishProtocolEvent(event)
                onEvent(event)
            },
        )
    }

    fun publishProtocolEvent(event: ProtocolEvent) {
        when (event) {
            is ProtocolEvent.Log -> eventBus.publish(AppEvent.Log(event.message))
            is ProtocolEvent.Connected -> eventBus.publish(AppEvent.ProtocolConnected(event.sessionId))
            is ProtocolEvent.Closed -> eventBus.publish(AppEvent.ProtocolDisconnected(event.reason))
            is ProtocolEvent.Error -> eventBus.publish(AppEvent.Error(event.message))
            is ProtocolEvent.NetworkStateChanged -> Unit
            is ProtocolEvent.JsonMessage -> {
                eventBus.publish(
                    AppEvent.IncomingJson(
                        type = event.type,
                        state = event.state,
                        preview = event.prettyJson.take(MAX_EVENT_PREVIEW_LENGTH),
                    ),
                )
                if (event.type == "mcp") {
                    handleMcpMessage(event)
                }
            }
            is ProtocolEvent.BinaryAudio -> eventBus.publish(AppEvent.IncomingAudio(event.data.size))
        }
    }

    fun transitionConversationState(
        nextState: ConversationState,
        reason: String,
    ): ConversationState {
        val fromState = conversationStateMachine.currentState
        val updated = conversationStateMachine.transitionTo(nextState, reason)
        if (fromState != updated) {
            eventBus.publish(AppEvent.ConversationStateChanged(fromState, updated, reason))
        }
        return updated
    }

    /**
     * Close currently active runtime resources in a single place.
     *
     * UI flows still decide how to update their visible state, but the actual
     * audio/protocol cleanup is centralized here so future MCP, wake word,
     * CameraX and notification modules do not duplicate shutdown behavior.
     */
    fun closeRuntime(
        reason: String,
        onAudioLog: (String) -> Unit = {},
        onAudioStatusChanged: (String) -> Unit = {},
    ) {
        audioEngine.stopRecording()
        audioEngine.stopPlayback(
            onLog = onAudioLog,
            onStatusChanged = onAudioStatusChanged,
        )
        xiaozhiWebSocketClient.close(reason)
        eventBus.publish(AppEvent.ProtocolDisconnected(reason))
    }

    private fun handleMcpMessage(event: ProtocolEvent.JsonMessage) {
        val trace = mcpTraceFrom(event.prettyJson)
        appendMcpDebugLog("MCP 收到请求：method=${trace.method}, id=${trace.requestId}, tool=${trace.toolName}")
        appScope.launch {
            val handled = mcpServer.handleProtocolMessage(
                protocolMessage = event.prettyJson,
                sendResponse = { payload ->
                    val sent = protocolClient.sendMcpMessage(payload)
                    val hasError = payload.has("error")
                    val responsePreview = mcpResponsePreview(payload)
                    appendMcpDebugLog(
                        "MCP 响应已发送：method=${trace.method}, id=${trace.requestId}, success=$sent, error=$hasError",
                        lastJson = responsePreview,
                    )
                    sent
                },
                onLog = { message ->
                    eventBus.publish(AppEvent.Log(message))
                    appendMcpDebugLog(message)
                },
                onToolStarted = { toolName, requestId ->
                    eventBus.publish(AppEvent.ToolCallStarted(toolName, requestId))
                    appendMcpDebugLog("MCP 工具开始：$toolName id=$requestId")
                },
                onToolFinished = { toolName, requestId, success, message ->
                    eventBus.publish(
                        AppEvent.ToolCallFinished(
                            toolName = toolName,
                            requestId = requestId,
                            success = success,
                            message = message,
                        ),
                    )
                    appendMcpDebugLog("MCP 工具完成：$toolName id=$requestId success=$success message=$message")
                },
            )
            if (!handled) {
                val message = "MCP 消息未处理：payload 缺失或格式错误"
                eventBus.publish(AppEvent.Log(message))
                appendMcpDebugLog(message)
            }
        }
    }

    private fun appendMcpDebugLog(
        message: String,
        lastJson: String? = null,
    ) {
        val line = "${timestamp()} $message"
        stateStore.update { state ->
            state.copy(
                debugLogs = (state.debugLogs + line).takeLast(MAX_DEBUG_LOG_LINES),
                lastServerJson = lastJson ?: state.lastServerJson,
            )
        }
    }

    private fun mcpTraceFrom(protocolMessage: String): McpTrace {
        val wrapper = runCatching { JSONObject(protocolMessage) }.getOrNull()
        val payload = when (val rawPayload = wrapper?.opt("payload")) {
            is JSONObject -> rawPayload
            is String -> runCatching { JSONObject(rawPayload) }.getOrNull()
            else -> null
        }
        val method = payload?.optString("method", "unknown")?.ifBlank { "unknown" } ?: "unknown"
        val requestId = when {
            payload == null -> "unknown"
            !payload.has("id") || payload.isNull("id") -> "none"
            else -> payload.opt("id").toString()
        }
        val toolName = payload
            ?.optJSONObject("params")
            ?.optString("name", "-")
            ?.ifBlank { "-" }
            ?: "-"
        return McpTrace(method = method, requestId = requestId, toolName = toolName)
    }

    private fun mcpResponsePreview(payload: JSONObject): String {
        val wrapper = JSONObject()
            .put("type", "mcp")
            .put("direction", "android_response")
            .put("payload", payload)
        return wrapper.toString(2).take(MAX_EVENT_PREVIEW_LENGTH)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun registerCoreModules() {
        moduleManager.register(
            object : AppModule {
                override val name: String = "protocol"
            },
        )
        moduleManager.register(
            object : AppModule {
                override val name: String = "audio"

                override suspend fun stop() {
                    audioEngine.stopRecording()
                    audioEngine.stopPlayback(onLog = {}, onStatusChanged = {})
                }
            },
        )
        moduleManager.register(
            object : AppModule {
                override val name: String = "mcp"
            },
        )
        moduleManager.register(
            object : AppModule {
                override val name: String = "camera-placeholder"
            },
        )
        moduleManager.register(
            object : AppModule {
                override val name: String = "wakeword-placeholder"
            },
        )
    }

    private fun registerResources() {
        resourceRegistry.register("audio") {
            audioEngine.stopRecording()
            audioEngine.stopPlayback(onLog = {}, onStatusChanged = {})
        }
        resourceRegistry.register("websocket") {
            xiaozhiWebSocketClient.close("app_shutdown")
        }
    }

    private data class McpTrace(
        val method: String,
        val requestId: String,
        val toolName: String,
    )

    companion object {
        private const val MAX_EVENT_PREVIEW_LENGTH = 1_500
        private const val MAX_DEBUG_LOG_LINES = 120

        fun create(
            context: Context,
            appScope: CoroutineScope,
        ): AppController {
            val appContext = context.applicationContext
            val eventBus = AppEventBus()
            val stateStore = AppStateStore()
            val moduleManager = ModuleManager(eventBus)
            val resourceRegistry = ResourceRegistry(eventBus)
            val configRepository = ConfigRepository(appContext)
            val deviceIdentityManager = DeviceIdentityManager(configRepository)
            val otaActivationClient = OtaActivationClient(configRepository)
            val xiaozhiWebSocketClient = XiaozhiWebSocketClient(
                configRepository = configRepository,
                appScope = appScope,
            )
            val protocolClient = WebSocketXiaozhiProtocolClient(xiaozhiWebSocketClient)
            val mcpRegistry = McpToolRegistry().apply {
                register(AndroidPingTool())
                register(AndroidEchoTool())
                register(DeviceInfoTool(appContext))
                register(BatteryStatusTool(appContext))
                register(NetworkStatusTool(appContext))
                register(CurrentTimeTool())
                register(VolumeGetTool(appContext))
                register(VolumeSetTool(appContext))
                register(RingerModeGetTool(appContext))
                register(RingerModeSetTool(appContext))
                register(AppOpenTool(appContext))
                register(BrightnessGetTool(appContext))
                register(BrightnessSetTool(appContext))
                register(FlashlightSetTool(appContext))
                register(ClipboardSetTextTool(appContext))
                register(OpenSettingsTool(appContext))
            }
            val mcpServer = AndroidMcpServer(mcpRegistry)
            val audioEngine = AudioEngine(appScope)
            val conversationStateMachine = ConversationStateMachine()
            val conversationSession = ConversationSession()

            return AppController(
                appContext = appContext,
                appScope = appScope,
                eventBus = eventBus,
                stateStore = stateStore,
                moduleManager = moduleManager,
                resourceRegistry = resourceRegistry,
                configRepository = configRepository,
                deviceIdentityManager = deviceIdentityManager,
                otaActivationClient = otaActivationClient,
                xiaozhiWebSocketClient = xiaozhiWebSocketClient,
                protocolClient = protocolClient,
                messageRouter = XiaozhiMessageRouter,
                mcpServer = mcpServer,
                audioEngine = audioEngine,
                conversationStateMachine = conversationStateMachine,
                conversationSession = conversationSession,
            ).apply {
                registerCoreModules()
                registerResources()
            }
        }
    }
}
