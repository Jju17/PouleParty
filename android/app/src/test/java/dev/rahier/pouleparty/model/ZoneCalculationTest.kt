package dev.rahier.pouleparty.model

import android.location.Location
import com.mapbox.geojson.Point
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PP-64 — strict goldens for the PP-13 / PP-14 zone helpers
 * (`model/GameSettings.kt`). Mirrors the iOS sibling
 * `ZoneCalculationTests.swift` byte-for-byte and the TS reference
 * `functions/test/zoneCalculation.test.ts`. Any drift between iOS,
 * Android, or the Cloud Function will fail one of these on every
 * platform that's wrong — the cross-platform contract is that
 * `computeZoneRadius` on the same inputs returns the same number to
 * within 1 m (sub-millimetre after the haversine round).
 */
class ZoneCalculationTest {

    /** Tolerance for the radius-from-pin-distance goldens. The test
     *  constructs pin offsets via the `1° latitude ≈ 111_111 m`
     *  approximation; both `Location.distanceBetween` (real device)
     *  and the spherical haversine mock used in this JVM-test suite
     *  deviate from that approximation by a percent or so. At
     *  D = 10 km that's ~25 m, well below the `D × 1.5` interior
     *  margin so the test stays in the noise. 25 m matches the iOS
     *  sibling `ZoneCalculationTests.metresTolerance`. */
    private val metresTolerance = 25.0
    private val interpolateTolerance = 1e-9

