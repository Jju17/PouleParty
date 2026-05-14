package dev.rahier.pouleparty.ui.gamelogic

import android.location.Location
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
import java.util.Date

/**
 * Pure helper functions shared between ChickenMapViewModel and HunterMapViewModel.
 * Extracts duplicated countdown / radius / game-over / winner-detection logic.
 *
 * Cross-platform parity: mirrors `ios/PouleParty/Components/GameTimerLogic.swift`.
 * Any change here must be reflected on the iOS side (and vice versa) — both platforms
 * must produce identical outputs for the same inputs. See `CLAUDE.md` → "Cross-platform parity".
 */

// ── Zone Check ───────────────────────────────────────

enum class PlayerRole { CHICKEN, HUNTER, GAME_MASTER }

/**
 * Phase of an active game, used by the Home banner to pick the right
 * copy + CTA. See iOS `GamePhase` for parity.
 */
enum class GamePhase {
    /** Game is already live (`status == IN_PROGRESS`). Banner: "Reprendre". */
    IN_PROGRESS,
    /** Game is scheduled but not started yet (`status == WAITING`). Banner:
     *  "Préparer" for chicken / "Rejoindre" for hunter. */
    UPCOMING,
}

data class ZoneCheckResult(
    val isOutsideZone: Boolean,
    val distanceToCenter: Float
)

/**
 * Whether this role should be zone-checked under the given game mode.
 * GameMaster (PP-24) is a pure spectator and is never zone-checked.
 */
