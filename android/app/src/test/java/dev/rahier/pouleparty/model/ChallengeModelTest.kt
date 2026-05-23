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
    fun `default Challenge has level 1 and number 0 sentinel`() {
        val challenge = Challenge()
        assertEquals(1, challenge.level)
        assertEquals(0, challenge.number)
    }

    @Test
    fun `Challenge keeps explicit level and number when set`() {
        val challenge = Challenge(level = 2, number = 7)
        assertEquals(2, challenge.level)
        assertEquals(7, challenge.number)
    }

    @Test
    fun `Challenge accepts number 0 as sentinel for not-yet-numbered`() {
        // 0 = "not yet numbered". `migrateChallengesV2` assigns a real
        // value at next deploy but a freshly seeded doc can land here.
        val challenge = Challenge(level = 3, number = 0)
        assertEquals(0, challenge.number)
    }

    @Test
    fun `Challenge round-trips level and number via copy`() {
        // Firestore `toObject()` calls the no-arg constructor and
        // injects properties; copy() mirrors that contract for the
        // values we care about staying intact.
        val original = Challenge(
            id = "lvl2-7",
            title = "Pyramide",
            type = "repeatable",
            points = 100,
            level = 2,
            number = 7,
        )
        val roundTripped = original.copy()
        assertEquals(2, roundTripped.level)
        assertEquals(7, roundTripped.number)
        assertEquals(ChallengeType.REPEATABLE, roundTripped.typeEnum)
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
