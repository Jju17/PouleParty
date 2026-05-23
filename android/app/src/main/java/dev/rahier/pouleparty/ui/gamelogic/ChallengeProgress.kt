package dev.rahier.pouleparty.ui.gamelogic

import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeType
import kotlin.math.ceil

data class LevelProgress(val validated: Int, val total: Int, val threshold: Int)

/**
 * Pure helpers mirroring iOS `ChallengeProgress`. A hunter unlocks
 * `level N+1` once they have validated at least `ceil(N_oneShot × 0.80)`
 * of the oneShot challenges in `level N`. Repeatable challenges are
 * unbounded by design and are excluded from the calculation.
 */
object ChallengeProgress {

    fun isLevelUnlocked(
        level: Int,
        challenges: List<Challenge>,
        validatedChallengeIds: Set<String>,
    ): Boolean {
        if (level <= 1) return true
        val progress = levelProgress(level, challenges, validatedChallengeIds)
        return progress.validated >= progress.threshold
    }

    fun levelProgress(
        level: Int,
        challenges: List<Challenge>,
        validatedChallengeIds: Set<String>,
    ): LevelProgress {
        val previousLevel = level - 1
        val oneShotsAtPrevious = challenges.filter {
            it.level == previousLevel && it.typeEnum == ChallengeType.ONE_SHOT
        }
        val total = oneShotsAtPrevious.size
        val validated = oneShotsAtPrevious.count { validatedChallengeIds.contains(it.id) }
        val threshold = ceil(total * 0.80).toInt()
        return LevelProgress(validated = validated, total = total, threshold = threshold)
    }
}
