package dev.rahier.pouleparty.ui

import com.google.android.gms.maps.model.LatLng
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
            initialLocation = LatLng(50.0, 4.0),
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
            initialLocation = LatLng(50.0, 4.0),
            currentCircleCenter = LatLng(50.0, 4.0)
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
            initialLocation = LatLng(50.0, 4.0),
            currentCircleCenter = LatLng(50.0, 4.0)
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
            initialLocation = LatLng(50.0, 4.0),
            currentCircleCenter = null
        )
        assertNotNull(result)
        assertTrue(result!!.isGameOver)
        assertEquals("The zone has collapsed!", result.gameOverMessage)
    }

    @Test
    fun `radius update uses initial location for stayInTheZone`() {
        val initialLocation = LatLng(50.0, 4.0)
        val currentCenter = LatLng(51.0, 5.0)
        val result = processRadiusUpdate(
            nextRadiusUpdate = Date(System.currentTimeMillis() - 1000),
            currentRadius = 1500,
            radiusDeclinePerUpdate = 100.0,
            radiusIntervalUpdate = 5.0,
            gameMod = GameMod.STAY_IN_THE_ZONE,
            initialLocation = initialLocation,
            currentCircleCenter = currentCenter
        )
        assertNotNull(result)
        assertEquals(initialLocation, result!!.newCircleCenter)
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
}
