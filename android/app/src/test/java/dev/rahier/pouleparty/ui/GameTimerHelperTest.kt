package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.gamelogic.*

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.model.calculateNormalModeSettings
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class GameTimerHelperTest {

    // ── evaluateCountdown ────────────────────────────

    @Test
    fun `countdown no change when far from target`() {
        val phase = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() + 60_000),
            completionText = "GO!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = Date(),
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.NoChange, result)
    }

    @Test
    fun `countdown shows number when within threshold`() {
        val phase = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() + 2500),
            completionText = "GO!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = Date(),
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.UpdateNumber(3), result)
    }

    @Test
    fun `countdown no change when number already shown`() {
        val phase = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() + 2500),
            completionText = "GO!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = Date(),
            currentCountdownNumber = 3,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.NoChange, result)
    }

    @Test
    fun `countdown shows text on completion`() {
        val phase = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() - 500),
            completionText = "RUN!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = Date(),
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.ShowText("RUN!"), result)
    }

    @Test
    fun `countdown skips disabled phases`() {
        val disabled = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() + 2000),
            completionText = "SKIP",
            showNumericCountdown = true,
            isEnabled = false
        )
        val enabled = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() - 500),
            completionText = "GO!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(disabled, enabled),
            now = Date(),
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.ShowText("GO!"), result)
    }

    @Test
    fun `countdown no change after all phases complete`() {
        val phase = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() - 5000),
            completionText = "GO!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = Date(),
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.NoChange, result)
    }

    @Test
    fun `countdown no change when text already showing`() {
        val phase = CountdownPhase(
            targetDate = Date(System.currentTimeMillis() - 500),
            completionText = "RUN!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = Date(),
            currentCountdownNumber = null,
            currentCountdownText = "RUN!"
        )
        assertEquals(CountdownResult.NoChange, result)
    }

    // ── checkGameOverByTime ──────────────────────────

    @Test
    fun `game over when past end date`() {
        assertTrue(checkGameOverByTime(Date(System.currentTimeMillis() - 1000)))
    }

    @Test
    fun `game not over when before end date`() {
        assertFalse(checkGameOverByTime(Date(System.currentTimeMillis() + 60_000)))
    }

    // ── processRadiusUpdate ──────────────────────────

    @Test
    fun `radius update returns null when no next update`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = null,
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = null
        )
        assertNull(result)
    }

    @Test
    fun `radius update returns null when not due yet`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() + 60_000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = Point.fromLngLat(4.0, 50.0)
        )
        assertNull(result)
    }

    @Test
    fun `radius update shrinks when due`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = Point.fromLngLat(4.0, 50.0)
        )
        assertNotNull(result)
        assertEquals(1400, result!!.newRadius)
        assertFalse(result.isGameOver)
    }

    @Test
    fun `radius update game over when radius reaches zero`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 100,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = null
        )
        assertNotNull(result)
        assertTrue(result!!.isGameOver)
        assertEquals("The zone has collapsed!", result.gameOverMessage)
    }

    @Test
    fun `radius update uses initial location for stayInTheZone`() {
        val initialLocation = Point.fromLngLat(4.0, 50.0)
        val currentCenter = Point.fromLngLat(5.0, 51.0)
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.STAY_IN_THE_ZONE,
            initialLocation = initialLocation,
            currentCircleCenter = currentCenter,
            driftSeed = 12345
        )
        assertNotNull(result)
        // With drift, center is close to initialLocation but not exactly equal
        val center = result!!.newCircleCenter!!
        val dLat = Math.abs(center.latitude() - initialLocation.latitude()) * 111_320
        val dLng = Math.abs(center.longitude() - initialLocation.longitude()) * 111_320 * Math.cos(Math.toRadians(initialLocation.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        // Max drift = min(1400*0.5, 100*0.5) = 50m
        assertTrue("Drifted center should be within 60m of base point, was ${distance}m", distance < 60)
    }

    // ── deterministicDriftCenter ────────────────────

    @Test
    fun `drift center is deterministic`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val r1 = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 12345)
        val r2 = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 12345)
        assertEquals(r1.latitude(), r2.latitude(), 0.0)
        assertEquals(r1.longitude(), r2.longitude(), 0.0)
    }

    @Test
    fun `drift center differs for different radius`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val r1 = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 12345)
        val r2 = deterministicDriftCenter(base, oldRadius = 1400.0, newRadius = 1300.0, driftSeed = 12345)
        val areDifferent = r1.latitude() != r2.latitude() || r1.longitude() != r2.longitude()
        assertTrue("Different radius values should produce different drift centers", areDifferent)
    }

    @Test
    fun `drift center differs for different seeds`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val r1 = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 11111)
        val r2 = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 99999)
        val areDifferent = r1.latitude() != r2.latitude() || r1.longitude() != r2.longitude()
        assertTrue("Different seeds should produce different drift centers", areDifferent)
    }

    @Test
    fun `drift center stays close to base`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 54321)
        val dLat = Math.abs(result.latitude() - base.latitude()) * 111_320
        val dLng = Math.abs(result.longitude() - base.longitude()) * 111_320 * Math.cos(Math.toRadians(base.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        // safeDrift = min(1400*0.5, 100*0.5) = 50m
        assertTrue("Drifted center must be within safeDrift of base, was ${distance}m", distance <= 50.01)
    }

    @Test
    fun `drift center returns base when no room`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1000.0, newRadius = 1000.0, driftSeed = 12345)
        assertEquals(base.latitude(), result.latitude(), 0.0)
        assertEquals(base.longitude(), result.longitude(), 0.0)
    }

    // ── detectNewWinners ─────────────────────────────

    @Test
    fun `detect new winners returns null when no new winners`() {
        val winners = listOf(
            Winner("h1", "Alice", Timestamp.now())
        )
        assertNull(detectNewWinners(winners, previousCount = 1))
    }

    @Test
    fun `detect new winners returns notification`() {
        val winners = listOf(
            Winner("h1", "Alice", Timestamp.now()),
            Winner("h2", "Bob", Timestamp.now())
        )
        assertEquals("Bob found the chicken! 🐔", detectNewWinners(winners, previousCount = 1))
    }

    @Test
    fun `detect new winners filters own hunter`() {
        val winners = listOf(
            Winner("h1", "Alice", Timestamp.now()),
            Winner("me", "Me", Timestamp.now())
        )
        assertNull(detectNewWinners(winners, previousCount = 1, ownHunterId = "me"))
    }

    @Test
    fun `detect new winners with empty list`() {
        assertNull(detectNewWinners(emptyList(), previousCount = 0))
    }

    @Test
    fun `detect new winners from zero`() {
        val winners = listOf(
            Winner("h1", "Alice", Timestamp.now())
        )
        assertEquals("Alice found the chicken! 🐔", detectNewWinners(winners, previousCount = 0))
    }

    // ── shouldCheckZone ────────────────────────────────

    // ── Zone freeze ──────────────────────────────────

    @Test
    fun `processRadiusUpdate returns null when zone is frozen`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.STAY_IN_THE_ZONE,
            initialLocation = Point.fromLngLat(4.3528, 50.8466),
            currentCircleCenter = null,
            isZoneFrozen = true
        )
        assertNull(result)
    }

    @Test
    fun `processRadiusUpdate proceeds when zone is not frozen`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.STAY_IN_THE_ZONE,
            initialLocation = Point.fromLngLat(4.3528, 50.8466),
            currentCircleCenter = null,
            isZoneFrozen = false
        )
        assertNotNull(result)
        assertEquals(1400, result!!.newRadius)
    }

    // ── shouldCheckZone ────────────────────────────────

    @Test
    fun `shouldCheckZone chicken stayInTheZone returns true`() {
        assertTrue(shouldCheckZone(PlayerRole.CHICKEN, GameMod.STAY_IN_THE_ZONE))
    }

    @Test
    fun `shouldCheckZone hunter stayInTheZone returns true`() {
        assertTrue(shouldCheckZone(PlayerRole.HUNTER, GameMod.STAY_IN_THE_ZONE))
    }

    @Test
    fun `shouldCheckZone chicken followTheChicken returns false`() {
        assertFalse(shouldCheckZone(PlayerRole.CHICKEN, GameMod.FOLLOW_THE_CHICKEN))
    }

    @Test
    fun `shouldCheckZone hunter followTheChicken returns true`() {
        assertTrue(shouldCheckZone(PlayerRole.HUNTER, GameMod.FOLLOW_THE_CHICKEN))
    }

    // ── seededRandom (splitmix64 cross-platform parity) ──

    @Test
    fun `seededRandom is deterministic`() {
        val a = seededRandom(42L, 0)
        val b = seededRandom(42L, 0)
        assertEquals(a, b, 0.0)
    }

    @Test
    fun `seededRandom differs for different index`() {
        val a = seededRandom(42L, 0)
        val b = seededRandom(42L, 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `seededRandom differs for different seed`() {
        val a = seededRandom(100L, 0)
        val b = seededRandom(200L, 0)
        assertNotEquals(a, b)
    }

    @Test
    fun `seededRandom returns value in 0-1 range`() {
        val seeds = listOf(0L, 1L, 42L, 999_999L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)
        val indices = listOf(0, 1, 5, 100)
        for (seed in seeds) {
            for (idx in indices) {
                val v = seededRandom(seed, idx)
                assertTrue("seed=$seed index=$idx produced $v", v >= 0.0)
                assertTrue("seed=$seed index=$idx produced $v", v < 1.0)
            }
        }
    }

    /**
     * Hard-coded expected values verified against iOS output.
     * If these break, cross-platform parity is lost — fix both platforms.
     */
    @Test
    fun `seededRandom cross-platform parity`() {
        assertEquals(0.6537157389870546, seededRandom(42L, 0), 1e-15)
        assertEquals(0.7415648787718234, seededRandom(42L, 1), 1e-15)
        assertEquals(0.9508810691208036, seededRandom(12345L, 0), 1e-15)
        assertEquals(0.0, seededRandom(0L, 0), 1e-15)
        assertEquals(0.6193696364984258, seededRandom(999_999L, 5), 1e-15)
    }

    // ── deterministicDriftCenter with large seeds ────

    @Test
    fun `drift center handles large seed`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 500_000_000)
        val dLat = Math.abs(result.latitude() - base.latitude()) * 111_320
        val dLng = Math.abs(result.longitude() - base.longitude()) * 111_320 * Math.cos(Math.toRadians(base.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        assertTrue("Large seed must produce valid drift within bounds, was ${distance}m", distance <= 50.01)
    }

    @Test
    fun `drift center handles negative seed`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = -42)
        val dLat = Math.abs(result.latitude() - base.latitude()) * 111_320
        val dLng = Math.abs(result.longitude() - base.longitude()) * 111_320 * Math.cos(Math.toRadians(base.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        assertTrue("Negative seed must produce valid drift, was ${distance}m", distance <= 50.01)
    }

    // ── evaluateCountdown with explicit now ──────────

    @Test
    fun `countdown with explicit now shows number`() {
        val target = Date(1_000_000L) // epoch + 1000s
        val now = Date(997_500L)      // 2.5s before target
        val phase = CountdownPhase(
            targetDate = target,
            completionText = "GO!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = now,
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.UpdateNumber(3), result)
    }

    @Test
    fun `countdown with explicit now shows text after target`() {
        val target = Date(1_000_000L)
        val now = Date(1_000_500L) // 0.5s after target
        val phase = CountdownPhase(
            targetDate = target,
            completionText = "RUN!",
            showNumericCountdown = true,
            isEnabled = true
        )
        val result = evaluateCountdown(
            phases = listOf(phase),
            now = now,
            currentCountdownNumber = null,
            currentCountdownText = null
        )
        assertEquals(CountdownResult.ShowText("RUN!"), result)
    }

    // ── interpolateZoneCenter edge cases ────────────

    @Test
    fun `interpolate center returns initial when no final`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val result = interpolateZoneCenter(initial, finalCenter = null, initialRadius = 1500.0, currentRadius = 750.0)
        assertEquals(initial.latitude(), result.latitude(), 0.0)
        assertEquals(initial.longitude(), result.longitude(), 0.0)
    }

    @Test
    fun `interpolate center returns final when radius zero`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val final_ = Point.fromLngLat(5.0, 51.0)
        val result = interpolateZoneCenter(initial, finalCenter = final_, initialRadius = 1500.0, currentRadius = 0.0)
        assertEquals(final_.latitude(), result.latitude(), 0.0001)
        assertEquals(final_.longitude(), result.longitude(), 0.0001)
    }

    @Test
    fun `interpolate center midpoint`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val final_ = Point.fromLngLat(6.0, 52.0)
        val result = interpolateZoneCenter(initial, finalCenter = final_, initialRadius = 1000.0, currentRadius = 500.0)
        assertEquals(51.0, result.latitude(), 0.0001)
        assertEquals(5.0, result.longitude(), 0.0001)
    }

    @Test
    fun `interpolate center clamps negative radius`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val final_ = Point.fromLngLat(6.0, 52.0)
        val result = interpolateZoneCenter(initial, finalCenter = final_, initialRadius = 1000.0, currentRadius = -100.0)
        assertEquals(final_.latitude(), result.latitude(), 0.0001)
        assertEquals(final_.longitude(), result.longitude(), 0.0001)
    }

    @Test
    fun `interpolate center returns initial when zero initial radius`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val final_ = Point.fromLngLat(6.0, 52.0)
        val result = interpolateZoneCenter(initial, finalCenter = final_, initialRadius = 0.0, currentRadius = 0.0)
        assertEquals(initial.latitude(), result.latitude(), 0.0)
        assertEquals(initial.longitude(), result.longitude(), 0.0)
    }

    @Test
    fun `interpolate center returns initial when current exceeds initial`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val final_ = Point.fromLngLat(6.0, 52.0)
        val result = interpolateZoneCenter(initial, finalCenter = final_, initialRadius = 1000.0, currentRadius = 1500.0)
        assertEquals(initial.latitude(), result.latitude(), 0.0001)
        assertEquals(initial.longitude(), result.longitude(), 0.0001)
    }

    @Test
    fun `interpolate center same initial and final`() {
        val point = Point.fromLngLat(4.0, 50.0)
        val result = interpolateZoneCenter(point, finalCenter = point, initialRadius = 1000.0, currentRadius = 500.0)
        assertEquals(point.latitude(), result.latitude(), 0.0001)
        assertEquals(point.longitude(), result.longitude(), 0.0001)
    }

    // ── deterministicDriftCenter exhaustive edge cases ──

    @Test
    fun `drift center newRadius zero returns base`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 100.0, newRadius = 0.0, driftSeed = 42)
        assertEquals(base.latitude(), result.latitude(), 0.0)
        assertEquals(base.longitude(), result.longitude(), 0.0)
    }

    @Test
    fun `drift center newRadius larger than old returns base`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 100.0, newRadius = 200.0, driftSeed = 42)
        assertEquals(base.latitude(), result.latitude(), 0.0)
        assertEquals(base.longitude(), result.longitude(), 0.0)
    }

    @Test
    fun `drift center max int seed`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = Int.MAX_VALUE)
        val dLat = Math.abs(result.latitude() - base.latitude()) * 111_320
        val dLng = Math.abs(result.longitude() - base.longitude()) * 111_320 * Math.cos(Math.toRadians(base.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        assertTrue("Int.MAX seed must produce valid drift, was ${distance}m", distance <= 50.01)
    }

    @Test
    fun `drift center min int seed`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = Int.MIN_VALUE)
        val dLat = Math.abs(result.latitude() - base.latitude()) * 111_320
        val dLng = Math.abs(result.longitude() - base.longitude()) * 111_320 * Math.cos(Math.toRadians(base.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        assertTrue("Int.MIN seed must produce valid drift, was ${distance}m", distance <= 50.01)
    }

    @Test
    fun `drift center very small difference`() {
        val base = Point.fromLngLat(4.0, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1001.0, newRadius = 1000.0, driftSeed = 42)
        val dLat = Math.abs(result.latitude() - base.latitude()) * 111_320
        val dLng = Math.abs(result.longitude() - base.longitude()) * 111_320 * Math.cos(Math.toRadians(base.latitude()))
        val distance = Math.sqrt(dLat * dLat + dLng * dLng)
        assertTrue("Tiny drift for tiny radius difference, was ${distance}m", distance <= 0.51)
    }

    @Test
    fun `drift center near pole`() {
        val base = Point.fromLngLat(4.0, 89.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 42)
        assertTrue(result.latitude().isFinite())
        assertTrue(result.longitude().isFinite())
    }

    @Test
    fun `drift center near date line`() {
        val base = Point.fromLngLat(179.9, 50.0)
        val result = deterministicDriftCenter(base, oldRadius = 1500.0, newRadius = 1400.0, driftSeed = 42)
        assertTrue(result.latitude().isFinite())
        assertTrue(result.longitude().isFinite())
    }

    // ── seededRandom extreme edge cases ──────────────

    @Test
    fun `seededRandom negative index`() {
        val v = seededRandom(42L, -1)
        assertTrue("Negative index should produce value in [0,1), was $v", v >= 0.0 && v < 1.0)
    }

    @Test
    fun `seededRandom max index`() {
        val v = seededRandom(42L, Int.MAX_VALUE)
        assertTrue("Max index should produce value in [0,1), was $v", v >= 0.0 && v < 1.0)
    }

    // ── processRadiusUpdate edge cases ──────────────

    @Test
    fun `radius update decline zero still advances time`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 0.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = Point.fromLngLat(4.0, 50.0)
        )
        assertNotNull(result)
        assertEquals(1500, result!!.newRadius)
        assertFalse(result.isGameOver)
    }

    @Test
    fun `radius update fractional decline truncates`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 0.9,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = Point.fromLngLat(4.0, 50.0)
        )
        assertNotNull(result)
        assertEquals(1500, result!!.newRadius)
    }

    @Test
    fun `radius update decline larger than current triggers game over`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 50,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = null
        )
        assertNotNull(result)
        assertTrue(result!!.isGameOver)
        assertEquals(50, result.newRadius)
    }

    @Test
    fun `radius update followTheChicken no circle returns null center`() {
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN,
            initialLocation = Point.fromLngLat(4.0, 50.0),
            currentCircleCenter = null
        )
        assertNotNull(result)
        assertNull(result!!.newCircleCenter)
    }

    // ── evaluateCountdown edge cases ────────────────

    @Test
    fun `countdown empty phases returns no change`() {
        val result = evaluateCountdown(
            phases = emptyList(), now = Date(),
            currentCountdownNumber = null, currentCountdownText = null
        )
        assertEquals(CountdownResult.NoChange, result)
    }

    @Test
    fun `countdown exactly at threshold boundary`() {
        val target = Date(1_000_000L)
        val now = Date(997_000L) // exactly 3.0s before
        val phase = CountdownPhase(target, "GO!", showNumericCountdown = true, isEnabled = true)
        val result = evaluateCountdown(listOf(phase), now, null, null)
        assertEquals(CountdownResult.UpdateNumber(3), result)
    }

    @Test
    fun `countdown exactly on target shows text`() {
        // At t=0 exact: timeToTargetSec=0.0, strict > 0 check skips numeric countdown,
        // falls through to ShowText (matches iOS behavior)
        val target = Date(1_000_000L)
        val phase = CountdownPhase(target, "GO!", showNumericCountdown = true, isEnabled = true)
        val result = evaluateCountdown(listOf(phase), target, null, null)
        assertEquals(CountdownResult.ShowText("GO!"), result)
    }

    @Test
    fun `countdown non-numeric phase skips number`() {
        val target = Date(1_000_000L)
        val now = Date(998_000L) // 2s before
        val phase = CountdownPhase(target, "GO!", showNumericCountdown = false, isEnabled = true)
        val result = evaluateCountdown(listOf(phase), now, null, null)
        assertEquals(CountdownResult.NoChange, result)
    }

    // ── generatePowerUps edge cases ────────────────

    @Test
    fun `generatePowerUps count zero returns empty`() {
        val result = generatePowerUps(
            center = Point.fromLngLat(4.0, 50.0), radius = 1500.0,
            count = 0, driftSeed = 42, batchIndex = 0
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generatePowerUps empty enabled types returns empty`() {
        val result = generatePowerUps(
            center = Point.fromLngLat(4.0, 50.0), radius = 1500.0,
            count = 5, driftSeed = 42, batchIndex = 0, enabledTypes = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generatePowerUps is deterministic`() {
        val center = Point.fromLngLat(4.0, 50.0)
        val a = generatePowerUps(center, 1500.0, 3, 42, 0)
        val b = generatePowerUps(center, 1500.0, 3, 42, 0)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].id, b[i].id)
            assertEquals(a[i].location.latitude, b[i].location.latitude, 0.0)
            assertEquals(a[i].location.longitude, b[i].location.longitude, 0.0)
        }
    }

    @Test
    fun `generatePowerUps all within zone`() {
        val center = Point.fromLngLat(4.0, 50.0)
        val radius = 1500.0
        val powerUps = generatePowerUps(center, radius, 20, 12345, 0)
        for (pu in powerUps) {
            val dLat = Math.abs(pu.location.latitude - center.latitude()) * 111_320
            val dLng = Math.abs(pu.location.longitude - center.longitude()) * 111_320 * Math.cos(Math.toRadians(center.latitude()))
            val distance = Math.sqrt(dLat * dLat + dLng * dLng)
            assertTrue("Power-up at ${distance}m exceeds zone", distance <= radius * 0.85 + 1)
        }
    }

    @Test
    fun `generatePowerUps different seeds produce different positions`() {
        val center = Point.fromLngLat(4.0, 50.0)
        val a = generatePowerUps(center, 1500.0, 1, 111, 0)
        val b = generatePowerUps(center, 1500.0, 1, 222, 0)
        val different = a[0].location.latitude != b[0].location.latitude ||
                        a[0].location.longitude != b[0].location.longitude
        assertTrue("Different seeds should produce different positions", different)
    }

    @Test
    fun `generatePowerUps radius zero spawns at center`() {
        val center = Point.fromLngLat(4.0, 50.0)
        val powerUps = generatePowerUps(center, 0.0, 3, 42, 0)
        for (pu in powerUps) {
            assertEquals(center.latitude(), pu.location.latitude, 0.0001)
            assertEquals(center.longitude(), pu.location.longitude, 0.0001)
        }
    }

    // ── Cross-platform parity: deterministicDriftCenter ──

    /** Hardcoded values verified against iOS. If these break, parity is lost. */
    @Test
    fun `drift center cross-platform parity`() {
        val base = Point.fromLngLat(4.3528, 50.8466)
        data class DriftTest(val oldR: Double, val newR: Double, val seed: Int, val eLat: Double, val eLng: Double)
        val tests = listOf(
            DriftTest(1500.0, 1400.0, 42,     50.846975022854906, 4.352811404529953),
            DriftTest(1500.0, 1400.0, 12345,  50.846304286836016, 4.35242679292988),
            DriftTest(1500.0, 1400.0, 999999, 50.846981166449616, 4.353113589623775),
            DriftTest(1000.0,  900.0, 42,     50.84699857089946,  4.352990826261173),
            DriftTest(2000.0, 1800.0, 54321,  50.84676052483228,  4.352177942909372),
            DriftTest( 500.0,  400.0, 1,      50.846909360931114, 4.352834175494442),
        )
        for (t in tests) {
            val r = deterministicDriftCenter(base, t.oldR, t.newR, t.seed)
            assertEquals("drift(${t.oldR},${t.newR},${t.seed}) lat", t.eLat, r.latitude(), 1e-12)
            assertEquals("drift(${t.oldR},${t.newR},${t.seed}) lng", t.eLng, r.longitude(), 1e-12)
        }
    }

    // ── Cross-platform parity: interpolateZoneCenter ──

    @Test
    fun `interpolate center cross-platform parity`() {
        val initial = Point.fromLngLat(4.0, 50.0)
        val final_ = Point.fromLngLat(5.0, 51.0)
        data class InterpTest(val iR: Double, val cR: Double, val eLat: Double, val eLng: Double)
        val tests = listOf(
            InterpTest(1500.0, 1500.0, 50.0,                4.0),
            InterpTest(1500.0,  750.0, 50.5,                4.5),
            InterpTest(1500.0,    0.0, 51.0,                5.0),
            InterpTest(1500.0, 1000.0, 50.333333333333336,  4.333333333333333),
            InterpTest(1500.0,  500.0, 50.666666666666664,  4.666666666666667),
            InterpTest(1500.0,  100.0, 50.93333333333333,   4.933333333333334),
        )
        for (t in tests) {
            val r = interpolateZoneCenter(initial, final_, t.iR, t.cR)
            assertEquals("interp(${t.iR},${t.cR}) lat", t.eLat, r.latitude(), 1e-12)
            assertEquals("interp(${t.iR},${t.cR}) lng", t.eLng, r.longitude(), 1e-12)
        }
    }

    // ── Cross-platform parity: calculateNormalModeSettings ──

    @Test
    fun `normalMode settings cross-platform parity`() {
        data class NMTest(val radius: Double, val duration: Double, val expectedDecline: Double)
        val tests = listOf(
            NMTest(1500.0,  60.0, 116.66666666666667),
            NMTest(1500.0,  90.0,  77.77777777777777),
            NMTest(1500.0, 120.0,  58.333333333333336),
            NMTest(1500.0, 150.0,  46.666666666666664),
            NMTest(1500.0, 180.0,  38.888888888888886),
            NMTest( 500.0,  90.0,  22.22222222222222),
            NMTest(3000.0, 120.0, 120.83333333333333),
            NMTest( 100.0,  60.0,   0.0),
            NMTest( 101.0,  90.0,   0.05555555555555555),
            NMTest(50000.0,180.0,1386.111111111111),
        )
        for (t in tests) {
            val (interval, decline) = calculateNormalModeSettings(t.radius, t.duration)
            assertEquals(5.0, interval, 0.0)
            assertEquals("normalMode(${t.radius},${t.duration}) decline", t.expectedDecline, decline, 1e-10)
        }
    }

    // ── Cross-platform parity: generatePowerUps ──

    @Test
    fun `generatePowerUps cross-platform parity`() {
        val center = Point.fromLngLat(4.3528, 50.8466)
        val powerUps = generatePowerUps(center, 1500.0, 5, 42, 0)

        data class Expected(val id: String, val lat: Double, val lng: Double)
        val expected = listOf(
            Expected("pu-0-0-1302", 50.85190592354773, 4.347959996611229),
            Expected("pu-0-1-1429", 50.85139253538571, 4.358528048360003),
            Expected("pu-0-2-1556", 50.84476488107779, 4.362537022917026),
            Expected("pu-0-3-1683", 50.84061364403051, 4.353763055127964),
            Expected("pu-0-4-1810", 50.84376473675584, 4.344824231076483),
        )

        assertEquals(5, powerUps.size)
        for (i in expected.indices) {
            assertEquals("PU[$i] id", expected[i].id, powerUps[i].id)
            assertEquals("PU[$i] lat", expected[i].lat, powerUps[i].location.latitude, 1e-10)
            assertEquals("PU[$i] lng", expected[i].lng, powerUps[i].location.longitude, 1e-10)
        }
    }
}
