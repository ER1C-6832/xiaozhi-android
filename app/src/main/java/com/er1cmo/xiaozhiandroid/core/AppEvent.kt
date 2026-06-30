package com.er1cmo.xiaozhiandroid.core

/**
 * Lightweight app-wide event model.
 *
 * Phase 8A introduces this event surface so future modules such as MCP,
 * CameraX, wake word, notification controls and UI cards do not need to call
 * MainViewModel directly. Only a small subset is emitted in 8A-1; later 8A
 * steps will move protocol/audio/conversation callbacks onto this bus.
 */
sealed interface AppEvent {
    data object AppStarted : AppEvent
    data object AppStopped : AppEvent

    data class ModuleRegistered(val moduleName: String) : AppEvent
    data class ModuleStarted(val moduleName: String) : AppEvent
    data class ModuleStopped(val moduleName: String) : AppEvent
    data class ModuleFailed(val moduleName: String, val message: String) : AppEvent

    data class ProtocolConnected(val sessionId: String) : AppEvent
    data class ProtocolDisconnected(val reason: String) : AppEvent
    data class IncomingJson(val type: String, val state: String, val preview: String) : AppEvent
    data class IncomingAudio(val bytes: Int) : AppEvent

    data class ToolCallStarted(val toolName: String, val requestId: String) : AppEvent
    data class ToolCallFinished(
        val toolName: String,
        val requestId: String,
        val success: Boolean,
        val message: String,
    ) : AppEvent

    data class Log(val message: String) : AppEvent
    data class Error(val message: String, val cause: Throwable? = null) : AppEvent
}
