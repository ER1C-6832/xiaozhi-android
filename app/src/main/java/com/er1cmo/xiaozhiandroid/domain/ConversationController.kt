package com.er1cmo.xiaozhiandroid.domain

/**
 * Stable operation surface for the main voice assistant flow.
 *
 * MainViewModel implements this interface in Phase 6. Later phases can move the
 * implementation into a separate controller class without changing UI callers.
 */
interface ConversationController {
    fun connect()
    fun sendText()
    fun startManualListening()
    fun stopListening()
    fun abort()
    fun reconnect()
    fun close()
}
