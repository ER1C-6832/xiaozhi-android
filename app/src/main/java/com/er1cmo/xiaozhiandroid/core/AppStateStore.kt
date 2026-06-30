package com.er1cmo.xiaozhiandroid.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState

/**
 * Compose-friendly app state holder.
 *
 * Current UI still reads MainViewModel.uiState. This store is introduced as the
 * future single UI state source so MainViewModel can become a thin presenter and
 * AppController can own the conversation/session lifecycle.
 */
class AppStateStore(
    initialState: ConversationUiState = ConversationUiState(),
) {
    var uiState by mutableStateOf(initialState)
        private set

    fun replace(nextState: ConversationUiState) {
        uiState = nextState
    }

    fun update(reducer: (ConversationUiState) -> ConversationUiState) {
        uiState = reducer(uiState)
    }
}
