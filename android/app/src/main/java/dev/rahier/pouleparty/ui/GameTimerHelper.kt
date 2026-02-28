package dev.rahier.pouleparty.ui

import com.google.android.gms.maps.model.LatLng
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
import java.util.Date

/**
 * Pure helper functions shared between ChickenMapViewModel and HunterMapViewModel.
 * Extracts duplicated countdown / radius / game-over / winner-detection logic.
 */

// ── Countdown ────────────────────────────────────────

data class CountdownPhase(
    val targetDate: Date,
    val completionText: String,
    val showNumericCountdown: Boolean,
    val isEnabled: Boolean
)

sealed class CountdownResult {
    data object NoChange : CountdownResult()
    data class UpdateNumber(val number: Int) : CountdownResult()
    data class ShowText(val text: String) : CountdownResult()
}

/**
 * Evaluate which countdown phase (if any) should fire given [now].
 * Returns [CountdownResult.NoChange] when the overlay shouldn't change.
 */
fun evaluateCountdown(
    phases: List<CountdownPhase>,
    now: Date,
    currentCountdownNumber: Int?,
    currentCountdownText: String?
): CountdownResult {
    for (phase in phases) {
        if (!phase.isEnabled) continue

        val timeToTargetMs = phase.targetDate.time - now.time
        val timeToTargetSec = timeToTargetMs / 1000.0

        if (phase.showNumericCountdown &&
            timeToTargetSec in 0.0..AppConstants.COUNTDOWN_THRESHOLD_SECONDS
        ) {
            val number = kotlin.math.ceil(timeToTargetSec).toInt()
            if (number > 0 && currentCountdownNumber != number) {
                return CountdownResult.UpdateNumber(number)
            }
            return CountdownResult.NoChange
        }

        if (timeToTargetSec <= 0 && timeToTargetSec > -1 &&
            currentCountdownText == null &&
            currentCountdownNumber == null
        ) {
            return CountdownResult.ShowText(phase.completionText)
        }
    }
    return CountdownResult.NoChange
}

// ── Game over by time ────────────────────────────────

fun checkGameOverByTime(endDate: Date): Boolean = Date().after(endDate)

// ── Radius update ────────────────────────────────────

data class RadiusUpdateResult(
    val newRadius: Int,
    val newNextUpdate: Date,
    val newCircleCenter: LatLng?,
    val isGameOver: Boolean,
    val gameOverMessage: String?
)

/**
 * Check whether a radius shrink should happen and compute the new state.
 * Returns `null` when no update is due yet.
 */
fun processRadiusUpdate(
    nextRadiusUpdate: Date?,
    currentRadius: Int,
    radiusDeclinePerUpdate: Double,
    radiusIntervalUpdate: Double,
    gameMod: GameMod,
    initialLocation: LatLng,
    currentCircleCenter: LatLng?
): RadiusUpdateResult? {
    val nextUpdate = nextRadiusUpdate ?: return null
    if (!Date().after(nextUpdate)) return null

    val newRadius = currentRadius - radiusDeclinePerUpdate.toInt()
    if (newRadius <= 0) {
        return RadiusUpdateResult(
            newRadius = currentRadius,
            newNextUpdate = nextUpdate,
            newCircleCenter = currentCircleCenter,
            isGameOver = true,
            gameOverMessage = "The zone has collapsed!"
        )
    }

    val intervalMs = (radiusIntervalUpdate * 60 * 1000).toLong()
    val newNextUpdate = Date(nextUpdate.time + intervalMs)

    val newCenter = if (gameMod == GameMod.STAY_IN_THE_ZONE) initialLocation else currentCircleCenter

    return RadiusUpdateResult(
        newRadius = newRadius,
        newNextUpdate = newNextUpdate,
        newCircleCenter = newCenter,
        isGameOver = false,
        gameOverMessage = null
    )
}

// ── Winner detection ─────────────────────────────────

/**
 * Returns a notification string when new winners appeared since [previousCount],
 * filtering out [ownHunterId] if supplied (so a hunter doesn't see their own win).
 */
fun detectNewWinners(
    winners: List<Winner>,
    previousCount: Int,
    ownHunterId: String? = null
): String? {
    if (winners.size <= previousCount) return null
    val latest = winners.last()
    if (ownHunterId != null && latest.hunterId == ownHunterId) return null
    return "${latest.hunterName} a trouvé la poule !"
}
