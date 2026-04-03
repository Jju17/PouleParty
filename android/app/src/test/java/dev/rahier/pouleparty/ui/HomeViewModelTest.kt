package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.home.HomeUiState
import org.junit.Assert.*
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun `initial state has all dialogs hidden`() {
        val state = HomeUiState()
        assertFalse(state.isShowingChickenConfirm)
        assertFalse(state.isShowingJoinDialog)
        assertFalse(state.isShowingGameRules)
        assertFalse(state.isShowingGameNotFound)
        assertEquals("", state.gameCode)
    }

    @Test
    fun `start button shows join dialog`() {
        val state = HomeUiState()
        val updated = state.copy(isShowingJoinDialog = true)
        assertTrue(updated.isShowingJoinDialog)
    }

    @Test
    fun `rules button shows game rules`() {
        val state = HomeUiState()
        val updated = state.copy(isShowingGameRules = true)
        assertTrue(updated.isShowingGameRules)
    }

    @Test
    fun `game not found shows alert`() {
        val state = HomeUiState()
        val updated = state.copy(isShowingGameNotFound = true)
        assertTrue(updated.isShowingGameNotFound)
    }

    @Test
    fun `initial state has empty hunterName`() {
        val state = HomeUiState()
        assertEquals("", state.hunterName)
    }

    @Test
    fun `hunterName can be updated`() {
        val state = HomeUiState()
        val updated = state.copy(hunterName = "Julien")
        assertEquals("Julien", updated.hunterName)
    }

    @Test
    fun `initial state has null activeGame`() {
        val state = HomeUiState()
        assertNull(state.activeGame)
    }

    @Test
    fun `initial state has null activeGameRole`() {
        val state = HomeUiState()
        assertNull(state.activeGameRole)
    }
}
