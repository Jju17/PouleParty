package dev.rahier.pouleparty.model

/**
 * Coerces a Realtime Database numeric leaf into a [Double] / [Long]. RTDB hands
 * numbers back as `Long` or `Double` depending on the value, so position
 * decoding (PP-102) normalizes through these helpers.
 */
fun rtdbDouble(value: Any?): Double? = when (value) {
    is Double -> value
    is Long -> value.toDouble()
    is Int -> value.toDouble()
    is Float -> value.toDouble()
    else -> null
}

fun rtdbLong(value: Any?): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    else -> null
}
