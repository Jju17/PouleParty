package dev.rahier.pouleparty.ui

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
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
        assertEquals("Bob a trouvé la poule !", detectNewWinners(winners, previousCount = 1))
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
        assertEquals("Alice a trouvé la poule !", detectNewWinners(winners, previousCount = 0))
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

    @Test
    fun `shouldCheckZone chicken mutualTracking returns false`() {
        assertFalse(shouldCheckZone(PlayerRole.CHICKEN, GameMod.MUTUAL_TRACKING))
    }

    @Test
    fun `shouldCheckZone hunter mutualTracking returns true`() {
        assertTrue(shouldCheckZone(PlayerRole.HUNTER, GameMod.MUTUAL_TRACKING))
    }
}
