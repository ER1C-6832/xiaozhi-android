package com.er1cmo.xiaozhiandroid.conversation

/**
 * Conversation command surface for future core-driven UI.
 *
 * The older domain.ConversationController remains for compatibility during
 * Phase 8A. Later 8A steps will migrate MainViewModel to depend on this
 * package-level controller and remove duplicate command surfaces.
 */
interface ConversationController {
    fun connect()
    fun reconnect()
    fun close()
    fun sendText()
    fun startManualListening()
    fun stopListening()
    fun abort()
}
