package dev.rahier.pouleparty.model

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
}
