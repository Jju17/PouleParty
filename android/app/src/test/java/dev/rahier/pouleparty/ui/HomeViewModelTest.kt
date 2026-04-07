package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.home.HomeUiState
import org.junit.Assert.*
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun `initial state has all dialogs hidden`() {
        val state = HomeUiState()
        assertFalse(state.isShowingJoinSheet)
        assertFalse(state.isShowingGameRules)
        assertFalse(state.isShowingGameNotFound)
        assertEquals("", state.gameCode)
    }

    @Test
    fun `start button shows join sheet`() {
        val state = HomeUiState()
        val updated = state.copy(isShowingJoinSheet = true)
        assertTrue(updated.isShowingJoinSheet)
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
    fun `initial state has empty teamName`() {
        val state = HomeUiState()
        assertEquals("", state.teamName)
    }

    @Test
    fun `teamName can be updated`() {
        val state = HomeUiState()
        val updated = state.copy(teamName = "The Foxes")
        assertEquals("The Foxes", updated.teamName)
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
