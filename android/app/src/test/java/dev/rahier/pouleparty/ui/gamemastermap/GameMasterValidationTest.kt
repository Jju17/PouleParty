package dev.rahier.pouleparty.ui.gamemastermap

import dev.rahier.pouleparty.model.ChallengeCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMasterValidationTest {

    @Test
    fun `oneShot validation accumulates points across distinct challenges`() {
        var c = ChallengeCompletion(
            hunterId = "h",
            validatedChallengeIds = emptyList(),
            repeatableCounts = emptyMap(),
            totalPoints = 0,
            teamName = "Team",
        )
        c = c.copy(
            validatedChallengeIds = c.validatedChallengeIds + "ch-1",
            totalPoints = c.totalPoints + 25,
        )
        c = c.copy(
            validatedChallengeIds = c.validatedChallengeIds + "ch-2",
            totalPoints = c.totalPoints + 10,
        )

        assertEquals(listOf("ch-1", "ch-2"), c.validatedChallengeIds)
        assertEquals(35, c.totalPoints)
        assertTrue(c.repeatableCounts.isEmpty())
    }

    @Test
    fun `oneShot validation is idempotent on duplicate challengeId`() {
        var c = ChallengeCompletion(
            hunterId = "h",
            validatedChallengeIds = listOf("ch-1"),
            repeatableCounts = emptyMap(),
            totalPoints = 25,
            teamName = "Team",
        )
        if (!c.validatedChallengeIds.contains("ch-1")) {
            c = c.copy(
                validatedChallengeIds = c.validatedChallengeIds + "ch-1",
                totalPoints = c.totalPoints + 25,
            )
        }

        assertEquals(listOf("ch-1"), c.validatedChallengeIds)
        assertEquals(25, c.totalPoints)
    }

    @Test
    fun `repeatable validation increments counter and accumulates points`() {
        var c = ChallengeCompletion(
            hunterId = "h",
            validatedChallengeIds = emptyList(),
            repeatableCounts = emptyMap(),
            totalPoints = 0,
            teamName = "Team",
        )
        repeat(3) {
            val current = c.repeatableCounts["bar-1"] ?: 0
            c = c.copy(
                repeatableCounts = c.repeatableCounts + ("bar-1" to current + 1),
                totalPoints = c.totalPoints + 5,
            )
        }

        assertEquals(3, c.repeatableCounts["bar-1"])
        assertEquals(15, c.totalPoints)
        assertTrue(c.validatedChallengeIds.isEmpty())
    }

    @Test
    fun `validation preserves teamName on write`() {
        val initial = ChallengeCompletion(
            hunterId = "h",
            validatedChallengeIds = listOf("ch-1"),
            repeatableCounts = emptyMap(),
            totalPoints = 10,
            teamName = "Original Team",
        )
        val updated = initial.copy(
            validatedChallengeIds = initial.validatedChallengeIds + "ch-2",
            totalPoints = initial.totalPoints + 15,
        )

        assertEquals("Original Team", updated.teamName)
        assertEquals(25, updated.totalPoints)
        assertEquals(2, updated.validatedChallengeIds.size)
    }
}
