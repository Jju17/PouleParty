package dev.rahier.pouleparty.ui.gamelogic

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.powerups.logic.generatePowerUps
import dev.rahier.pouleparty.powerups.model.PowerUpType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks cross-platform determinism for the math that iOS, Android, and the
 * Cloud Functions all run. The golden values are authored once in the TS
 * reference (`functions/test/parity.test.ts`) and copied verbatim here + into
 * the iOS sibling (`ParityGoldenTests.swift`). If any platform ever drifts,
 * these tests flag it immediately instead of hunters seeing different decoys /
 * drifts / spawn positions in the same game.
 */
class ParityGoldenTest {

    // Geo math is double-precision; 1e-9 ° ≈ 0.11 mm, well below anything
    // visible on the map, and tight enough that a real divergence still fails.
    private val tol = 1e-9

    // ── interpolateZoneCenter ───────────────────────────────

    @Test
    fun `interpolate half progress`() {
        val out = interpolateZoneCenter(
            initialCenter = Point.fromLngLat(4.35, 50.85),
            finalCenter = Point.fromLngLat(4.37, 50.87),
            initialRadius = 1500.0,
            currentRadius = 750.0,
        )
        assertEquals(50.86, out.latitude(), tol)
        assertEquals(4.36, out.longitude(), tol)
    }

    @Test
    fun `interpolate missing final returns initial`() {
        val initial = Point.fromLngLat(4.35, 50.85)
        val out = interpolateZoneCenter(
            initialCenter = initial,
            finalCenter = null,
            initialRadius = 1500.0,
            currentRadius = 750.0,
        )
        assertEquals(initial.latitude(), out.latitude(), 0.0)
        assertEquals(initial.longitude(), out.longitude(), 0.0)
    }

    @Test
    fun `interpolate zero progress returns final`() {
        val out = interpolateZoneCenter(
            initialCenter = Point.fromLngLat(2.3, 48.8),
            finalCenter = Point.fromLngLat(2.4, 48.9),
            initialRadius = 2000.0,
            currentRadius = 0.0,
        )
        assertEquals(48.9, out.latitude(), tol)
        assertEquals(2.4, out.longitude(), tol)
    }

    @Test
    fun `interpolate full radius returns initial`() {
        val initial = Point.fromLngLat(4.35, 50.85)
        val out = interpolateZoneCenter(
            initialCenter = initial,
            finalCenter = Point.fromLngLat(4.4, 50.9),
            initialRadius = 1500.0,
            currentRadius = 1500.0,
        )
        assertEquals(initial.latitude(), out.latitude(), 0.0)
        assertEquals(initial.longitude(), out.longitude(), 0.0)
    }

    // ── deterministicDriftCenter ────────────────────────────

    @Test
    fun `drift seed 12345 from 1500 to 1400`() {
        val out = deterministicDriftCenter(
            basePoint = Point.fromLngLat(4.35, 50.85),
            oldRadius = 1500.0,
            newRadius = 1400.0,
            driftSeed = 12345,
        )
        assertEquals(50.849704286836015, out.latitude(), tol)
        assertEquals(4.349626765727747, out.longitude(), tol)
    }

    @Test
    fun `drift seed 42 from 2000 to 1800`() {
        val out = deterministicDriftCenter(
            basePoint = Point.fromLngLat(2.3, 48.8),
            oldRadius = 2000.0,
            newRadius = 1800.0,
            driftSeed = 42,
        )
        assertEquals(48.800889182782186, out.latitude(), tol)
        assertEquals(2.3001280811249516, out.longitude(), tol)
    }

    @Test
    fun `drift new radius zero returns base`() {
        val base = Point.fromLngLat(4.35, 50.85)
        val out = deterministicDriftCenter(
            basePoint = base,
            oldRadius = 500.0,
            newRadius = 0.0,
            driftSeed = 7,
        )
        assertEquals(base.latitude(), out.latitude(), 0.0)
        assertEquals(base.longitude(), out.longitude(), 0.0)
    }

    @Test
    fun `drift same radius returns base`() {
        val base = Point.fromLngLat(4.35, 50.85)
        val out = deterministicDriftCenter(
            basePoint = base,
            oldRadius = 1500.0,
            newRadius = 1500.0,
            driftSeed = 12345,
        )
        assertEquals(base.latitude(), out.latitude(), 0.0)
        assertEquals(base.longitude(), out.longitude(), 0.0)
    }

    // ── generatePowerUps ────────────────────────────────────

