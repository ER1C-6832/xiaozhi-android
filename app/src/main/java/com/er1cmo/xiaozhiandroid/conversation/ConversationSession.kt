package com.er1cmo.xiaozhiandroid.conversation

/** Runtime conversation/session snapshot. */
data class ConversationSession(
    val sessionId: String = "",
    val connected: Boolean = false,
    val activated: Boolean = false,
    val lastServerJson: String = "",
    val lastError: String = "",
) {
    val hasActiveSession: Boolean
        get() = connected && sessionId.isNotBlank()
}
