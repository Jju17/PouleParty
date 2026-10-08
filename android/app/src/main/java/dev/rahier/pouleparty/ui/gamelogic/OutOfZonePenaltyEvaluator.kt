package dev.rahier.pouleparty.ui.gamelogic

import dev.rahier.pouleparty.AppConstants

data class OutOfZonePenaltyDecision(
    val newLastPenaltyAt: Long?,
    val resetLastPenaltyAt: Boolean,
    val shouldFirePenalty: Boolean,
)

/**
 * Decide what to do at one timer tick of the hunter map.
 *
 * Decision tree:
 * 1. Out of zone, gameplay active, hunter known:
 *    - first tick after exit → start the 5 s window, no penalty
 *    - window elapsed → fire penalty, reset window
 *    - still inside the window → no-op
 * 2. Back inside the zone with a live window → reset so the next
 *    exit starts fresh.
 * 3. Anything else → no-op.
 */
fun evaluateOutOfZonePenalty(
    isOutsideZone: Boolean,
    isGameOver: Boolean,
    hunterId: String,
    lastPenaltyAt: Long?,
    nowMs: Long,
    intervalMs: Long = AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS,
): OutOfZonePenaltyDecision {
    if (isOutsideZone && !isGameOver && hunterId.isNotEmpty()) {
        return if (lastPenaltyAt == null) {
            OutOfZonePenaltyDecision(
                newLastPenaltyAt = nowMs,
                resetLastPenaltyAt = false,
                shouldFirePenalty = false,
            )
        } else if (nowMs - lastPenaltyAt >= intervalMs) {
            OutOfZonePenaltyDecision(
                newLastPenaltyAt = nowMs,
                resetLastPenaltyAt = false,
                shouldFirePenalty = true,
            )
        } else {
            OutOfZonePenaltyDecision(
                newLastPenaltyAt = null,
                resetLastPenaltyAt = false,
                shouldFirePenalty = false,
            )
        }
    } else if (!isOutsideZone && lastPenaltyAt != null) {
        return OutOfZonePenaltyDecision(
            newLastPenaltyAt = null,
            resetLastPenaltyAt = true,
            shouldFirePenalty = false,
        )
    }
    return OutOfZonePenaltyDecision(
        newLastPenaltyAt = null,
        resetLastPenaltyAt = false,
        shouldFirePenalty = false,
    )
}
