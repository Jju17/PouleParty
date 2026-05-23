package dev.rahier.pouleparty.ui.gamelogic

import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeProgressTest {

    private fun challenge(
        id: String,
        level: Int,
        type: ChallengeType = ChallengeType.ONE_SHOT,
    ): Challenge = Challenge(
        id = id,
        title = id,
        points = 10,
        type = type.firestoreValue,
        level = level,
        number = 1,
    )

    @Test
    fun `level 1 is always unlocked`() {
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 1,
                challenges = emptyList(),
                validatedChallengeIds = emptySet(),
            )
        )
    }

    @Test
    fun `next level unlocked when previous has only repeatable`() {
        val challenges = (1..3).map { challenge("rep-$it", level = 1, type = ChallengeType.REPEATABLE) }
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = emptySet(),
            )
        )
    }

    @Test
    fun `one out of one oneShot unlocks next`() {
        val challenges = listOf(challenge("a", level = 1))
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = setOf("a"),
            )
        )
    }

    @Test
    fun `four out of five oneShot unlocks next`() {
        val challenges = (1..5).map { challenge("c$it", level = 1) }
        val validated = setOf("c1", "c2", "c3", "c4")
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = challenges,
            validatedChallengeIds = validated,
        )
        assertEquals(4, progress.validated)
        assertEquals(5, progress.total)
        assertEquals(4, progress.threshold)
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `three out of five oneShot keeps next locked`() {
        val challenges = (1..5).map { challenge("c$it", level = 1) }
        val validated = setOf("c1", "c2", "c3")
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = challenges,
            validatedChallengeIds = validated,
        )
        assertEquals(3, progress.validated)
        assertEquals(5, progress.total)
        assertEquals(4, progress.threshold)
        assertFalse(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `repeatable validations are ignored in count`() {
        val oneShots = (1..4).map { challenge("o$it", level = 1) }
        val repeatables = (1..5).map {
            challenge("r$it", level = 1, type = ChallengeType.REPEATABLE)
        }
        val validated = setOf("o1", "o2", "o3", "r1", "r2", "r3", "r4", "r5")
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = oneShots + repeatables,
            validatedChallengeIds = validated,
        )
        assertEquals(3, progress.validated)
        assertEquals(4, progress.total)
        assertEquals(4, progress.threshold)
        assertFalse(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = oneShots + repeatables,
                validatedChallengeIds = validated,
            )
        )
    }
}
