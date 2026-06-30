package com.er1cmo.xiaozhiandroid.core

import android.content.Context
import com.er1cmo.xiaozhiandroid.audio.AudioEngine
import com.er1cmo.xiaozhiandroid.data.config.ConfigRepository
import com.er1cmo.xiaozhiandroid.data.identity.DeviceIdentityManager
import com.er1cmo.xiaozhiandroid.data.ota.OtaActivationClient
import com.er1cmo.xiaozhiandroid.network.XiaozhiWebSocketClient
import kotlinx.coroutines.CoroutineScope

/**
 * Android app core container.
 *
 * Phase 8A-1 moves object construction and lifecycle ownership out of
 * AppNavigation. MainViewModel still owns most conversation behavior in this
 * step to keep the package low-risk; subsequent 8A steps will move state
 * transitions, protocol routing and MCP dispatch from MainViewModel into this
 * controller and its modules.
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
    val audioEngine: AudioEngine,
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
            val audioEngine = AudioEngine(appScope)

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
                audioEngine = audioEngine,
            ).apply {
                registerCoreModules()
                registerResources()
            }
        }
    }
}