    @Test
    fun `generate power-ups batch 1 count 3 seed 12345`() {
        val out = generatePowerUps(
            center = Point.fromLngLat(4.35, 50.85),
            radius = 1500.0,
            count = 3,
            driftSeed = 12345,
            batchIndex = 1,
            enabledTypes = listOf("invisibility", "zoneFreeze", "radarPing"),
        )
        assertEquals(3, out.size)

        assertEquals("pu-1-0-371690", out[0].id)
        assertEquals(PowerUpType.RADAR_PING, out[0].typeEnum)
        assertEquals(50.85167604533923, out[0].location.latitude, tol)
        assertEquals(4.36041475997135, out[0].location.longitude, tol)

        assertEquals("pu-1-1-371817", out[1].id)
        assertEquals(PowerUpType.INVISIBILITY, out[1].typeEnum)
        assertEquals(50.84442779979479, out[1].location.latitude, tol)
        assertEquals(4.356573765300285, out[1].location.longitude, tol)

        assertEquals("pu-1-2-371944", out[2].id)
        assertEquals(PowerUpType.ZONE_FREEZE, out[2].typeEnum)
        assertEquals(50.84439414095354, out[2].location.latitude, tol)
        assertEquals(4.344395523809516, out[2].location.longitude, tol)
    }

    // ── seededRandom ────────────────────────────────────────

    @Test
    fun `seeded random golden vectors`() {
        assertEquals(0.0, seededRandom(0L, 0), tol)
        assertEquals(0.3381666012719898, seededRandom(1L, 0), tol)
        assertEquals(0.9508810691208036, seededRandom(12345L, 0), tol)
        assertEquals(0.13307966866142731, seededRandom(12345L, 1), tol)
        assertEquals(0.21840519371218445, seededRandom(42L, 7), tol)
        assertEquals(0.7063039534139497, seededRandom(-1L, 0), tol)
    }

    // ── generatePowerUps, ordering & edges ─────────────────

    @Test
    fun `generate power-ups preserves enabled types order`() {
        // Same inputs as the main test but enabledTypes is reversed → the
        // types at each index must permute too. If Android ever drifts back
        // to entries-filter ordering this fails loudly.
        val out = generatePowerUps(
            center = Point.fromLngLat(4.35, 50.85),
            radius = 1500.0,
            count = 3,
            driftSeed = 12345,
            batchIndex = 1,
            enabledTypes = listOf("radarPing", "zoneFreeze", "invisibility"),
        )
        assertEquals(
            listOf(PowerUpType.INVISIBILITY, PowerUpType.RADAR_PING, PowerUpType.ZONE_FREEZE),
            out.map { it.typeEnum },
        )
    }

    @Test
    fun `generate power-ups single type fills every slot`() {
        val out = generatePowerUps(
            center = Point.fromLngLat(4.35, 50.85),
            radius = 1500.0,
            count = 3,
            driftSeed = 12345,
            batchIndex = 1,
            enabledTypes = listOf("invisibility"),
        )
        assertEquals(
            listOf(PowerUpType.INVISIBILITY, PowerUpType.INVISIBILITY, PowerUpType.INVISIBILITY),
            out.map { it.typeEnum },
        )
    }

    @Test
    fun `generate power-ups zero count returns empty`() {
        val out = generatePowerUps(
            center = Point.fromLngLat(0.0, 0.0),
            radius = 1500.0,
            count = 0,
            driftSeed = 12345,
            batchIndex = 1,
            enabledTypes = listOf("invisibility"),
        )
        assertEquals(0, out.size)
    }

    @Test
    fun `generate power-ups empty types returns empty`() {
        val out = generatePowerUps(
            center = Point.fromLngLat(0.0, 0.0),
            radius = 1500.0,
            count = 5,
            driftSeed = 12345,
            batchIndex = 1,
            enabledTypes = emptyList(),
        )
        assertEquals(0, out.size)
    }

    @Test
    fun `generate power-ups negative seed batch 0`() {
        val out = generatePowerUps(
            center = Point.fromLngLat(4.35, 50.85),
            radius = 1500.0,
            count = 2,
            driftSeed = -1,
            batchIndex = 0,
            enabledTypes = listOf("invisibility", "radarPing"),
        )
        assertEquals(2, out.size)
        assertEquals("pu-0-0-31", out[0].id)
        assertEquals(PowerUpType.RADAR_PING, out[0].typeEnum)
        assertEquals(50.85543657219541, out[0].location.latitude, tol)
        assertEquals(4.3525392519978965, out[0].location.longitude, tol)
        assertEquals("pu-0-1-96", out[1].id)
        assertEquals(PowerUpType.INVISIBILITY, out[1].typeEnum)
        assertEquals(50.85637613935454, out[1].location.latitude, tol)
        assertEquals(4.362005903305925, out[1].location.longitude, tol)
    }

