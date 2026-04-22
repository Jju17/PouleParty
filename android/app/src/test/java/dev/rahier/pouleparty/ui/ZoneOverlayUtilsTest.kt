package dev.rahier.pouleparty.ui

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.ui.components.circlePolygonPoints
import dev.rahier.pouleparty.ui.components.outerBoundsPoints
import dev.rahier.pouleparty.ui.components.powerUpPulseAlpha
import dev.rahier.pouleparty.ui.components.zoomForRadius
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

    // ── powerUpPulseAlpha ──────────────────────────────

    @Test
    fun `powerUpPulseAlpha at time 0 returns midpoint`() {
        // sin(0) = 0, (0+1)/2 = 0.5 → min + 0.5*(max-min) = midpoint
        val alpha = powerUpPulseAlpha(timeMs = 0L, periodMs = 2000L, minAlpha = 0.08f, maxAlpha = 0.18f)
        assertEquals(0.13f, alpha, 0.001f)
    }

    @Test
    fun `powerUpPulseAlpha at quarter period returns max`() {
        // sin(π/2) = 1, (1+1)/2 = 1 → maxAlpha
        val alpha = powerUpPulseAlpha(timeMs = 500L, periodMs = 2000L, minAlpha = 0.08f, maxAlpha = 0.18f)
        assertEquals(0.18f, alpha, 0.001f)
    }

    @Test
    fun `powerUpPulseAlpha at half period returns midpoint`() {
        // sin(π) = 0, (0+1)/2 = 0.5 → midpoint
        val alpha = powerUpPulseAlpha(timeMs = 1000L, periodMs = 2000L, minAlpha = 0.08f, maxAlpha = 0.18f)
        assertEquals(0.13f, alpha, 0.001f)
    }

    @Test
    fun `powerUpPulseAlpha at three-quarter period returns min`() {
        // sin(3π/2) = -1, (-1+1)/2 = 0 → minAlpha
        val alpha = powerUpPulseAlpha(timeMs = 1500L, periodMs = 2000L, minAlpha = 0.08f, maxAlpha = 0.18f)
        assertEquals(0.08f, alpha, 0.001f)
    }

    @Test
    fun `powerUpPulseAlpha wraps at period boundary`() {
        val atZero = powerUpPulseAlpha(timeMs = 0L, periodMs = 2000L)
        val atPeriod = powerUpPulseAlpha(timeMs = 2000L, periodMs = 2000L)
        val atTwoPeriods = powerUpPulseAlpha(timeMs = 4000L, periodMs = 2000L)
        assertEquals(atZero, atPeriod, 0.001f)
        assertEquals(atZero, atTwoPeriods, 0.001f)
    }

    @Test
    fun `powerUpPulseAlpha stays within bounds for any time`() {
        val minAlpha = 0.05f
        val maxAlpha = 0.25f
        for (timeMs in 0L..10_000L step 73L) {
            val alpha = powerUpPulseAlpha(timeMs, periodMs = 2000L, minAlpha = minAlpha, maxAlpha = maxAlpha)
            assertTrue("alpha $alpha < min $minAlpha at t=$timeMs", alpha >= minAlpha - 0.001f)
            assertTrue("alpha $alpha > max $maxAlpha at t=$timeMs", alpha <= maxAlpha + 0.001f)
        }
    }

    @Test
    fun `powerUpPulseAlpha respects custom period`() {
        // With period = 4000ms, quarter period is 1000ms → max
        val alpha = powerUpPulseAlpha(timeMs = 1000L, periodMs = 4000L, minAlpha = 0.0f, maxAlpha = 1.0f)
        assertEquals(1.0f, alpha, 0.001f)
    }

    @Test
    fun `powerUpPulseAlpha handles degenerate range (min equals max)`() {
        val alpha = powerUpPulseAlpha(timeMs = 500L, periodMs = 2000L, minAlpha = 0.1f, maxAlpha = 0.1f)
        assertEquals(0.1f, alpha, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `powerUpPulseAlpha rejects non-positive period`() {
        powerUpPulseAlpha(timeMs = 100L, periodMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `powerUpPulseAlpha rejects negative period`() {
        powerUpPulseAlpha(timeMs = 100L, periodMs = -1000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `powerUpPulseAlpha rejects min greater than max`() {
        powerUpPulseAlpha(timeMs = 100L, minAlpha = 0.5f, maxAlpha = 0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `powerUpPulseAlpha rejects alpha out of range`() {
        powerUpPulseAlpha(timeMs = 100L, minAlpha = -0.1f, maxAlpha = 0.5f)
    }

    @Test
    fun `powerUpPulseAlpha handles very large time values`() {
        // 10 years of milliseconds — should still be within bounds
        val tenYearsMs = 10L * 365L * 24L * 3600L * 1000L
        val alpha = powerUpPulseAlpha(timeMs = tenYearsMs, periodMs = 2000L, minAlpha = 0.08f, maxAlpha = 0.18f)
        assertTrue(alpha in 0.08f..0.18f)
    }

    @Test
    fun `powerUpPulseAlpha is continuous across period boundary`() {
        // Verify the function doesn't jump discontinuously at period transitions
        val justBeforePeriod = powerUpPulseAlpha(timeMs = 1999L, periodMs = 2000L)
        val justAfterPeriod = powerUpPulseAlpha(timeMs = 2001L, periodMs = 2000L)
        // Difference should be tiny (not jumping from max to min)
        assertTrue(
            "Should be continuous, got $justBeforePeriod → $justAfterPeriod",
            abs(justBeforePeriod - justAfterPeriod) < 0.01f
        )
    }

    // ── zoomForRadius defensive guards ────────────────

    @Test
    fun `zoomForRadius falls back on zero radius`() {
        assertEquals(15.0, zoomForRadius(0.0, 50.85), 0.0)
    }

    @Test
    fun `zoomForRadius falls back on negative radius`() {
        assertEquals(15.0, zoomForRadius(-100.0, 50.85), 0.0)
    }

    @Test
    fun `zoomForRadius falls back on NaN radius`() {
        assertEquals(15.0, zoomForRadius(Double.NaN, 50.85), 0.0)
    }

    @Test
    fun `zoomForRadius falls back on NaN latitude`() {
        assertEquals(15.0, zoomForRadius(1000.0, Double.NaN), 0.0)
    }

    @Test
    fun `zoomForRadius handles polar latitude without NaN`() {
        val zoom = zoomForRadius(1000.0, 90.0)
        assertTrue("zoom must be finite at the pole, got $zoom", zoom.isFinite())
        assertTrue("zoom in [8, 18], got $zoom", zoom in 8.0..18.0)
    }

    @Test
    fun `zoomForRadius is in range for typical inputs`() {
        val zoom = zoomForRadius(1500.0, 50.85)
        assertTrue(zoom.isFinite())
        assertTrue("zoom in (8, 18), got $zoom", zoom > 8.0 && zoom < 18.0)
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
