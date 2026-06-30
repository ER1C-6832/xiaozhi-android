package com.er1cmo.xiaozhiandroid.core

import android.content.Context
import com.er1cmo.xiaozhiandroid.audio.AudioEngine
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.domain.ConversationState
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
import com.er1cmo.xiaozhiandroid.conversation.ConversationSession
import com.er1cmo.xiaozhiandroid.conversation.ConversationStateMachine
import com.er1cmo.xiaozhiandroid.protocol.ProtocolEvent
import com.er1cmo.xiaozhiandroid.protocol.WebSocketXiaozhiProtocolClient
import com.er1cmo.xiaozhiandroid.protocol.XiaozhiMessageRouter
import com.er1cmo.xiaozhiandroid.protocol.XiaozhiProtocolClient
import kotlinx.coroutines.CoroutineScope

/**
 * Android app core container.
 *
 * Phase 8A-1 moved object construction and lifecycle ownership out of
 * AppNavigation. Phase 8A-2 started moving runtime ownership into this controller.
 * Phase 8A-3 moved WebSocket callback adaptation and protocol message routing
 * here as well. Phase 8A-4 moves conversation state transitions into this
 * controller and AppStateStore so MainViewModel is closer to a thin presenter.
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


    fun transitionConversationState(
        next: ConversationState,
        reason: String,
    ): ConversationState {
        val from = conversationStateMachine.currentState
        val resolved = conversationStateMachine.transitionTo(next, reason)
        stateStore.update { state -> state.copy(conversationState = resolved) }
        if (from != resolved) {
            eventBus.publish(
                AppEvent.ConversationStateChanged(
                    from = from,
                    to = resolved,
                    reason = reason,
                ),
            )
        }
        return resolved
    }

    fun resetConversationState(reason: String): ConversationState {
        return transitionConversationState(ConversationState.Idle, reason)
    }

    fun publishProtocolEvent(event: ProtocolEvent) {
        when (event) {
            is ProtocolEvent.Log -> eventBus.publish(AppEvent.Log(event.message))
            is ProtocolEvent.Connected -> {
                transitionConversationState(ConversationState.Connected, "protocol_connected")
                eventBus.publish(AppEvent.ProtocolConnected(event.sessionId))
            }
            is ProtocolEvent.Closed -> {
                if (!event.reason.contains("reconnect", ignoreCase = true)) {
                    transitionConversationState(ConversationState.Idle, "protocol_closed")
                }
                eventBus.publish(AppEvent.ProtocolDisconnected(event.reason))
            }
            is ProtocolEvent.Error -> {
                transitionConversationState(ConversationState.Error, "protocol_error")
                eventBus.publish(AppEvent.Error(event.message))
            }
            is ProtocolEvent.NetworkStateChanged -> Unit
            is ProtocolEvent.JsonMessage -> eventBus.publish(
                AppEvent.IncomingJson(
                    type = event.type,
                    state = event.state,
                    preview = event.prettyJson.take(MAX_EVENT_PREVIEW_LENGTH),
                ),
            )
            is ProtocolEvent.BinaryAudio -> eventBus.publish(AppEvent.IncomingAudio(event.data.size))
        }
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
        resetConversationState("close_runtime:$reason")
        eventBus.publish(AppEvent.ProtocolDisconnected(reason))
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
                override val name: String = "mcp-placeholder"
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

    companion object {
        private const val MAX_EVENT_PREVIEW_LENGTH = 1_500

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
