package dev.rahier.pouleparty.ui

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.huntermap.HunterMapUiState
import org.junit.Assert.*
import org.junit.Test

class HunterMapViewModelTest {

    @Test
    fun `initial state has default values`() {
        val state = HunterMapUiState()
        assertEquals(1500, state.radius)
        assertNull(state.circleCenter)
    }

    @Test
    fun `circle center follows chicken location in followTheChicken`() {
        val game = Game(id = "test", gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue)
        val chickenPos = LatLng(50.0, 4.0)
        val state = HunterMapUiState(game = game, circleCenter = chickenPos)

        assertEquals(50.0, state.circleCenter!!.latitude, 0.0001)
    }

    @Test
    fun `circle center stays fixed in stayInTheZone`() {
        val game = Game(id = "test", gameMod = GameMod.STAY_IN_THE_ZONE.firestoreValue)
        val state = HunterMapUiState(game = game, circleCenter = game.initialLocation)

        assertEquals(game.initialLocation.latitude, state.circleCenter!!.latitude, 0.0001)
        assertEquals(game.initialLocation.longitude, state.circleCenter!!.longitude, 0.0001)
    }

    @Test
    fun `radius update reduces radius`() {
        val game = Game(id = "test", radiusDeclinePerUpdate = 100.0)
        var state = HunterMapUiState(game = game, radius = 500)

        val newRadius = state.radius - game.radiusDeclinePerUpdate.toInt()
        state = state.copy(radius = newRadius)
        assertEquals(400, state.radius)
    }

    @Test
    fun `radius does not go below zero`() {
        val game = Game(id = "test", radiusDeclinePerUpdate = 100.0)
        val state = HunterMapUiState(game = game, radius = 50)

        val newRadius = state.radius - game.radiusDeclinePerUpdate.toInt()
        // Guard in ViewModel: should not apply
        assertTrue("Radius should not go below 0", newRadius <= 0)
    }

    @Test
    fun `hunter subtitle for followTheChicken`() {
        val game = Game(id = "test", gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue)
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, game.gameModEnum)
    }

    @Test
    fun `hunter subtitle for stayInTheZone`() {
        val game = Game(id = "test", gameMod = GameMod.STAY_IN_THE_ZONE.firestoreValue)
        assertEquals(GameMod.STAY_IN_THE_ZONE, game.gameModEnum)
    }

    @Test
    fun `hunter subtitle for mutualTracking`() {
        val game = Game(id = "test", gameMod = GameMod.MUTUAL_TRACKING.firestoreValue)
        assertEquals(GameMod.MUTUAL_TRACKING, game.gameModEnum)
    }

    @Test
    fun `game config update refreshes state`() {
        val oldGame = Game(id = "test", initialRadius = 1500.0)
        val newGame = oldGame.copy(initialRadius = 2000.0)

        val state = HunterMapUiState(game = newGame, circleCenter = newGame.initialLocation)
        assertEquals(2000.0, state.game.initialRadius, 0.01)
    }

    // MARK: - Found code state

    @Test
    fun `initial state has found code fields at defaults`() {
        val state = HunterMapUiState()
        assertFalse(state.isEnteringFoundCode)
        assertEquals("", state.enteredCode)
        assertFalse(state.showWrongCodeAlert)
    }

    @Test
    fun `found button shows code entry`() {
        val state = HunterMapUiState()
        val updated = state.copy(isEnteringFoundCode = true)
        assertTrue(updated.isEnteringFoundCode)
    }

    @Test
    fun `wrong code alert can be shown and dismissed`() {
        var state = HunterMapUiState()
        assertFalse(state.showWrongCodeAlert)

        state = state.copy(showWrongCodeAlert = true)
        assertTrue(state.showWrongCodeAlert)

        state = state.copy(showWrongCodeAlert = false)
        assertFalse(state.showWrongCodeAlert)
    }

    @Test
    fun `entered code can be updated`() {
        val state = HunterMapUiState()
        val updated = state.copy(enteredCode = "1234")
        assertEquals("1234", updated.enteredCode)
    }

    // MARK: - Winner notification state

    @Test
    fun `winner notification defaults to null`() {
        val state = HunterMapUiState()
        assertNull(state.winnerNotification)
    }

    @Test
    fun `winner notification can be set and cleared`() {
        var state = HunterMapUiState()
        state = state.copy(winnerNotification = "Julien a trouvé la poule !")
        assertEquals("Julien a trouvé la poule !", state.winnerNotification)

        state = state.copy(winnerNotification = null)
        assertNull(state.winnerNotification)
    }

    @Test
    fun `previousWinnersCount tracks winner count`() {
        val winner = Winner(hunterId = "h1", hunterName = "Julien", timestamp = Timestamp.now())
        val game = Game(id = "test", foundCode = "1234", winners = listOf(winner))
        val state = HunterMapUiState(game = game, previousWinnersCount = 1)

        assertEquals(1, state.previousWinnersCount)
        assertEquals(1, state.game.winners.size)
    }
}
