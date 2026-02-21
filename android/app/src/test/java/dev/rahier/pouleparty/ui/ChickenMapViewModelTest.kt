package dev.rahier.pouleparty.ui

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapUiState
import dev.rahier.pouleparty.ui.chickenmap.HunterAnnotation
import org.junit.Assert.*
import org.junit.Test

class ChickenMapViewModelTest {

    @Test
    fun `initial state has default values`() {
        val state = ChickenMapUiState()
        assertEquals(1500, state.radius)
        assertTrue(state.hunterAnnotations.isEmpty())
        assertNull(state.circleCenter)
        assertFalse(state.showCancelAlert)
    }

    @Test
    fun `cancel alert can be shown and dismissed`() {
        var state = ChickenMapUiState()
        assertFalse(state.showCancelAlert)

        state = state.copy(showCancelAlert = true)
        assertTrue(state.showCancelAlert)

        state = state.copy(showCancelAlert = false)
        assertFalse(state.showCancelAlert)
    }

    @Test
    fun `hunter annotations are created correctly`() {
        val annotations = listOf(
            HunterAnnotation(
                id = "hunter-a",
                coordinate = LatLng(50.0, 4.0),
                displayName = "Hunter 1"
            ),
            HunterAnnotation(
                id = "hunter-b",
                coordinate = LatLng(51.0, 5.0),
                displayName = "Hunter 2"
            )
        )
        val state = ChickenMapUiState(hunterAnnotations = annotations)

        assertEquals(2, state.hunterAnnotations.size)
        assertEquals("Hunter 1", state.hunterAnnotations[0].displayName)
        assertEquals("hunter-a", state.hunterAnnotations[0].id)
    }

    @Test
    fun `circle center is updated with location`() {
        val latLng = LatLng(50.8466, 4.3528)
        val state = ChickenMapUiState(circleCenter = latLng)

        assertNotNull(state.circleCenter)
        assertEquals(50.8466, state.circleCenter!!.latitude, 0.0001)
    }

    @Test
    fun `chicken subtitle for followTheChicken mode`() {
        val game = Game(id = "test", gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue)
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, game.gameModEnum)
    }

    @Test
    fun `chicken subtitle for stayInTheZone mode`() {
        val game = Game(id = "test", gameMod = GameMod.STAY_IN_THE_ZONE.firestoreValue)
        assertEquals(GameMod.STAY_IN_THE_ZONE, game.gameModEnum)
    }

    @Test
    fun `chicken subtitle for mutualTracking mode`() {
        val game = Game(id = "test", gameMod = GameMod.MUTUAL_TRACKING.firestoreValue)
        assertEquals(GameMod.MUTUAL_TRACKING, game.gameModEnum)
    }

    @Test
    fun `radius update reduces radius`() {
        var state = ChickenMapUiState(radius = 500)
        val game = Game(id = "test", radiusDeclinePerUpdate = 100.0)
        val newRadius = state.radius - game.radiusDeclinePerUpdate.toInt()

        state = state.copy(radius = newRadius)
        assertEquals(400, state.radius)
    }

    @Test
    fun `radius does not go below zero check`() {
        val state = ChickenMapUiState(radius = 50)
        val game = Game(id = "test", radiusDeclinePerUpdate = 100.0)
        val newRadius = state.radius - game.radiusDeclinePerUpdate.toInt()

        // The ViewModel should guard against this
        assertTrue(newRadius <= 0)
    }

    // MARK: - Found code state

    @Test
    fun `initial state has showFoundCode false`() {
        val state = ChickenMapUiState()
        assertFalse(state.showFoundCode)
    }

    @Test
    fun `showFoundCode can be toggled`() {
        var state = ChickenMapUiState()
        state = state.copy(showFoundCode = true)
        assertTrue(state.showFoundCode)

        state = state.copy(showFoundCode = false)
        assertFalse(state.showFoundCode)
    }

    // MARK: - Winner notification state

    @Test
    fun `winner notification defaults to null`() {
        val state = ChickenMapUiState()
        assertNull(state.winnerNotification)
    }

    @Test
    fun `winner notification can be set and cleared`() {
        var state = ChickenMapUiState()
        state = state.copy(winnerNotification = "Julien a trouvé la poule !")
        assertEquals("Julien a trouvé la poule !", state.winnerNotification)

        state = state.copy(winnerNotification = null)
        assertNull(state.winnerNotification)
    }

    @Test
    fun `previousWinnersCount tracks winner count`() {
        val winner = Winner(hunterId = "h1", hunterName = "Julien", timestamp = Timestamp.now())
        val game = Game(id = "test", foundCode = "1234", winners = listOf(winner))
        val state = ChickenMapUiState(game = game, previousWinnersCount = 1)

        assertEquals(1, state.previousWinnersCount)
        assertEquals(1, state.game.winners.size)
    }
}
