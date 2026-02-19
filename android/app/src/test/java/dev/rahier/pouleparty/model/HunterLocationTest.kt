package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class HunterLocationTest {

    @Test
    fun `default values are set correctly`() {
        val location = HunterLocation()
        assertEquals("", location.hunterId)
        assertEquals(0.0, location.location.latitude, 0.0001)
        assertEquals(0.0, location.location.longitude, 0.0001)
    }

    @Test
    fun `custom values are stored correctly`() {
        val location = HunterLocation(
            hunterId = "hunter-123",
            location = GeoPoint(51.0, 5.0),
            timestamp = Timestamp.now()
        )

        assertEquals("hunter-123", location.hunterId)
        assertEquals(51.0, location.location.latitude, 0.0001)
        assertEquals(5.0, location.location.longitude, 0.0001)
    }

    @Test
    fun `data class equality works`() {
        val timestamp = Timestamp.now()
        val geoPoint = GeoPoint(50.0, 4.0)

        val a = HunterLocation(hunterId = "h1", location = geoPoint, timestamp = timestamp)
        val b = HunterLocation(hunterId = "h1", location = geoPoint, timestamp = timestamp)

        assertEquals(a, b)
    }

    @Test
    fun `data class copy works`() {
        val original = HunterLocation(
            hunterId = "h1",
            location = GeoPoint(50.0, 4.0),
            timestamp = Timestamp.now()
        )
        val copy = original.copy(hunterId = "h2")

        assertEquals("h2", copy.hunterId)
        assertEquals(original.location, copy.location)
    }
}
