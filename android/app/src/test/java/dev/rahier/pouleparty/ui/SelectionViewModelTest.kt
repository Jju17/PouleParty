package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.selection.SelectionUiState
import org.junit.Assert.*
import org.junit.Test

class SelectionViewModelTest {

    @Test
    fun `initial state has all dialogs hidden`() {
        val state = SelectionUiState()
        assertFalse(state.isShowingPasswordDialog)
        assertFalse(state.isShowingJoinDialog)
        assertFalse(state.isShowingGameRules)
        assertFalse(state.isShowingGameNotFound)
        assertEquals("", state.password)
        assertEquals("", state.gameCode)
    }

    @Test
    fun `start button shows join dialog`() {
        val state = SelectionUiState()
        val updated = state.copy(isShowingJoinDialog = true)
        assertTrue(updated.isShowingJoinDialog)
    }

    @Test
    fun `i am la poule shows password dialog`() {
        val state = SelectionUiState()
        val updated = state.copy(isShowingPasswordDialog = true)
        assertTrue(updated.isShowingPasswordDialog)
    }

    @Test
    fun `rules button shows game rules`() {
        val state = SelectionUiState()
        val updated = state.copy(isShowingGameRules = true)
        assertTrue(updated.isShowingGameRules)
    }

    @Test
    fun `game not found shows alert`() {
        val state = SelectionUiState()
        val updated = state.copy(isShowingGameNotFound = true)
        assertTrue(updated.isShowingGameNotFound)
    }
}
