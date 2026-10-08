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

const val ZONE_FINAL_RADIUS_METERS = 50.0

const val ZONE_INTERIOR_MARGIN_METERS = 200.0

const val ZONE_MINIMUM_INITIAL_RADIUS_METERS = 800.0

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

fun generateDriftSeed(): Int {
    var seed = 0
    while (seed == 0) {
        seed = Random.nextInt(1, Int.MAX_VALUE)
    }
    return seed
}

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
