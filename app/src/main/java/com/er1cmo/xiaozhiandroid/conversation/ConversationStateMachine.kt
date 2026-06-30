package com.er1cmo.xiaozhiandroid.conversation

import com.er1cmo.xiaozhiandroid.domain.ConversationState

/**
 * Central state-transition helper.
 *
 * Phase 8A-1 keeps this conservative and deterministic. It does not try to
 * encode every edge case yet; it gives MCP, wake word and automatic dialogue a
 * common place to add state transitions instead of editing MainViewModel in
 * multiple unrelated sections.
 */
class ConversationStateMachine(
    initialState: ConversationState = ConversationState.Idle,
) {
    var currentState: ConversationState = initialState
        private set

    fun onActivating(): ConversationState = transitionTo(ConversationState.Activating)

    fun onConnecting(): ConversationState = transitionTo(ConversationState.Connecting)

    fun onConnected(): ConversationState = transitionTo(ConversationState.Connected)

    fun onListening(): ConversationState = transitionTo(ConversationState.Listening)

    fun onThinking(): ConversationState = transitionTo(ConversationState.Thinking)

    fun onSpeaking(): ConversationState = transitionTo(ConversationState.Speaking)

    fun onIdle(): ConversationState = transitionTo(ConversationState.Idle)

    fun onError(): ConversationState = transitionTo(ConversationState.Error)

    fun onAbort(hasActiveSession: Boolean): ConversationState {
        return transitionTo(if (hasActiveSession) ConversationState.Connected else ConversationState.Idle)
    }

    private fun transitionTo(next: ConversationState): ConversationState {
        currentState = next
        return currentState
    }
}