    @Before
    fun mockLocationDistance() {
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val lat1 = Math.toRadians(arg<Double>(0))
            val lon1 = Math.toRadians(arg<Double>(1))
            val lat2 = Math.toRadians(arg<Double>(2))
            val lon2 = Math.toRadians(arg<Double>(3))
            val results = arg<FloatArray>(4)
            val dlat = lat2 - lat1
            val dlon = lon2 - lon1
            val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            results[0] = (6_371_000.0 * c).toFloat()
        }
    }

    @After
    fun unmockLocationDistance() {
        unmockkStatic(Location::class)
    }

    // ── computeZoneRadius (stayInTheZone) — golden distances ──────────

    @Test
    fun `radius stayInTheZone at 50 m falls to floor`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.35, 50.85 + (50.0 / 111_111.0))
        val radius = computeZoneRadius(start, finalCenter, GameMod.STAY_IN_THE_ZONE, null)
        assertEquals(ZONE_MINIMUM_INITIAL_RADIUS_METERS, radius, metresTolerance)
    }

    @Test
    fun `radius stayInTheZone at 500 m falls to floor`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.35, 50.85 + (500.0 / 111_111.0))
        val radius = computeZoneRadius(start, finalCenter, GameMod.STAY_IN_THE_ZONE, null)
        assertEquals(800.0, radius, metresTolerance)
    }

    @Test
    fun `radius stayInTheZone at 1 km uses D times 1_5`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.35, 50.85 + (1000.0 / 111_111.0))
        val radius = computeZoneRadius(start, finalCenter, GameMod.STAY_IN_THE_ZONE, null)
        assertEquals(1500.0, radius, metresTolerance)
    }

    @Test
    fun `radius stayInTheZone at 2 km uses D times 1_5`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.35, 50.85 + (2000.0 / 111_111.0))
        val radius = computeZoneRadius(start, finalCenter, GameMod.STAY_IN_THE_ZONE, null)
        assertEquals(3000.0, radius, metresTolerance)
    }

    @Test
    fun `radius stayInTheZone at 10 km uses D times 1_5`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.35, 50.85 + (10_000.0 / 111_111.0))
        val radius = computeZoneRadius(start, finalCenter, GameMod.STAY_IN_THE_ZONE, null)
        assertEquals(15_000.0, radius, metresTolerance)
    }

    @Test
    fun `radius stayInTheZone nil final returns the floor`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val radius = computeZoneRadius(start, null, GameMod.STAY_IN_THE_ZONE, null)
        assertEquals(ZONE_MINIMUM_INITIAL_RADIUS_METERS, radius, 0.0)
    }

    @Test
    fun `radius stayInTheZone interior margin invariant for D up to 10 km`() {
        // PP-69 / PP-13 contract: for every D ≤ 10 km, the interior
        // margin (initialRadius − D) is ≥ 200 m so the zone never
        // collapses early. Sweep at 100 m steps to lock it in.
        val start = Point.fromLngLat(4.35, 50.85)
        var d = 100.0
        while (d <= 10_000.0) {
            val finalCenter = Point.fromLngLat(4.35, 50.85 + (d / 111_111.0))
            val radius = computeZoneRadius(start, finalCenter, GameMod.STAY_IN_THE_ZONE, null)
            val margin = radius - d
            assertTrue("D=$d margin=$margin", margin >= ZONE_INTERIOR_MARGIN_METERS - 1.0)
            d += 100.0
        }
    }

    // ── computeZoneRadius (followTheChicken) ──────────────────────────

    @Test
    fun `radius followTheChicken small`() {
        val r = computeZoneRadius(Point.fromLngLat(4.35, 50.85), null, GameMod.FOLLOW_THE_CHICKEN, 500.0)
        assertEquals(500.0, r, 0.0)
    }

    @Test
    fun `radius followTheChicken medium`() {
        val r = computeZoneRadius(Point.fromLngLat(4.35, 50.85), null, GameMod.FOLLOW_THE_CHICKEN, 1000.0)
        assertEquals(1000.0, r, 0.0)
    }

    @Test
    fun `radius followTheChicken large`() {
        val r = computeZoneRadius(Point.fromLngLat(4.35, 50.85), null, GameMod.FOLLOW_THE_CHICKEN, 2000.0)
        assertEquals(2000.0, r, 0.0)
    }

    @Test
    fun `radius followTheChicken invalid hint falls back to medium`() {
        val r = computeZoneRadius(Point.fromLngLat(4.35, 50.85), null, GameMod.FOLLOW_THE_CHICKEN, 1337.0)
        assertEquals(1000.0, r, 0.0)
    }

    @Test
    fun `radius followTheChicken nil hint falls back to medium`() {
        val r = computeZoneRadius(Point.fromLngLat(4.35, 50.85), null, GameMod.FOLLOW_THE_CHICKEN, null)
        assertEquals(1000.0, r, 0.0)
    }

    // ── generateDriftSeed ─────────────────────────────────────────────

    @Test
    fun `generateDriftSeed never returns zero`() {
        // 0 is treated as "no drift" by the runtime PRNG. The helper
        // re-rolls until it gets a positive value — 1000 samples is
        // statistically more than enough.
        repeat(1000) {
            assertTrue(generateDriftSeed() > 0)
        }
    }

    // ── interpolateZoneCenter — strict goldens ────────────────────────

    @Test
    fun `interpolate at zero progress returns initial`() {
        val out = dev.rahier.pouleparty.ui.gamelogic.interpolateZoneCenter(
            initialCenter = Point.fromLngLat(4.35, 50.85),
            finalCenter = Point.fromLngLat(4.36, 50.86),
            initialRadius = 1500.0,
            currentRadius = 1500.0,
        )
        assertEquals(50.85, out.latitude(), interpolateTolerance)
        assertEquals(4.35, out.longitude(), interpolateTolerance)
    }

    @Test
    fun `interpolate at 50 percent progress returns midpoint`() {
        val out = dev.rahier.pouleparty.ui.gamelogic.interpolateZoneCenter(
            initialCenter = Point.fromLngLat(4.35, 50.85),
            finalCenter = Point.fromLngLat(4.37, 50.87),
            initialRadius = 1500.0,
            currentRadius = 750.0,
        )
        assertEquals(50.86, out.latitude(), interpolateTolerance)
        assertEquals(4.36, out.longitude(), interpolateTolerance)
    }

    @Test
    fun `interpolate at 100 percent progress returns final`() {
        val out = dev.rahier.pouleparty.ui.gamelogic.interpolateZoneCenter(
            initialCenter = Point.fromLngLat(4.35, 50.85),
            finalCenter = Point.fromLngLat(4.37, 50.87),
            initialRadius = 1500.0,
            currentRadius = 0.0,
        )
        assertEquals(50.87, out.latitude(), interpolateTolerance)
        assertEquals(4.37, out.longitude(), interpolateTolerance)
    }

    // ── deterministicDriftCenter — same seed → same output ────────────

    @Test
    fun `drift deterministic same inputs same output`() {
        val base = Point.fromLngLat(4.35, 50.85)
        val a = dev.rahier.pouleparty.ui.gamelogic.deterministicDriftCenter(
            basePoint = base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 12345,
        )
        val b = dev.rahier.pouleparty.ui.gamelogic.deterministicDriftCenter(
            basePoint = base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 12345,
        )
        assertEquals(a.latitude(), b.latitude(), 0.0)
        assertEquals(a.longitude(), b.longitude(), 0.0)
    }

    @Test
    fun `drift pinned golden seed 12345 from 1500 to 1400`() {
        val out = dev.rahier.pouleparty.ui.gamelogic.deterministicDriftCenter(
            basePoint = Point.fromLngLat(4.35, 50.85),
            oldRadius = 1500.0,
            newRadius = 1400.0,
            driftSeed = 12345,
        )
        // Same constants as `ParityGoldenTest.drift seed 12345 from
        // 1500 to 1400`. Kept here so a `ZoneCalculationTest` run on
        // its own still catches a regression without depending on the
        // wider parity suite.
        assertEquals(50.84923912478917, out.latitude(), interpolateTolerance)
        assertEquals(4.349340564597558, out.longitude(), interpolateTolerance)
    }

    // ── pickInitialZoneCenter — same seed yields same center ──────────

    @Test
    fun `pickInitialZoneCenter is deterministic for same seed`() {
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.36, 50.86)
        val a = pickInitialZoneCenter(start, finalCenter, radius = 1668.0, seed = 42)
        val b = pickInitialZoneCenter(start, finalCenter, radius = 1668.0, seed = 42)
        assertEquals(a.latitude(), b.latitude(), 0.0)
        assertEquals(a.longitude(), b.longitude(), 0.0)
    }

    @Test
    fun `pickInitialZoneCenter respects containment lens`() {
        // For ANY seed in 1..32, the picked center must keep both pins
        // inside the disc of `radius` around it. PP-13 / PP-69
        // contract: user-placed pins live inside the disc as markers,
        // not at its center, and never escape it.
        val start = Point.fromLngLat(4.35, 50.85)
        val finalCenter = Point.fromLngLat(4.36, 50.86)
        val radius = 1668.0
        for (seed in 1..32) {
            val centre = pickInitialZoneCenter(start, finalCenter, radius, seed)
            val startResults = FloatArray(1)
            Location.distanceBetween(
                centre.latitude(), centre.longitude(),
                start.latitude(), start.longitude(),
                startResults,
            )
            val finalResults = FloatArray(1)
            Location.distanceBetween(
                centre.latitude(), centre.longitude(),
                finalCenter.latitude(), finalCenter.longitude(),
                finalResults,
            )
            assertTrue("seed $seed start escaped", startResults[0] <= radius + 1.0)
            assertTrue("seed $seed final escaped", finalResults[0] <= radius + 1.0)
        }
    }
}
