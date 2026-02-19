package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class ChickenLocationTest {

    @Test
    fun `default values are set correctly`() {
        val location = ChickenLocation()
        assertEquals(0.0, location.location.latitude, 0.0001)
        assertEquals(0.0, location.location.longitude, 0.0001)
    }

    @Test
    fun `custom values are stored correctly`() {
        val geoPoint = GeoPoint(50.8466, 4.3528)
        val timestamp = Timestamp.now()
        val location = ChickenLocation(location = geoPoint, timestamp = timestamp)

        assertEquals(50.8466, location.location.latitude, 0.0001)
        assertEquals(4.3528, location.location.longitude, 0.0001)
        assertEquals(timestamp, location.timestamp)
    }
}
