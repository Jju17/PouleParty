package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class ChickenConfigViewModelTest {

    @Test
    fun `initial state has game with correct defaults`() {
        val state = ChickenConfigUiState()
        assertEquals(Game.mock.initialRadius, state.game.initialRadius, 0.01)
        assertFalse(state.codeCopied)
        assertFalse(state.showAlert)
    }

    @Test
    fun `game code is 6 characters`() {
        val game = Game(id = "abcdef-1234")
        assertEquals(6, game.gameCode.length)
    }

    @Test
    fun `updating game mod changes state`() {
        val game = Game(id = "test", gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue)
        val updated = game.copy(gameMod = GameMod.MUTUAL_TRACKING.firestoreValue)
        assertEquals(GameMod.MUTUAL_TRACKING, updated.gameModEnum)
    }

    @Test
    fun `updating radius interval changes state`() {
        val game = Game(id = "test", radiusIntervalUpdate = 5.0)
        val updated = game.copy(radiusIntervalUpdate = 10.0)
        assertEquals(10.0, updated.radiusIntervalUpdate, 0.01)
    }

    @Test
    fun `updating radius decline changes state`() {
        val game = Game(id = "test", radiusDeclinePerUpdate = 100.0)
        val updated = game.copy(radiusDeclinePerUpdate = 200.0)
        assertEquals(200.0, updated.radiusDeclinePerUpdate, 0.01)
    }

    @Test
    fun `updating start date creates new game`() {
        val game = Game(id = "test")
        val newDate = Date(System.currentTimeMillis() + 1_000_000)
        val updated = game.withStartDate(newDate)

        assertNotSame(game, updated)
        assertTrue(Math.abs(updated.startDate.time - newDate.time) < 1000)
    }

    @Test
    fun `alert state can be shown and dismissed`() {
        var state = ChickenConfigUiState()
        assertFalse(state.showAlert)

        state = state.copy(showAlert = true, alertMessage = "Error")
        assertTrue(state.showAlert)
        assertEquals("Error", state.alertMessage)

        state = state.copy(showAlert = false)
        assertFalse(state.showAlert)
    }
}
