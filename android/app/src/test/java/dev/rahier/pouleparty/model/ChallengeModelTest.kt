package dev.rahier.pouleparty.model

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeModelTest {

    @Test
    fun `default Challenge has oneShot type and nil optionals`() {
        val challenge = Challenge()
        assertEquals(ChallengeType.ONE_SHOT, challenge.typeEnum)
        assertEquals("oneShot", challenge.type)
        assertNull(challenge.location)
        assertNull(challenge.proximityRadiusMeters)
        assertNull(challenge.partner)
    }

    @Test
    fun `default Challenge keeps existing fields with their previous defaults`() {
        val challenge = Challenge()
        assertEquals("", challenge.id)
        assertEquals("", challenge.title)
        assertEquals("", challenge.body)
        assertEquals(0, challenge.points)
        assertNotNull(challenge.lastUpdated)
    }

    @Test
    fun `typeEnum maps oneShot string to ONE_SHOT`() {
        val challenge = Challenge(type = "oneShot")
        assertEquals(ChallengeType.ONE_SHOT, challenge.typeEnum)
    }

    @Test
    fun `typeEnum maps repeatable string to REPEATABLE`() {
        val challenge = Challenge(type = "repeatable")
        assertEquals(ChallengeType.REPEATABLE, challenge.typeEnum)
    }

    @Test
    fun `typeEnum falls back to ONE_SHOT for unknown raw value`() {
        val challenge = Challenge(type = "dailyOnce")
        assertEquals(ChallengeType.ONE_SHOT, challenge.typeEnum)
    }

    @Test
    fun `typeEnum falls back to ONE_SHOT for empty string`() {
        val challenge = Challenge(type = "")
        assertEquals(ChallengeType.ONE_SHOT, challenge.typeEnum)
    }

    @Test
    fun `Challenge can carry GeoPoint location and proximity radius`() {
        val challenge = Challenge(
            id = "bar-le-cirio",
            title = "Drink a beer at Le Cirio",
            type = "repeatable",
            points = 5,
            location = GeoPoint(50.8503, 4.3517),
            proximityRadiusMeters = 50,
            partner = "InBev",
        )
        assertEquals(50.8503, challenge.location?.latitude)
        assertEquals(4.3517, challenge.location?.longitude)
        assertEquals(50, challenge.proximityRadiusMeters)
        assertEquals("InBev", challenge.partner)
        assertEquals(ChallengeType.REPEATABLE, challenge.typeEnum)
    }

    @Test
    fun `proximityRadiusMeters can be nil explicitly`() {
        // `null` = no proximity check (distinct from the default 100 m
        // the migration sets).
        val challenge = Challenge(
            id = "anywhere",
            type = "oneShot",
            location = GeoPoint(0.0, 0.0),
            proximityRadiusMeters = null,
        )
        assertNull(challenge.proximityRadiusMeters)
        assertNotNull(challenge.location)
    }

    @Test
    fun `ChallengeType raw values stay in lockstep with iOS`() {
        assertEquals("oneShot", ChallengeType.ONE_SHOT.firestoreValue)
        assertEquals("repeatable", ChallengeType.REPEATABLE.firestoreValue)
    }

    @Test
    fun `ChallengeType has exactly two values`() {
        assertEquals(2, ChallengeType.values().size)
    }

    @Test
    fun `fromFirestore is null-safe`() {
        assertEquals(ChallengeType.ONE_SHOT, ChallengeType.fromFirestore(null))
    }
}
