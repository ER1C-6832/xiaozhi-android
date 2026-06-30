package com.er1cmo.xiaozhiandroid.conversation

import com.er1cmo.xiaozhiandroid.domain.ConversationState

/**
 * Central state-transition helper.
 *
 * Phase 8A-4 makes state transitions explicit and observable. This class still
 * keeps transitions permissive to avoid changing runtime behavior, but every
 * transition now carries a reason and a small history for future diagnostics.
 */
class ConversationStateMachine(
    initialState: ConversationState = ConversationState.Idle,
) {
    data class Transition(
        val from: ConversationState,
        val to: ConversationState,
        val reason: String,
        val timestampMillis: Long = System.currentTimeMillis(),
    )

    var currentState: ConversationState = initialState
        private set

    var lastTransition: Transition = Transition(
        from = initialState,
        to = initialState,
        reason = "initial",
    )
        private set

    private val history = ArrayDeque<Transition>()

    fun recentTransitions(): List<Transition> = history.toList()

    fun onActivating(reason: String = "activate"): ConversationState = transitionTo(ConversationState.Activating, reason)

    fun onConnecting(reason: String = "connect"): ConversationState = transitionTo(ConversationState.Connecting, reason)

    fun onConnected(reason: String = "connected"): ConversationState = transitionTo(ConversationState.Connected, reason)

    fun onListening(reason: String = "listen"): ConversationState = transitionTo(ConversationState.Listening, reason)

    fun onThinking(reason: String = "thinking"): ConversationState = transitionTo(ConversationState.Thinking, reason)

    fun onSpeaking(reason: String = "speaking"): ConversationState = transitionTo(ConversationState.Speaking, reason)

    fun onIdle(reason: String = "idle"): ConversationState = transitionTo(ConversationState.Idle, reason)

    fun onError(reason: String = "error"): ConversationState = transitionTo(ConversationState.Error, reason)

    fun onAbort(
        hasActiveSession: Boolean,
        reason: String = "abort",
    ): ConversationState {
        return transitionTo(if (hasActiveSession) ConversationState.Connected else ConversationState.Idle, reason)
    }

    fun transitionTo(
        next: ConversationState,
        reason: String = "transition",
    ): ConversationState {
        val transition = Transition(
            from = currentState,
            to = next,
            reason = reason,
        )
        currentState = next
        lastTransition = transition
        history.addLast(transition)
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeFirst()
        }
        return currentState
    }

    private companion object {
        const val MAX_HISTORY_SIZE = 40
    }
}
