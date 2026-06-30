package com.er1cmo.xiaozhiandroid.core

import com.er1cmo.xiaozhiandroid.domain.ConversationState

/**
 * Lightweight app-wide event model.
 *
 * Phase 8A-4 makes conversation state changes first-class events so future
 * modules such as MCP, CameraX, wake word and notification controls can observe
 * state transitions without coupling themselves to MainViewModel.
 */
sealed interface AppEvent {
    data object AppStarted : AppEvent
    data object AppStopped : AppEvent

    data class ModuleRegistered(val moduleName: String) : AppEvent
    data class ModuleStarted(val moduleName: String) : AppEvent
    data class ModuleStopped(val moduleName: String) : AppEvent
    data class ModuleFailed(val moduleName: String, val message: String) : AppEvent

    data class ConversationStateChanged(
        val from: ConversationState,
        val to: ConversationState,
        val reason: String,
    ) : AppEvent

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
