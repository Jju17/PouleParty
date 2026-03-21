package dev.rahier.pouleparty.model

const val NORMAL_MODE_FIXED_INTERVAL = 5.0 // minutes
const val NORMAL_MODE_MINIMUM_RADIUS = 100.0 // meters

fun calculateNormalModeSettings(initialRadius: Double, gameDurationMinutes: Double): Pair<Double, Double> {
    val numberOfShrinks = gameDurationMinutes / NORMAL_MODE_FIXED_INTERVAL
    if (numberOfShrinks <= 0) return Pair(NORMAL_MODE_FIXED_INTERVAL, 0.0)
    val declinePerUpdate = (initialRadius - NORMAL_MODE_MINIMUM_RADIUS) / numberOfShrinks
    return Pair(NORMAL_MODE_FIXED_INTERVAL, maxOf(0.0, declinePerUpdate))
}