fun shouldCheckZone(role: PlayerRole, gameMod: GameMod): Boolean {
    if (role == PlayerRole.GAME_MASTER) return false
    return when (gameMod) {
        GameMod.STAY_IN_THE_ZONE -> true
        GameMod.FOLLOW_THE_CHICKEN -> role == PlayerRole.HUNTER
    }
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
            timeToTargetSec > 0 && timeToTargetSec <= AppConstants.COUNTDOWN_THRESHOLD_SECONDS
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

// ── Center Interpolation ─────────────────────────────

/**
 * Interpolates the zone center between [initialCenter] and [finalCenter]
 * based on how much the radius has shrunk.
 *
 * When currentRadius == initialRadius → returns initialCenter
 * When currentRadius == 0             → returns finalCenter
 *
 * If [finalCenter] is null, returns [initialCenter] unchanged.
 */
fun interpolateZoneCenter(
    initialCenter: Point,
    finalCenter: Point?,
    initialRadius: Double,
    currentRadius: Double
): Point {
    if (finalCenter == null) return initialCenter
    if (!initialRadius.isFinite() || initialRadius <= 0) return initialCenter
    if (!currentRadius.isFinite()) return initialCenter

    val rawProgress = (initialRadius - currentRadius) / initialRadius
    if (!rawProgress.isFinite()) return initialCenter
    val progress = rawProgress.coerceIn(0.0, 1.0)

    val lat = initialCenter.latitude() + progress * (finalCenter.latitude() - initialCenter.latitude())
    val lng = initialCenter.longitude() + progress * (finalCenter.longitude() - initialCenter.longitude())
    if (!lat.isFinite() || !lng.isFinite()) return initialCenter

    return Point.fromLngLat(lng, lat)
}

// ── Deterministic Drift ──────────────────────────────

/**
 * Extra meters carved out of the drift budget so floating-point error
 * in the meters ↔ degrees conversion can never push [finalCenter]
 * outside the new circle. The conversion is consistent (same
 * `cos(lat)` used for the offset and for the distance check), so 1 m
 * is plenty.
 */
const val FINAL_CENTER_SAFETY_METERS = 1.0

/**
 * Radius of the "final zone" the chicken sees as a green glow on the
 * map — the whole disk, not just its center, must stay inside every
 * drifted circle. Matches the hardcoded 50 m used by the final-zone
 * polyline block in `ChickenMapScreen.kt` and iOS'
 * `finalZoneGlowContent`. Kept alongside the other drift constants
 * so the drift algo and the UI can never disagree on what "final
 * zone" means.
 */
const val FINAL_ZONE_RADIUS_METERS = 50.0

/**
 * How many rejection-sampling attempts before falling back to the
 * deterministic "pull toward finalCenter by `delta`" point. Each
 * attempt costs one splitmix64 evaluation; 32 is plenty — the
 * rejection rate only gets high near game end where
 * `disk(C, delta)` sticks out past `disk(F, r)`, and even at 50 %
 * rejection 32 attempts succeed with probability > 99.99 %.
 */
private const val MAX_DRIFT_ATTEMPTS = 32

/**
 * Computes a deterministic drifted center for stayInTheZone mode.
 * Picks the zone center for one shrink step as a pseudo-random
 * point that simultaneously satisfies:
 *  A. `|candidate − basePoint| ≤ oldRadius − newRadius` → the new
 *     circle fits entirely inside `disk(basePoint, oldRadius)`.
 *  B. when [finalCenter] is provided, `|candidate − finalCenter| ≤
 *     newRadius − FINAL_ZONE_RADIUS − safety` → the final-zone
 *     disk (50 m glow) fits entirely inside the drifted circle.
 *
 * Caller contract: [basePoint] is the **initial** zone center and
 * [oldRadius] is the **initial** zone radius — NOT the previous
 * drifted center. Every shrink's candidate is drawn independently
 * from `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − FINAL −
 * safety)`, so successive circles can overlap each other freely as
 * long as both constraints hold. This matches the product rules:
 * no circle escapes the start zone, every circle contains the
 * final zone, intermediate circles have no nesting constraint
 * between them.
 *
 * Strategy: sample uniformly inside the *smaller* of disk A / disk
 * B and reject against the larger. When one disk contains the
 * other, the first sample is always valid. When they partially
 * overlap, the lens/smaller-disk ratio is usually > 10 %, so 32
 * splitmix64-seeded attempts succeed with overwhelming probability.
 * When rejection exhausts, fall back to a deterministic point on
 * the base→final line — always in the intersection whenever the
 * disks overlap (caller invariant: final zone fits in start zone).
 */
fun deterministicDriftCenter(
    basePoint: Point,
    oldRadius: Double,
    newRadius: Double,
    driftSeed: Int,
    finalCenter: Point? = null
): Point {
    if (newRadius >= oldRadius || newRadius <= 0) return basePoint

    val rA = oldRadius - newRadius
    val rB = if (finalCenter != null) {
        maxOf(0.0, newRadius - FINAL_ZONE_RADIUS_METERS - FINAL_CENTER_SAFETY_METERS)
    } else {
        Double.POSITIVE_INFINITY
    }

    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLng = 111_320.0 * kotlin.math.cos(basePoint.latitude() * kotlin.math.PI / 180.0)

    val stepSeed = driftSeed.toLong() xor newRadius.toLong()

    if (finalCenter == null) {
        val angle = seededRandom(stepSeed, 0) * 2.0 * kotlin.math.PI
        val dist = rA * kotlin.math.sqrt(seededRandom(stepSeed, 1))
        return Point.fromLngLat(
            basePoint.longitude() + (dist * kotlin.math.cos(angle)) / metersPerDegreeLng,
            basePoint.latitude() + (dist * kotlin.math.sin(angle)) / metersPerDegreeLat,
        )
    }

    val vx = (finalCenter.longitude() - basePoint.longitude()) * metersPerDegreeLng
    val vy = (finalCenter.latitude() - basePoint.latitude()) * metersPerDegreeLat
    val vLen = kotlin.math.sqrt(vx * vx + vy * vy)

    val sampleFromA = rA <= rB
    val sampleR = minOf(rA, rB)

    for (k in 0 until MAX_DRIFT_ATTEMPTS) {
        val angle = seededRandom(stepSeed, 2 * k) * 2.0 * kotlin.math.PI
        val dist = sampleR * kotlin.math.sqrt(seededRandom(stepSeed, 2 * k + 1))
        val ox = dist * kotlin.math.cos(angle)
        val oy = dist * kotlin.math.sin(angle)
        val cdx = if (sampleFromA) ox else vx + ox
        val cdy = if (sampleFromA) oy else vy + oy
        val checkDx = if (sampleFromA) cdx - vx else cdx
        val checkDy = if (sampleFromA) cdy - vy else cdy
        val checkR = if (sampleFromA) rB else rA
        if (kotlin.math.sqrt(checkDx * checkDx + checkDy * checkDy) <= checkR) {
            return Point.fromLngLat(
                basePoint.longitude() + cdx / metersPerDegreeLng,
                basePoint.latitude() + cdy / metersPerDegreeLat,
            )
        }
    }

    if (vLen > 0) {
        val pull = minOf(rA, vLen)
        return Point.fromLngLat(
            basePoint.longitude() + ((vx / vLen) * pull) / metersPerDegreeLng,
            basePoint.latitude() + ((vy / vLen) * pull) / metersPerDegreeLat,
        )
    }
    return basePoint
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
    driftSeed: Int = 0,
    isZoneFrozen: Boolean = false,
    finalLocation: Point? = null,
    initialRadius: Double = 0.0
): RadiusUpdateResult? {
    val nextUpdate = nextRadiusUpdate ?: return null
    val now = Date()
    if (!now.after(nextUpdate)) return null
    // Zone Freeze: skip the radius reduction for THIS scheduled shrink but
    // still advance `nextRadiusUpdate` to the following one. Previously we
    // returned null here, which left the countdown target frozen on a past
    // date — after freeze expired, one tick would process and jump the next
    // update past `endDate`, producing a "Map update in: 3:00 / 4:59"
    // countdown on the hunter side even after the chicken's game had ended.
    // Advancing here keeps the countdown monotonic and in sync with
    // findLastUpdate, which always returns lastUpdate + interval.
    if (isZoneFrozen) {
        val intervalMsFrozen = (radiusIntervalUpdate * 60 * 1000).toLong()
        return RadiusUpdateResult(
            newRadius = currentRadius,
            newNextUpdate = Date(nextUpdate.time + intervalMsFrozen),
            newCircleCenter = currentCircleCenter,
            isGameOver = false,
            gameOverMessage = null
        )
    }

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
        // Drift is independent per shrink: candidate sampled from
        // `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − FINAL −
        // safety)`. That enforces both product rules directly — new
        // circle inside start zone, final zone inside new circle —
        // while leaving successive intermediate circles free to
        // overlap each other.
        deterministicDriftCenter(
            basePoint = initialLocation,
            oldRadius = initialRadius,
            newRadius = newRadius.toDouble(),
            driftSeed = driftSeed,
            finalCenter = finalLocation
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

// ── Debug Preview (all shifted circles at once) ──────

/**
 * A single preview circle entry returned by [computeDebugShiftedCircles]
 * — the center and radius the zone will hold at each scheduled shrink,
 * in order.
 */
data class DebugShrinkCircle(
    val center: Point,
    val radius: Double,
)

/**
 * Walks the zone shrink schedule forward from `hunterStartDate` through
 * `endDate` using the same [interpolateZoneCenter] +
 * [deterministicDriftCenter] the live timer invokes. Returns one entry
 * per scheduled shrink, ordered from first to last, stopping early
 * when the radius would collapse to zero.
 *
 * Pure function — only used by the long-press debug preview on the
 * chicken map to render every future circle simultaneously. Mirrors
 * the iOS `computeDebugShiftedCircles` sibling.
 */
fun computeDebugShiftedCircles(game: dev.rahier.pouleparty.model.Game): List<DebugShrinkCircle> {
    if (game.gameModEnum != GameMod.STAY_IN_THE_ZONE) return emptyList()
    val initialRadius = game.zone.radius
    if (initialRadius <= 0 || game.zone.shrinkIntervalMinutes <= 0) return emptyList()

    val initialCenter = game.initialLocation
    val finalCenter = game.finalLocation
    val driftSeed = game.zone.driftSeed
    val declinePerUpdate = game.zone.shrinkMetersPerUpdate
    val intervalSeconds = game.zone.shrinkIntervalMinutes * 60.0
    val duration = (game.endDate.time - game.hunterStartDate.time) / 1000.0
    if (duration <= 0 || intervalSeconds <= 0) return emptyList()

    val maxShrinks = kotlin.math.floor(duration / intervalSeconds).toInt()
    val result = mutableListOf<DebugShrinkCircle>()
    var radius = initialRadius
    // Drift is independent per shrink — every call uses the initial
    // center/radius, no state between iterations.
    for (i in 0 until maxShrinks) {
        val newRadius = radius - declinePerUpdate
        if (newRadius <= 0) break
        val drifted = deterministicDriftCenter(
            basePoint = initialCenter,
            oldRadius = initialRadius,
            newRadius = newRadius,
            driftSeed = driftSeed,
            finalCenter = finalCenter,
        )
        result.add(DebugShrinkCircle(center = drifted, radius = newRadius))
        radius = newRadius
    }
    return result
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
    return "${latest.hunterName} found the chicken! 🐔"
}

// ── Jammer Noise ─────────────────────────────────────

/**
 * Adds deterministic ±halfNoise ° of latitude/longitude jitter to [coordinate].
 *
 * The noise is a pure function of `(driftSeed, nowMillis)` bucketed to 1 s, so
 * iOS and Android produce the same value for the same inputs (used by parity
 * tests) and the bruit shifts once per second, too fast for a hunter to
 * average out, too slow to burn battery re-computing inside a single write.
 */
fun applyJammerNoise(
    coordinate: com.mapbox.geojson.Point,
    driftSeed: Int,
    nowMillis: Long = System.currentTimeMillis(),
    jammerNoiseDegrees: Double = dev.rahier.pouleparty.AppConstants.JAMMER_NOISE_DEGREES
): com.mapbox.geojson.Point {
    val bucket = nowMillis / 1000L
    val seed = driftSeed.toLong() xor bucket
    val halfNoise = jammerNoiseDegrees / 2.0
    val latNoise = (seededRandom(seed, 0) * 2.0 - 1.0) * halfNoise
    val lonNoise = (seededRandom(seed, 1) * 2.0 - 1.0) * halfNoise
    return com.mapbox.geojson.Point.fromLngLat(
        coordinate.longitude() + lonNoise,
        coordinate.latitude() + latNoise
    )
}

// ── Seeded Random (splitmix64) ──────────────────────

/**
 * Deterministic pseudo-random number from [seed] and [index].
 * Uses splitmix64 — must match iOS GameTimerLogic.seededRandom exactly.
 */
@Suppress("INTEGER_OVERFLOW")
fun seededRandom(seed: Long, index: Int): Double {
    var z = seed + index.toLong() * -0x61c8864680b583ebL // 0x9e3779b97f4a7c15 as signed Long
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111eb
    z = z xor (z ushr 31)
    return (z ushr 1).toDouble() / Long.MAX_VALUE.toDouble()
}

// ── PP-17 Overtime formatter ────────────────────────────────────

/**
 * Renders the `+MM:SS` (or `+HH:MM:SS` past one hour) overtime
 * delta shown on the gameOver countdown bar. Negative deltas
 * (i.e. `now < endDate`) clamp to `+00:00`. Mirrors the Swift
 * `formatOvertime` so the two platforms produce identical strings.
 */
fun formatOvertime(now: java.util.Date, endDate: java.util.Date): String {
    val seconds = ((now.time - endDate.time) / 1000L).coerceAtLeast(0L).toInt()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "+%02d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "+%02d:%02d".format(minutes, secs)
    }
}