    // ── interpolate edges ───────────────────────────────────

    @Test
    fun `interpolate clamps when currentRadius exceeds initial`() {
        val initial = Point.fromLngLat(4.35, 50.85)
        val out = interpolateZoneCenter(
            initialCenter = initial,
            finalCenter = Point.fromLngLat(4.37, 50.87),
            initialRadius = 1500.0,
            currentRadius = 2000.0,
        )
        assertEquals(initial.latitude(), out.latitude(), 0.0)
        assertEquals(initial.longitude(), out.longitude(), 0.0)
    }

    @Test
    fun `interpolate initialRadius zero is guard`() {
        val initial = Point.fromLngLat(4.35, 50.85)
        val out = interpolateZoneCenter(
            initialCenter = initial,
            finalCenter = Point.fromLngLat(4.4, 50.9),
            initialRadius = 0.0,
            currentRadius = 0.0,
        )
        assertEquals(initial.latitude(), out.latitude(), 0.0)
        assertEquals(initial.longitude(), out.longitude(), 0.0)
    }

    // ── drift edges ─────────────────────────────────────────

    @Test
    fun `drift big shrink 2000 to 500`() {
        val out = deterministicDriftCenter(
            basePoint = Point.fromLngLat(4.35, 50.85),
            oldRadius = 2000.0,
            newRadius = 500.0,
            driftSeed = 777,
        )
        assertEquals(50.849217523608516, out.latitude(), tol)
        assertEquals(4.34728423145545, out.longitude(), tol)
    }

    @Test
    fun `drift negative seed`() {
        val out = deterministicDriftCenter(
            basePoint = Point.fromLngLat(4.35, 50.85),
            oldRadius = 1500.0,
            newRadius = 1400.0,
            driftSeed = -99,
        )
        assertEquals(50.85023537723916, out.latitude(), tol)
        assertEquals(4.350282673772987, out.longitude(), tol)
    }

    // ── finalCenter invariant (see functions/test/parity.test.ts) ─

    /**
     * Flat-earth distance in meters between two points using the same
     * conversion `deterministicDriftCenter` applies internally, so the
     * invariant check is self-consistent with the drift formula.
     */
    private fun distMeters(a: Point, b: Point): Double {
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLng = 111_320.0 * kotlin.math.cos(a.latitude() * kotlin.math.PI / 180.0)
        val dLatM = (b.latitude() - a.latitude()) * metersPerDegreeLat
        val dLngM = (b.longitude() - a.longitude()) * metersPerDegreeLng
        return kotlin.math.sqrt(dLatM * dLatM + dLngM * dLngM)
    }

    @Test
    fun `drift finalCenter constrains drift when base is near the edge`() {
        val finalCenter = Point.fromLngLat(4.36, 50.86)
        val out = deterministicDriftCenter(
            basePoint = Point.fromLngLat(4.35, 50.85),
            oldRadius = 2000.0,
            newRadius = 1400.0,
            driftSeed = 12345,
            finalCenter = finalCenter,
        )
        assertEquals(50.84951207475732, out.latitude(), tol)
        assertEquals(4.349384165316106, out.longitude(), tol)
        if (distMeters(out, finalCenter) > 1400.0) {
            throw AssertionError("invariant broken: finalCenter not inside drifted circle")
        }
    }

    @Test
    fun `drift missing finalCenter leaves existing behavior untouched`() {
        val explicitNull = deterministicDriftCenter(
            basePoint = Point.fromLngLat(4.35, 50.85),
            oldRadius = 1500.0,
            newRadius = 1400.0,
            driftSeed = 12345,
            finalCenter = null,
        )
        assertEquals(50.849704286836015, explicitNull.latitude(), tol)
        assertEquals(4.349626765727747, explicitNull.longitude(), tol)
    }

    @Test
    fun `drift finalCenter outside new circle returns base`() {
        val base = Point.fromLngLat(4.35, 50.85)
        val farFinal = Point.fromLngLat(4.35, 50.85 + 150.0 / 111_320.0)
        val out = deterministicDriftCenter(
            basePoint = base,
            oldRadius = 200.0,
            newRadius = 100.0,
            driftSeed = 12345,
            finalCenter = farFinal,
        )
        assertEquals(base.latitude(), out.latitude(), 0.0)
        assertEquals(base.longitude(), out.longitude(), 0.0)
    }

