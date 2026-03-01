package dev.rahier.pouleparty.ui

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ZoneOverlayUtilsTest {

    // ── circlePolygonPoints ────────────────────────────

    @Test
    fun `circlePolygonPoints returns default 72 points`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val points = circlePolygonPoints(center, 1500.0)
        assertEquals(72, points.size)
    }

    @Test
    fun `circlePolygonPoints returns custom number of points`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val points = circlePolygonPoints(center, 1500.0, numPoints = 36)
        assertEquals(36, points.size)
    }

    @Test
    fun `circlePolygonPoints all points are approximately at correct distance`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val radiusMeters = 1000.0
        val points = circlePolygonPoints(center, radiusMeters)

        for (point in points) {
            val distance = haversineDistance(
                center.latitude(), center.longitude(),
                point.latitude(), point.longitude()
            )
            // Allow 1% tolerance
            assertTrue(
                "Distance $distance should be within 1% of $radiusMeters",
                abs(distance - radiusMeters) / radiusMeters < 0.01
            )
        }
    }

    @Test
    fun `circlePolygonPoints first point is north of center`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val points = circlePolygonPoints(center, 1500.0)
        val first = points.first()
        // First point (bearing = 0) should be due north: higher latitude, same longitude
        assertTrue("First point should be north of center", first.latitude() > center.latitude())
        assertEquals(center.longitude(), first.longitude(), 0.001)
    }

    // ── outerBoundsPoints ──────────────────────────────

    @Test
    fun `outerBoundsPoints returns 4 points`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val points = outerBoundsPoints(center)
        assertEquals(4, points.size)
    }

    @Test
    fun `outerBoundsPoints clamps latitude near poles`() {
        // Center near north pole
        val center = Point.fromLngLat(0.0, 80.0)
        val points = outerBoundsPoints(center, padding = 20.0)
        // North latitude should be clamped to 85
        val maxLat = points.maxOf { it.latitude() }
        assertTrue("North latitude should be clamped to 85", maxLat <= 85.0)
    }

    @Test
    fun `outerBoundsPoints extends by padding amount`() {
        val center = Point.fromLngLat(4.0, 50.0)
        val padding = 15.0
        val points = outerBoundsPoints(center, padding = padding)

        val maxLat = points.maxOf { it.latitude() }
        val minLat = points.minOf { it.latitude() }
        val maxLon = points.maxOf { it.longitude() }
        val minLon = points.minOf { it.longitude() }

        assertEquals(50.0 + padding, maxLat, 0.001)
        assertEquals(50.0 - padding, minLat, 0.001)
        assertEquals(4.0 + padding, maxLon, 0.001)
        assertEquals(4.0 - padding, minLon, 0.001)
    }

    // ── Helper ─────────────────────────────────────────

    /** Haversine distance in meters between two lat/lon pairs. */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
