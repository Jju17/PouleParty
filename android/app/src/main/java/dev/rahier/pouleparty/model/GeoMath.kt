package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Great-circle distance, identical on iOS, Android and the server. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val toRad = Math.PI / 180.0
    val dLat = (lat2 - lat1) * toRad
    val dLng = (lng2 - lng1) * toRad
    val a = sin(dLat / 2).let { it * it } +
        cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
}

fun distanceMeters(from: Point, to: Point): Double =
    distanceMeters(from.latitude(), from.longitude(), to.latitude(), to.longitude())