    @Test
    fun `drift invariant sweep finalCenter inside every circle`() {
        // Exhaustive property sweep mirroring the TS parity test: 100 seeds
        // × 7 finalCenter distances × 10 shrink steps. If the invariant
        // breaks for any combination, the platform has drifted.
        val initialCenter = Point.fromLngLat(4.35, 50.85)
        val initialRadius = 1500.0
        val finalDistancesM = doubleArrayOf(0.0, 100.0, 400.0, 700.0, 1000.0, 1300.0, 1499.0)
        for (dM in finalDistancesM) {
            val finalCenter = Point.fromLngLat(
                initialCenter.longitude(),
                initialCenter.latitude() + dM / 111_320.0,
            )
            for (seed in 1..100) {
                var radius = initialRadius
                repeat(10) {
                    val newRadius = radius - 100.0
                    if (newRadius <= 0.0) return@repeat
                    val interpolated = interpolateZoneCenter(
                        initialCenter = initialCenter,
                        finalCenter = finalCenter,
                        initialRadius = initialRadius,
                        currentRadius = newRadius,
                    )
                    val drifted = deterministicDriftCenter(
                        basePoint = interpolated,
                        oldRadius = radius,
                        newRadius = newRadius,
                        driftSeed = seed,
                        finalCenter = finalCenter,
                    )
                    val d = distMeters(drifted, finalCenter)
                    if (d > newRadius) {
                        throw AssertionError(
                            "invariant broken: seed=$seed finalDist=$dM " +
                                "distToFinal=$d newRadius=$newRadius"
                        )
                    }
                    radius = newRadius
                }
            }
        }
    }

    // ── applyJammerNoise, iOS ↔ Android golden ─────────────

    @Test
    fun `jammer golden seed 12345 now 0`() {
        val out = applyJammerNoise(
            coordinate = Point.fromLngLat(4.35, 50.85),
            driftSeed = 12345,
            nowMillis = 0L,
        )
        assertEquals(50.851623171848836, out.latitude(), tol)
        assertEquals(4.348679086807181, out.longitude(), tol)
    }

    @Test
    fun `jammer within same second is stable`() {
        val coord = Point.fromLngLat(4.35, 50.85)
        val a = applyJammerNoise(coord, 12345, 0L)
        val b = applyJammerNoise(coord, 12345, 500L)
        val c = applyJammerNoise(coord, 12345, 999L)
        assertEquals(a.latitude(), b.latitude(), 0.0)
        assertEquals(a.longitude(), b.longitude(), 0.0)
        assertEquals(a.latitude(), c.latitude(), 0.0)
        assertEquals(a.longitude(), c.longitude(), 0.0)
    }

    @Test
    fun `jammer crosses bucket at 1 second`() {
        val coord = Point.fromLngLat(4.35, 50.85)
        val before = applyJammerNoise(coord, 12345, 999L)
        val after = applyJammerNoise(coord, 12345, 1000L)
        val changed = before.latitude() != after.latitude() || before.longitude() != after.longitude()
        assertEquals(true, changed)
        assertEquals(50.84841369080797, after.latitude(), tol)
        assertEquals(4.351738880923983, after.longitude(), tol)
    }

    @Test
    fun `jammer golden zero seed zero now`() {
        val out = applyJammerNoise(Point.fromLngLat(4.35, 50.85), 0, 0L)
        assertEquals(50.8482, out.latitude(), tol)
        assertEquals(4.3513799189095685, out.longitude(), tol)
    }

    @Test
    fun `jammer golden negative seed`() {
        val out = applyJammerNoise(Point.fromLngLat(4.35, 50.85), -1, 0L)
        assertEquals(50.850742694232295, out.latitude(), tol)
        assertEquals(4.351418194513019, out.longitude(), tol)
    }

    @Test
    fun `jammer golden realtime bucket`() {
        // Realistic Stripe-era timestamp, catches 64-bit XOR drift.
        val out = applyJammerNoise(Point.fromLngLat(4.35, 50.85), 777, 1_700_000_000_000L)
        assertEquals(50.85092634988487, out.latitude(), tol)
        assertEquals(4.3504428176455745, out.longitude(), tol)
    }

    @Test
    fun `jammer stays within half-noise bounds across 1000 buckets`() {
        val coord = Point.fromLngLat(4.35, 50.85)
        val halfNoise = dev.rahier.pouleparty.AppConstants.JAMMER_NOISE_DEGREES / 2.0
        for (i in 0 until 1000) {
            val out = applyJammerNoise(coord, 12345, i * 1000L)
            val latDiff = kotlin.math.abs(out.latitude() - coord.latitude())
            val lonDiff = kotlin.math.abs(out.longitude() - coord.longitude())
            if (latDiff > halfNoise + 1e-12 || lonDiff > halfNoise + 1e-12) {
                throw AssertionError("bucket=$i latDiff=$latDiff lonDiff=$lonDiff halfNoise=$halfNoise")
            }
        }
    }
}
