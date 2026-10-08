package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import kotlin.random.Random

const val NORMAL_MODE_FIXED_INTERVAL = 5.0 // minutes
const val NORMAL_MODE_MINIMUM_RADIUS = 100.0 // meters

fun calculateNormalModeSettings(initialRadius: Double, gameDurationMinutes: Double): Pair<Double, Double> {
    val numberOfShrinks = gameDurationMinutes / NORMAL_MODE_FIXED_INTERVAL
    if (numberOfShrinks <= 0) return Pair(NORMAL_MODE_FIXED_INTERVAL, 0.0)
    val declinePerUpdate = (initialRadius - NORMAL_MODE_MINIMUM_RADIUS) / numberOfShrinks
    return Pair(NORMAL_MODE_FIXED_INTERVAL, maxOf(0.0, declinePerUpdate))
}

// ── Zone radius computation (PP-13 phase 1, mirrors PP-69) ──────────

/** Final-zone disc shown on the recap preview + applied at runtime
 *  when the zone collapses. 50 m matches the PP-69 backend spec. */
const val ZONE_FINAL_RADIUS_METERS = 50.0

/** Minimum buffer between the shrinking start zone and the final
 *  disc so the zone never collapses earlier than `endDate`. 200 m
 *  matches the PP-69 backend spec. */
const val ZONE_INTERIOR_MARGIN_METERS = 200.0

/** Floor for the initial radius in `stayInTheZone`: even when start
 *  and final pins are very close the game starts with at least 800 m
 *  of breathing room. 800 m matches the PP-69 backend spec. */
const val ZONE_MINIMUM_INITIAL_RADIUS_METERS = 800.0

/**
 * Computes the initial zone radius for the recap step (PP-13 phase 1).
 * Mirrors the PP-69 Cloud Function formula bit-for-bit so phase 1
 * produces the exact same radius the backend will return once phase 2
 * lands and this helper is deleted.
 *
 * - `stayInTheZone`: `max(D × 1.5, D + final + margin, minimumInitial)`
 *   with `D = haversine(start, final)`.
 * - `followTheChicken`: the user-picked `radiusHint` (500/1000/2000);
 *   defaults to 1000 m if no hint is set yet.
 */
fun computeZoneRadius(
    start: Point,
    finalCenter: Point?,
    gameMode: GameMod,
    radiusHint: Double?,
): Double = when (gameMode) {
    GameMod.FOLLOW_THE_CHICKEN -> {
        val allowed = listOf(500.0, 1000.0, 2000.0)
        if (radiusHint != null && radiusHint in allowed) radiusHint else 1000.0
    }
    GameMod.STAY_IN_THE_ZONE -> {
        if (finalCenter == null) {
            ZONE_MINIMUM_INITIAL_RADIUS_METERS
        } else {
            val distance = distanceMeters(start, finalCenter)
            maxOf(
                distance * 1.5,
                distance + ZONE_FINAL_RADIUS_METERS + ZONE_INTERIOR_MARGIN_METERS,
                ZONE_MINIMUM_INITIAL_RADIUS_METERS,
            )
        }
    }
}

/**
 * PP-14 phase 1 — fresh client-side drift seed for the Shuffle button.
 * Must be `> 0` (the runtime PRNG treats 0 as "no drift"). Dropped at
 * the same time as `computeZoneRadius` per PP-13 phase 2 once PP-69
 * lands.
 */
fun generateDriftSeed(): Int {
    var seed = 0
    while (seed == 0) {
        seed = Random.nextInt(1, Int.MAX_VALUE)
    }
    return seed
}

/**
 * PP-13 — picks a center for the initial zone disc such that the
 * disc contains BOTH `startPin` and `finalCenter` without being
 * centered on either. Mirrors the iOS `pickInitialZoneCenter` so
 * both platforms agree on the chosen center for the same `seed`.
 *
 * See the Swift sibling for the algorithm.
 */
fun pickInitialZoneCenter(
    startPin: Point,
    finalCenter: Point,
    radius: Double,
    seed: Int,
): Point {
    val distance = distanceMeters(startPin, finalCenter)
    val midLat = (startPin.latitude() + finalCenter.latitude()) / 2.0
    val midLng = (startPin.longitude() + finalCenter.longitude()) / 2.0
    val maxOffset = maxOf(0.0, radius - distance / 2.0)

    val random = SplitMix64(if (seed == 0) 1L else seed.toLong())
    val r1 = random.next()
    val r2 = random.next()
    val u1 = (r1.toULong().toDouble()) / ULong.MAX_VALUE.toDouble()
    val u2 = (r2.toULong().toDouble()) / ULong.MAX_VALUE.toDouble()
    val angle = u1 * 2.0 * Math.PI
    val mag = kotlin.math.sqrt(u2) * maxOffset

    val dxMeters = mag * kotlin.math.cos(angle)
    val dyMeters = mag * kotlin.math.sin(angle)

    val dLat = dyMeters / 111_111.0
    val cosLat = kotlin.math.cos(midLat * Math.PI / 180.0)
    val dLng = if (cosLat == 0.0) 0.0 else dxMeters / (111_111.0 * cosLat)

    return Point.fromLngLat(midLng + dLng, midLat + dLat)
}

/** Standard splitmix64: advance the state, then mix it. Same stream as iOS. */
private class SplitMix64(private var state: Long) {
    fun next(): Long {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
}
