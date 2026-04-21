package dev.rahier.pouleparty.powerups.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class PowerUpTest {

    @Test
    fun `typeEnum returns correct PowerUpType from firestoreValue`() {
        val powerUp = PowerUp(type = "radarPing")
        assertEquals(PowerUpType.RADAR_PING, powerUp.typeEnum)
    }

    @Test
    fun `typeEnum defaults to ZONE_PREVIEW for unknown value`() {
        val powerUp = PowerUp(type = "unknown")
        assertEquals(PowerUpType.ZONE_PREVIEW, powerUp.typeEnum)
    }

    @Test
    fun `isCollected returns true when collectedBy is set`() {
        val powerUp = PowerUp(collectedBy = "user123")
        assertTrue(powerUp.isCollected)
    }

    @Test
    fun `isCollected returns false when collectedBy is null`() {
        val powerUp = PowerUp()
        assertFalse(powerUp.isCollected)
    }

    @Test
    fun `isActivated returns true when activatedAt is set`() {
        val powerUp = PowerUp(activatedAt = Timestamp.now())
        assertTrue(powerUp.isActivated)
    }

    @Test
    fun `isActivated returns false when activatedAt is null`() {
        val powerUp = PowerUp()
        assertFalse(powerUp.isActivated)
    }

    @Test
    fun `hunter power-ups are correctly classified`() {
        assertTrue(PowerUpType.ZONE_PREVIEW.isHunterPowerUp)
        assertTrue(PowerUpType.RADAR_PING.isHunterPowerUp)
        assertFalse(PowerUpType.INVISIBILITY.isHunterPowerUp)
        assertFalse(PowerUpType.ZONE_FREEZE.isHunterPowerUp)
    }

    @Test
    fun `duration values are correct`() {
        assertNull(PowerUpType.ZONE_PREVIEW.durationSeconds)
        assertEquals(30L, PowerUpType.RADAR_PING.durationSeconds)
        assertEquals(30L, PowerUpType.INVISIBILITY.durationSeconds)
        assertEquals(120L, PowerUpType.ZONE_FREEZE.durationSeconds)
    }

    @Test
    fun `firestoreValue round-trips correctly`() {
        PowerUpType.entries.forEach { type ->
            assertEquals(type, PowerUpType.fromFirestore(type.firestoreValue))
        }
    }

    @Test
    fun `locationPoint returns correct Point`() {
        val powerUp = PowerUp(location = GeoPoint(50.8466, 4.3528))
        val point = powerUp.locationPoint
        assertEquals(50.8466, point.latitude(), 0.0001)
        assertEquals(4.3528, point.longitude(), 0.0001)
    }

    @Test
    fun `mock is valid`() {
        val mock = PowerUp.mock
        assertTrue(mock.id.isNotEmpty())
        assertEquals(PowerUpType.RADAR_PING, mock.typeEnum)
        assertFalse(mock.isCollected)
        assertFalse(mock.isActivated)
    }

    // MARK: - Firestore decoding contract (regression for v1.6.2)

    /**
     * FirestoreRepository.powerUpsFlow relies on the data class tolerating
     * a missing `id` field: `doc.toObject(PowerUp::class.java)` falls back to
     * the default "" then `.copy(id = doc.id)` injects the document name.
     * Locking that contract in so no-one makes `id` non-defaulted.
     */
    @Test
    fun `id has safe default so toObject never fails when Firestore lacks id`() {
        val withoutId = PowerUp(
            type = "radarPing",
            location = GeoPoint(50.8466, 4.3528),
            spawnedAt = Timestamp(Date(1_800_000_000_000L))
        )
        assertEquals("", withoutId.id)
        val injected = withoutId.copy(id = "pu-0-0-1529788")
        assertEquals("pu-0-0-1529788", injected.id)
        assertEquals(PowerUpType.RADAR_PING, injected.typeEnum)
    }

    /**
     * Server 1.6.2+ writes an explicit `id` field. Round-trip must preserve it
     * even when `.copy(id = doc.id)` is chained afterwards — both should agree.
     */
    @Test
    fun `explicit id from Firestore is preserved through copy`() {
        val fromServer = PowerUp(
            id = "pu-0-0-1529788",
            type = "radarPing",
            location = GeoPoint(50.8466, 4.3528),
            spawnedAt = Timestamp(Date(1_800_000_000_000L))
        )
        val afterCopy = fromServer.copy(id = "pu-0-0-1529788")
        assertEquals("pu-0-0-1529788", afterCopy.id)
    }
}
