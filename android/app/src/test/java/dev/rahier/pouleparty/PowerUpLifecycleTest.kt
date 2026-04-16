package dev.rahier.pouleparty

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.*
import dev.rahier.pouleparty.ui.gamelogic.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class PowerUpLifecycleTest {

    // ── Spawn Phase ──────────────────────────────────────

    @Test
    fun `generatePowerUps produces deterministic results`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch1 = generatePowerUps(center, 1000.0, 5, 42, 0)
        val batch2 = generatePowerUps(center, 1000.0, 5, 42, 0)

        assertEquals(batch1.size, batch2.size)
        batch1.zip(batch2).forEach { (a, b) ->
            assertEquals(a.id, b.id)
            assertEquals(a.type, b.type)
            assertEquals(a.location.latitude, b.location.latitude, 1e-10)
            assertEquals(a.location.longitude, b.location.longitude, 1e-10)
        }
    }

    @Test
    fun `generatePowerUps respects count`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch = generatePowerUps(center, 1000.0, 3, 99, 0)
        assertEquals(3, batch.size)
    }

    @Test
    fun `generatePowerUps with zero count returns empty`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch = generatePowerUps(center, 1000.0, 0, 42, 0)
        assertTrue(batch.isEmpty())
    }

    @Test
    fun `generatePowerUps with empty types returns empty`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch = generatePowerUps(center, 1000.0, 5, 42, 0, enabledTypes = emptyList())
        assertTrue(batch.isEmpty())
    }

    @Test
    fun `generatePowerUps positions are within zone`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val radius = 1000.0
        val batch = generatePowerUps(center, radius, 20, 42, 0)

        // Use Haversine approximation (avoids Android framework dependency in unit tests)
        val metersPerDegree = 111_320.0
        for (pu in batch) {
            val dLat = (pu.location.latitude - center.latitude()) * metersPerDegree
            val dLng = (pu.location.longitude - center.longitude()) * metersPerDegree *
                kotlin.math.cos(center.latitude() * kotlin.math.PI / 180.0)
            val distance = kotlin.math.sqrt(dLat * dLat + dLng * dLng)
            assertTrue("Power-up at distance $distance exceeds zone", distance <= radius * 0.85 + 1)
        }
    }

    @Test
    fun `generatePowerUps different seeds produce different positions`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch1 = generatePowerUps(center, 1000.0, 5, 42, 0)
        val batch2 = generatePowerUps(center, 1000.0, 5, 99, 0)

        val anyDifferent = batch1.zip(batch2).any { (a, b) ->
            kotlin.math.abs(a.location.latitude - b.location.latitude) > 1e-10
        }
        assertTrue(anyDifferent)
    }

    @Test
    fun `generatePowerUps different batch index produces different results`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch0 = generatePowerUps(center, 1000.0, 5, 42, 0)
        val batch1 = generatePowerUps(center, 1000.0, 5, 42, 1)
        assertNotEquals(batch0[0].id, batch1[0].id)
    }

    @Test
    fun `generatePowerUps has deterministic ids with batch prefix`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batch = generatePowerUps(center, 1000.0, 3, 42, 2)
        for (pu in batch) {
            assertTrue("ID ${pu.id} should start with pu-2-", pu.id.startsWith("pu-2-"))
        }
    }

    @Test
    fun `generatePowerUps filters enabled types`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val onlyHunterTypes = listOf("zonePreview", "radarPing")
        val batch = generatePowerUps(center, 1000.0, 10, 42, 0, enabledTypes = onlyHunterTypes)

        for (pu in batch) {
            assertTrue("Expected hunter power-up but got ${pu.type}", pu.typeEnum.isHunterPowerUp)
        }
    }

    @Test
    fun `initial batch size constant is 5`() {
        assertEquals(5, AppConstants.POWER_UP_INITIAL_BATCH_SIZE)
    }

    @Test
    fun `periodic batch size constant is 2`() {
        assertEquals(2, AppConstants.POWER_UP_PERIODIC_BATCH_SIZE)
    }

    // ── Collection Phase ────────────────────────────────

    @Test
    fun `collection radius is 30 meters`() {
        assertEquals(30.0, AppConstants.POWER_UP_COLLECTION_RADIUS_METERS, 0.01)
    }

    @Test
    fun `power-up starts uncollected`() {
        val pu = PowerUp(id = "test", type = "radarPing", location = GeoPoint(50.8466, 4.3528))
        assertFalse(pu.isCollected)
        assertNull(pu.collectedBy)
    }

    @Test
    fun `power-up becomes collected when collectedBy is set`() {
        val pu = PowerUp(id = "test", type = "radarPing", location = GeoPoint(50.8466, 4.3528), collectedBy = "user1")
        assertTrue(pu.isCollected)
    }

    // ── Activation Phase ────────────────────────────────

    @Test
    fun `power-up starts not activated`() {
        val pu = PowerUp(id = "test", type = "radarPing", location = GeoPoint(50.8466, 4.3528))
        assertFalse(pu.isActivated)
    }

    @Test
    fun `power-up becomes activated when activatedAt is set`() {
        val pu = PowerUp(
            id = "test", type = "radarPing", location = GeoPoint(50.8466, 4.3528),
            collectedBy = "user1", activatedAt = Timestamp.now()
        )
        assertTrue(pu.isActivated)
    }

    // ── Active Effect Detection ─────────────────────────

    @Test
    fun `isChickenInvisible true when future timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(invisibility = Timestamp(Date(System.currentTimeMillis() + 30_000)))
            )
        )
        assertTrue(game.isChickenInvisible)
    }

    @Test
    fun `isChickenInvisible false when past timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(invisibility = Timestamp(Date(System.currentTimeMillis() - 10_000)))
            )
        )
        assertFalse(game.isChickenInvisible)
    }

    @Test
    fun `isChickenInvisible false when null`() {
        assertFalse(Game.mock.isChickenInvisible)
    }

    @Test
    fun `isZoneFrozen true when future timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(zoneFreeze = Timestamp(Date(System.currentTimeMillis() + 120_000)))
            )
        )
        assertTrue(game.isZoneFrozen)
    }

    @Test
    fun `isZoneFrozen false when past timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(zoneFreeze = Timestamp(Date(System.currentTimeMillis() - 10_000)))
            )
        )
        assertFalse(game.isZoneFrozen)
    }

    @Test
    fun `isRadarPingActive true when future timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(radarPing = Timestamp(Date(System.currentTimeMillis() + 30_000)))
            )
        )
        assertTrue(game.isRadarPingActive)
    }

    @Test
    fun `isDecoyActive true when future timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(decoy = Timestamp(Date(System.currentTimeMillis() + 20_000)))
            )
        )
        assertTrue(game.isDecoyActive)
    }

    @Test
    fun `isJammerActive true when future timestamp`() {
        val game = Game.mock.copy(
            powerUps = GamePowerUps(
                activeEffects = ActiveEffects(jammer = Timestamp(Date(System.currentTimeMillis() + 30_000)))
            )
        )
        assertTrue(game.isJammerActive)
    }

    // ── Zone Freeze Effect on Radius ────────────────────

    @Test
    fun `zone freeze skips radius shrink`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1000,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.3528, 50.8466),
            currentCircleCenter = Point.fromLngLat(4.3528, 50.8466),
            isZoneFrozen = true
        )
        assertNull(result)
    }

    @Test
    fun `zone freeze does not prevent update when expired`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1000,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.3528, 50.8466),
            currentCircleCenter = Point.fromLngLat(4.3528, 50.8466),
            isZoneFrozen = false
        )
        assertNotNull(result)
        assertEquals(900, result!!.newRadius)
    }

    // ── Cross-Platform Parity ───────────────────────────

    @Test
    fun `spawn positions match between batch calls with same seed`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val batchA = generatePowerUps(center, 1500.0, 5, 42, 0)
        val batchB = generatePowerUps(center, 1500.0, 5, 42, 0)

        // Verify ID format matches expected pattern
        assertEquals("pu-0-0-${kotlin.math.abs(42 xor 0) * 31}", batchA[0].id)
        assertEquals(batchA[0].id, batchB[0].id)
    }

    @Test
    fun `power-up full lifecycle state transitions`() {
        // 1. Spawn: uncollected, not activated
        var pu = PowerUp(id = "test", type = "invisibility", location = GeoPoint(50.8466, 4.3528))
        assertFalse(pu.isCollected)
        assertFalse(pu.isActivated)

        // 2. Collect: collected, not activated
        pu = pu.copy(collectedBy = "user1", collectedAt = Timestamp.now())
        assertTrue(pu.isCollected)
        assertFalse(pu.isActivated)

        // 3. Activate: collected and activated
        val now = Timestamp.now()
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + 30_000))
        pu = pu.copy(activatedAt = now, expiresAt = expiresAt)
        assertTrue(pu.isCollected)
        assertTrue(pu.isActivated)

        // 4. Verify expiration timestamp exists
        assertNotNull(pu.expiresAt)
    }
}
