package dev.rahier.pouleparty.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMapComposable
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import dev.rahier.pouleparty.ui.theme.ZoneDanger
import dev.rahier.pouleparty.ui.theme.ZoneGreen

/** Neon border drawn as stacked lines, widest and faintest first. */
private val GLOW_LAYERS = listOf(0.08f to 16.0, 0.15f to 8.0, 0.35f to 4.0, 0.9f to 2.5)
private val FLAT_LAYER = listOf(0.9f to 2.5)
private val FINAL_GLOW_LAYERS = listOf(0.15f to 8.0, 0.5f to 3.0, 0.9f to 1.5)
private val FINAL_FLAT_LAYER = listOf(0.5f to 3.0)
private const val FINAL_ZONE_RADIUS_METERS = 50.0

/** A closed ring of points, computed once per center and radius. */
@Composable
private fun rememberRing(center: Point, radiusMeters: Double): List<Point> = remember(center, radiusMeters) {
    val circle = circlePolygonPoints(center, radiusMeters)
    circle + circle.first()
}

/** Darkens everything outside the zone and outlines its border. */
@Composable
@MapboxMapComposable
fun ZoneOverlay(center: Point, radiusMeters: Double, isOutsideZone: Boolean, glow: Boolean = true) {
    val ring = rememberRing(center, radiusMeters)
    val outer = remember(center) { outerBoundsPoints(center) }
    PolygonAnnotation(points = listOf(outer, ring)) {
        fillColor = if (isOutsideZone) ZoneDanger.copy(alpha = 0.4f) else Color(0f, 0f, 0f, 0.3f)
        fillOpacity = 1.0
    }
    (if (glow) GLOW_LAYERS else FLAT_LAYER).forEach { (alpha, width) ->
        PolylineAnnotation(points = ring) {
            lineColor = ZoneGreen.copy(alpha = alpha)
            lineWidth = width
        }
    }
}

/** Outline of the final 50 m zone the game settles on. */
@Composable
@MapboxMapComposable
fun FinalZoneOutline(center: Point, glow: Boolean = true) {
    val ring = rememberRing(center, FINAL_ZONE_RADIUS_METERS)
    (if (glow) FINAL_GLOW_LAYERS else FINAL_FLAT_LAYER).forEach { (alpha, width) ->
        PolylineAnnotation(points = ring) {
            lineColor = ZoneGreen.copy(alpha = alpha)
            lineWidth = width
        }
    }
}

/** A single thin circle, used for the zone preview power-up. */
@Composable
@MapboxMapComposable
fun CircleOutline(center: Point, radiusMeters: Double, color: Color, width: Double = 2.0) {
    val ring = rememberRing(center, radiusMeters)
    PolylineAnnotation(points = ring) {
        lineColor = color
        lineWidth = width
    }
}
