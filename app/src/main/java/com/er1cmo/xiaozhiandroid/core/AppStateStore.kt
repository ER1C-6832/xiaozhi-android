package com.er1cmo.xiaozhiandroid.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.er1cmo.xiaozhiandroid.domain.ConversationUiState

/**
 * Compose-friendly app state holder.
 *
 * Phase 8A-4 makes this the single backing store for ConversationUiState. The
 * MainViewModel still acts as the Compose presenter, but reads/writes UI state
 * through this store instead of owning a separate mutable state field.
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
