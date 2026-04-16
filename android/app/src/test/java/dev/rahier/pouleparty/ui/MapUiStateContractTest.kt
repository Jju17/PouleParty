package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapUiState
import dev.rahier.pouleparty.ui.huntermap.HunterMapUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the [MapUiState] interface contract: both ChickenMapUiState
 * and HunterMapUiState expose the shared map UI surface so that
 * shared composables (MapHapticsEffect, MapBottomBar in iOS parlance)
 * can read identical fields regardless of role.
 */
class MapUiStateContractTest {

    @Test
    fun `ChickenMapUiState satisfies MapUiState contract`() {
        val game = Game.mock
        val state: MapUiState = ChickenMapUiState(game = game)
        assertEquals(game.id, state.game.id)
        assertEquals(1500, state.radius)
        assertNull(state.circleCenter)
        assertNull(state.winnerNotification)
        assertNull(state.countdownNumber)
        assertFalse(state.isOutsideZone)
        assertTrue(state.availablePowerUps.isEmpty())
        assertTrue(state.collectedPowerUps.isEmpty())
        assertFalse(state.showGameInfo)
        assertFalse(state.showPowerUpInventory)
    }

    @Test
    fun `HunterMapUiState satisfies MapUiState contract`() {
        val game = Game.mock
        val state: MapUiState = HunterMapUiState(game = game)
        assertEquals(game.id, state.game.id)
        assertEquals(1500, state.radius)
        assertNull(state.circleCenter)
        assertNull(state.winnerNotification)
    }

    // MARK: - PowerUpsUiState projection

    @Test
    fun `powerUps projection exposes the same fields`() {
        val powerUp = PowerUp(
            id = "pu1",
            type = "invisibility",
            location = com.google.firebase.firestore.GeoPoint(50.0, 4.0),
            spawnedAt = com.google.firebase.Timestamp.now()
        )
        val state: MapUiState = ChickenMapUiState(
            game = Game.mock,
            availablePowerUps = listOf(powerUp),
            collectedPowerUps = emptyList(),
            showPowerUpInventory = true,
            powerUpNotification = "Activated!",
            lastActivatedPowerUpType = PowerUpType.INVISIBILITY
        )
        val projection = state.powerUps()
        assertEquals(1, projection.available.size)
        assertTrue(projection.collected.isEmpty())
        assertTrue(projection.showInventory)
        assertEquals("Activated!", projection.notification)
        assertEquals(PowerUpType.INVISIBILITY, projection.lastActivatedType)
        assertNull(projection.activatingId)
    }

    @Test
    fun `powerUps projection accepts an explicit activatingId`() {
        val state: MapUiState = HunterMapUiState(game = Game.mock)
        val projection = state.powerUps(activatingId = "in-flight")
        assertEquals("in-flight", projection.activatingId)
    }

    // ── Edge cases ─────────────────────────────────────────

    @Test
    fun `defaults match between Chicken and Hunter UI state`() {
        val chicken: MapUiState = ChickenMapUiState(game = Game.mock)
        val hunter: MapUiState = HunterMapUiState(game = Game.mock)
        assertEquals(chicken.radius, hunter.radius)
        assertEquals(chicken.showGameInfo, hunter.showGameInfo)
        assertEquals(chicken.showPowerUpInventory, hunter.showPowerUpInventory)
        assertEquals(chicken.isOutsideZone, hunter.isOutsideZone)
        assertEquals(chicken.availablePowerUps, hunter.availablePowerUps)
        assertEquals(chicken.collectedPowerUps, hunter.collectedPowerUps)
    }

    @Test
    fun `powerUps projection on empty state returns empty lists and null fields`() {
        val state: MapUiState = ChickenMapUiState(game = Game.mock)
        val projection = state.powerUps()
        assertTrue(projection.available.isEmpty())
        assertTrue(projection.collected.isEmpty())
        assertNull(projection.notification)
        assertNull(projection.lastActivatedType)
        assertNull(projection.activatingId)
        assertFalse(projection.showInventory)
    }

    @Test
    fun `MapUiState contract values reflect mutations through copy`() {
        val initial = ChickenMapUiState(game = Game.mock)
        val updated = initial.copy(
            radius = 750,
            isOutsideZone = true,
            countdownNumber = 3,
            countdownText = "Go!",
            powerUpNotification = "Hello"
        )
        val surface: MapUiState = updated
        assertEquals(750, surface.radius)
        assertTrue(surface.isOutsideZone)
        assertEquals(3, surface.countdownNumber)
        assertEquals("Go!", surface.countdownText)
        assertEquals("Hello", surface.powerUpNotification)
    }
}
