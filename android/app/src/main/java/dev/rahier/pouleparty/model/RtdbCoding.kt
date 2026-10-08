package dev.rahier.pouleparty.model

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
