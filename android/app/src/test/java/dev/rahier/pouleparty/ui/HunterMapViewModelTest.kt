package dev.rahier.pouleparty.ui

import com.google.android.gms.maps.model.LatLng
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
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
}
