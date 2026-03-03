package dev.rahier.pouleparty.ui

import android.location.Location
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
import java.util.Date

/**
 * Pure helper functions shared between ChickenMapViewModel and HunterMapViewModel.
 * Extracts duplicated countdown / radius / game-over / winner-detection logic.
 */

// ── Zone Check ───────────────────────────────────────

enum class PlayerRole { CHICKEN, HUNTER }

data class ZoneCheckResult(
    val isOutsideZone: Boolean,
    val distanceToCenter: Float
)

/** Whether this role should be zone-checked under the given game mode. */
fun shouldCheckZone(role: PlayerRole, gameMod: GameMod): Boolean = when (gameMod) {
    GameMod.STAY_IN_THE_ZONE -> true
    GameMod.FOLLOW_THE_CHICKEN, GameMod.MUTUAL_TRACKING -> role == PlayerRole.HUNTER
}

/** Pure check: is the user outside the zone? */
fun checkZoneStatus(
    userLocation: Point,
    zoneCenter: Point,
    zoneRadius: Double
): ZoneCheckResult {
    val results = FloatArray(1)
    Location.distanceBetween(
        userLocation.latitude(), userLocation.longitude(),
        zoneCenter.latitude(), zoneCenter.longitude(),
        results
    )
    val distance = results[0]
    return ZoneCheckResult(isOutsideZone = distance > zoneRadius, distanceToCenter = distance)
}

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
            currentCountdownText == null
        ) {
            return CountdownResult.ShowText(phase.completionText)
        }
    }
    return CountdownResult.NoChange
}

// ── Game over by time ────────────────────────────────

fun checkGameOverByTime(endDate: Date): Boolean = Date().after(endDate)

// ── Deterministic Drift ──────────────────────────────

/**
 * Computes a deterministic drifted center for stayInTheZone mode.
 * The result is offset from [basePoint], always within both:
 *  - `newRadius * 0.5` of basePoint (so basePoint stays well inside)
 *  - `(oldRadius - newRadius)` of any previous center computed the same way
 *
 * [driftSeed] is a random integer stored in the Game document so every
 * client produces the exact same circle at each shrink step while each
 * game session has a unique drift pattern.
 */
fun deterministicDriftCenter(
    basePoint: Point,
    oldRadius: Double,
    newRadius: Double,
    driftSeed: Int
): Point {
    val maxFromBase = newRadius * 0.5
    val maxFromPrev = maxOf(0.0, oldRadius - newRadius) * 0.5
    val safeDrift = minOf(maxFromBase, maxFromPrev)

    if (safeDrift <= 0) return basePoint

    val angleSeed = kotlin.math.abs(driftSeed * 31 xor newRadius.toInt())
    val distSeed = kotlin.math.abs(driftSeed * 127 xor (newRadius.toInt() * 37))

    val angle = (angleSeed % 36000) / 36000.0 * 2.0 * kotlin.math.PI
    val distFraction = (distSeed % 10000) / 10000.0
    val distance = safeDrift * kotlin.math.sqrt(distFraction)

    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLng = 111_320.0 * kotlin.math.cos(basePoint.latitude() * kotlin.math.PI / 180.0)

    val dLat = (distance * kotlin.math.cos(angle)) / metersPerDegreeLat
    val dLng = (distance * kotlin.math.sin(angle)) / metersPerDegreeLng

    return Point.fromLngLat(
        basePoint.longitude() + dLng,
        basePoint.latitude() + dLat
    )
}

// ── Radius update ────────────────────────────────────

data class RadiusUpdateResult(
    val newRadius: Int,
    val newNextUpdate: Date,
    val newCircleCenter: Point?,
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
    initialLocation: Point,
    currentCircleCenter: Point?,
    driftSeed: Int = 0
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

    val newCenter = if (gameMod == GameMod.STAY_IN_THE_ZONE) {
        deterministicDriftCenter(
            basePoint = initialLocation,
            oldRadius = currentRadius.toDouble(),
            newRadius = newRadius.toDouble(),
            driftSeed = driftSeed
        )
    } else {
        currentCircleCenter
    }

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
