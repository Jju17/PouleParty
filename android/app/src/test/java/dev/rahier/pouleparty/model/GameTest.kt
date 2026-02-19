package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import java.util.UUID

class GameTest {

    @Test
    fun `gameCode is first 6 chars uppercased`() {
        val game = Game(id = "abcdef-1234-5678")
        assertEquals("ABCDEF", game.gameCode)
    }

    @Test
    fun `gameCode is uppercased`() {
        val game = Game(id = "xyz123")
        assertEquals("XYZ123", game.gameCode)
    }

    @Test
    fun `gameCode from short id uses full id`() {
        val game = Game(id = "abc")
        assertEquals("ABC", game.gameCode)
    }

    @Test
    fun `findLastUpdate returns initial radius before game starts`() {
        val game = Game(
            id = "test",
            startTimestamp = Timestamp(Date(System.currentTimeMillis() + 600_000)),
            endTimestamp = Timestamp(Date(System.currentTimeMillis() + 3_600_000)),
            initialRadius = 1500.0,
            radiusDeclinePerUpdate = 100.0
        )
        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `findLastUpdate shrinks radius after game started`() {
        val game = Game(
            id = "test",
            radiusIntervalUpdate = 5.0,
            startTimestamp = Timestamp(Date(System.currentTimeMillis() - 600_000)), // started 10 min ago
            endTimestamp = Timestamp(Date(System.currentTimeMillis() + 3_600_000)),
            initialRadius = 1500.0,
            radiusDeclinePerUpdate = 100.0
        )
        val (_, radius) = game.findLastUpdate()
        assertTrue("Radius should have shrunk", radius < 1500)
    }

    @Test
    fun `startDate and endDate conversion`() {
        val now = Date()
        val game = Game(
            id = "test",
            startTimestamp = Timestamp(now),
            endTimestamp = Timestamp(Date(now.time + 3_600_000))
        )
        assertTrue(Math.abs(game.startDate.time - now.time) < 1000)
    }

    @Test
    fun `initialLocation returns correct LatLng`() {
        val game = Game(
            id = "test",
            initialCoordinates = GeoPoint(48.8566, 2.3522)
        )
        assertEquals(48.8566, game.initialLocation.latitude, 0.0001)
        assertEquals(2.3522, game.initialLocation.longitude, 0.0001)
    }

    @Test
    fun `withStartDate creates new game with updated start`() {
        val game = Game(id = "test")
        val newDate = Date(System.currentTimeMillis() + 1_000_000)
        val updated = game.withStartDate(newDate)

        assertTrue(Math.abs(updated.startDate.time - newDate.time) < 1000)
        assertEquals(game.id, updated.id) // id unchanged
    }

    @Test
    fun `withEndDate creates new game with updated end`() {
        val game = Game(id = "test")
        val newDate = Date(System.currentTimeMillis() + 5_000_000)
        val updated = game.withEndDate(newDate)

        assertTrue(Math.abs(updated.endDate.time - newDate.time) < 1000)
    }

    @Test
    fun `withInitialLocation creates new game with updated coordinates`() {
        val game = Game(id = "test")
        val latLng = com.google.android.gms.maps.model.LatLng(48.8566, 2.3522)
        val updated = game.withInitialLocation(latLng)

        assertEquals(48.8566, updated.initialLocation.latitude, 0.0001)
        assertEquals(2.3522, updated.initialLocation.longitude, 0.0001)
    }

    // MARK: - GameMod tests

    @Test
    fun `default game mod is followTheChicken`() {
        val game = Game(id = "test")
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, game.gameModEnum)
    }

    @Test
    fun `all game mods have correct titles`() {
        assertEquals("Follow the chicken \uD83D\uDC14", GameMod.FOLLOW_THE_CHICKEN.title)
        assertEquals("Stay in tha zone \uD83D\uDCCD", GameMod.STAY_IN_THE_ZONE.title)
        assertEquals("Mutual tracking \uD83D\uDC40", GameMod.MUTUAL_TRACKING.title)
    }

    @Test
    fun `GameMod fromFirestore returns correct enum`() {
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, GameMod.fromFirestore("followTheChicken"))
        assertEquals(GameMod.STAY_IN_THE_ZONE, GameMod.fromFirestore("stayInTheZone"))
        assertEquals(GameMod.MUTUAL_TRACKING, GameMod.fromFirestore("mutualTracking"))
    }

    @Test
    fun `GameMod fromFirestore with unknown value defaults to followTheChicken`() {
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, GameMod.fromFirestore("unknownMode"))
    }

    @Test
    fun `GameMod has exactly 3 entries`() {
        assertEquals(3, GameMod.entries.size)
    }

    @Test
    fun `GameMod firestoreValue roundtrip`() {
        GameMod.entries.forEach { mod ->
            assertEquals(mod, GameMod.fromFirestore(mod.firestoreValue))
        }
    }

    @Test
    fun `mock game is valid`() {
        val mock = Game.mock
        assertTrue(mock.id.isNotEmpty())
        assertEquals(10, mock.numberOfPlayers)
        assertEquals(1500.0, mock.initialRadius, 0.01)
    }
}
