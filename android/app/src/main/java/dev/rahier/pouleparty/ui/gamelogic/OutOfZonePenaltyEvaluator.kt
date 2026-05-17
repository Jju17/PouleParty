package dev.rahier.pouleparty.ui.gamelogic

import dev.rahier.pouleparty.AppConstants

/**
 * PP-36: pure decision rule for the out-of-zone penalty (-1 point /
 * 5 s). Production callers (`HunterMapViewModel.startTimer()`) and
 * parity tests both go through this single function, so the test
 * surface is the actual code path the runtime exercises.
 *
 * iOS sibling: the same rule is encoded inline in the TCA reducer
 * (`HunterMapFeature` timerTicked branch). Both implementations must
 * stay aligned — `OutOfZonePenaltyTests` (iOS) and
 * `OutOfZonePenaltyTest` (Android) pin the contract with strict
 * parity scenarios.
 *
 * @param newLastPenaltyAt if non-null, set the state's `lastPenaltyAt`
 *   to this value. `null` means "leave it as-is".
 * @param resetLastPenaltyAt when true, clear `lastPenaltyAt`. Set on
 *   re-entry into the zone so the next exit starts a fresh window.
 *   When set, `newLastPenaltyAt` must be null.
 * @param shouldFirePenalty when true, the caller must invoke
 *   `decrementTotalPoints` for this tick.
 */
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
    isDebugPreview: Boolean,
    hunterId: String,
    lastPenaltyAt: Long?,
    nowMs: Long,
    intervalMs: Long = AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS,
): OutOfZonePenaltyDecision {
    if (isOutsideZone && !isGameOver && !isDebugPreview && hunterId.isNotEmpty()) {
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
