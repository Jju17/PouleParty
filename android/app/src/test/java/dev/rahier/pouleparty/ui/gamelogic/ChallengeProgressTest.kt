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
        points = 10,
        type = type.firestoreValue,
        level = level,
        number = 1,
        titleByLocale = mapOf("fr" to id),
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

    @Test
    fun `ten out of ten oneShot unlocks next`() {
        val challenges = (1..10).map { challenge("c$it", level = 1) }
        val validated = (1..10).map { "c$it" }.toSet()
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = challenges,
            validatedChallengeIds = validated,
        )
        assertEquals(10, progress.validated)
        assertEquals(10, progress.total)
        assertEquals(8, progress.threshold)
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `eight of ten is exactly the boundary`() {
        val challenges = (1..10).map { challenge("c$it", level = 1) }
        val validated = setOf("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8")
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = challenges,
            validatedChallengeIds = validated,
        )
        assertEquals(8, progress.threshold)
        assertEquals(8, progress.validated)
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `seven of ten stays blocked`() {
        val challenges = (1..10).map { challenge("c$it", level = 1) }
        val validated = setOf("c1", "c2", "c3", "c4", "c5", "c6", "c7")
        assertFalse(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `mixed-level catalog only counts requested level`() {
        val level1 = (1..3).map { challenge("l1-$it", level = 1) }
        val level2 = (1..5).map { challenge("l2-$it", level = 2) }
        val level3 = (1..2).map { challenge("l3-$it", level = 3) }
        val all = level1 + level2 + level3
        val validated = setOf("l2-1", "l2-2", "l2-3", "l2-4")
        val progress3 = ChallengeProgress.levelProgress(
            level = 3,
            challenges = all,
            validatedChallengeIds = validated,
        )
        assertEquals(5, progress3.total)
        assertEquals(4, progress3.validated)
        assertEquals(4, progress3.threshold)
        assertTrue(
            ChallengeProgress.isLevelUnlocked(
                level = 3,
                challenges = all,
                validatedChallengeIds = validated,
            )
        )
        val progress2 = ChallengeProgress.levelProgress(
            level = 2,
            challenges = all,
            validatedChallengeIds = validated,
        )
        assertEquals(3, progress2.total)
        assertEquals(0, progress2.validated)
        assertEquals(3, progress2.threshold)
        assertFalse(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = all,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `unknown validated ids are ignored`() {
        val challenges = (1..3).map { challenge("c$it", level = 1) }
        val validated = setOf("c1", "c2", "ghost-id")
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = challenges,
            validatedChallengeIds = validated,
        )
        assertEquals(2, progress.validated)
        assertEquals(3, progress.total)
        assertEquals(3, progress.threshold)
        assertFalse(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = challenges,
                validatedChallengeIds = validated,
            )
        )
    }

    @Test
    fun `validated only has repeatable ids still counts zero`() {
        val oneShots = (1..3).map { challenge("o$it", level = 1) }
        val repeatables = (1..3).map {
            challenge("r$it", level = 1, type = ChallengeType.REPEATABLE)
        }
        val validated = setOf("r1", "r2", "r3")
        val progress = ChallengeProgress.levelProgress(
            level = 2,
            challenges = oneShots + repeatables,
            validatedChallengeIds = validated,
        )
        assertEquals(0, progress.validated)
        assertEquals(3, progress.total)
        assertEquals(3, progress.threshold)
        assertFalse(
            ChallengeProgress.isLevelUnlocked(
                level = 2,
                challenges = oneShots + repeatables,
                validatedChallengeIds = validated,
            )
        )
    }
}
