package dev.rahier.pouleparty.ui.components

import com.mapbox.geojson.Point
import kotlin.math.*

/**
 * Computes the pulse alpha for the power-up collection overlay based on a
 * monotonic time value. The disc breathes between [minAlpha, maxAlpha] over
 * a [periodMs] period.
 *
 * Exposed as a pure function so it can be unit-tested independently of Compose.
 */
fun powerUpPulseAlpha(
    timeMs: Long,
    periodMs: Long = 2000L,
    minAlpha: Float = 0.08f,
    maxAlpha: Float = 0.18f
): Float {
    require(periodMs > 0) { "periodMs must be > 0" }
    require(minAlpha in 0f..1f && maxAlpha in 0f..1f && minAlpha <= maxAlpha) {
        "alpha bounds must be in [0, 1] with min <= max"
    }
    val phase = (timeMs.mod(periodMs)).toDouble() / periodMs.toDouble()
    val sine = (sin(phase * 2 * PI) + 1) / 2  // 0…1
    return (minAlpha + (maxAlpha - minAlpha) * sine).toFloat()
}

/**
 * Approximate a circle as a polygon with [numPoints] vertices.
 */
fun circlePolygonPoints(
    center: Point,
    radiusMeters: Double,
    numPoints: Int = 72
): List<Point> {
    val earthRadius = 6_371_000.0
    val angularRadius = radiusMeters / earthRadius
    val centerLatRad = Math.toRadians(center.latitude())
    val centerLonRad = Math.toRadians(center.longitude())

    return (0 until numPoints).map { i ->
        val bearing = i * 2 * PI / numPoints
        val lat = asin(
            sin(centerLatRad) * cos(angularRadius) +
                cos(centerLatRad) * sin(angularRadius) * cos(bearing)
        )
        val lon = centerLonRad + atan2(
            sin(bearing) * sin(angularRadius) * cos(centerLatRad),
            cos(angularRadius) - sin(centerLatRad) * sin(lat)
        )
        Point.fromLngLat(Math.toDegrees(lon), Math.toDegrees(lat))
    }
}

/**
 * Large rectangle centered on [center] used as the outer boundary for the inverted zone overlay.
 * Avoids date-line wrapping issues that occur with world-spanning coordinates.
 */
/**
 * Calculates the Mapbox zoom level needed to show a circle of given radius on screen.
 * Provides ~25% padding around the circle on a typical mobile viewport.
 */
fun zoomForRadius(radiusMeters: Double, latitudeDegrees: Double): Double {
    val earthCircumference = 40_075_016.686
    val latRad = latitudeDegrees * PI / 180.0
    val zoom = ln(earthCircumference * cos(latRad) / (2.0 * radiusMeters)) / ln(2.0) - 1.0
    return zoom.coerceIn(8.0, 18.0)
}

fun outerBoundsPoints(center: Point, padding: Double = 20.0): List<Point> {
    val north = minOf(85.0, center.latitude() + padding)
    val south = maxOf(-85.0, center.latitude() - padding)
    val west = center.longitude() - padding
    val east = center.longitude() + padding
    return listOf(
        Point.fromLngLat(west, north),
        Point.fromLngLat(east, north),
        Point.fromLngLat(east, south),
        Point.fromLngLat(west, south)
    )
}
