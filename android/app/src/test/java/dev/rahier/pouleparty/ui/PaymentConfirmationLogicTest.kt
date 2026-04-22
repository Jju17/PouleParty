package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.paymentconfirmation.ConfirmationKind
import dev.rahier.pouleparty.ui.paymentconfirmation.PaymentConfirmationLogic
import dev.rahier.pouleparty.ui.paymentconfirmation.PaymentConfirmationLogic.formatCountdown
import dev.rahier.pouleparty.ui.paymentconfirmation.PaymentConfirmationLogic.secondsRemainingUntil
import dev.rahier.pouleparty.ui.paymentconfirmation.PaymentConfirmationUiState
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Exhaustive unit coverage for the pure formatting / state rules of the
 * payment-confirmation screen. No Android framework needed, no coroutines.
 * Focus is explicitly on edge cases that caused past-me pain on similar
 * countdown screens: hour boundaries, negative deltas (clock skew), >30 day
 * values, zero values, and kind-arg parsing with junk input.
 */
class PaymentConfirmationLogicTest {

    // ── formatCountdown ──────────────────────────────────────────────────────

    @Test
    fun `formatCountdown zero returns dash`() {
        assertEquals("—", formatCountdown(0))
    }

    @Test
    fun `formatCountdown 1 second shows m colon ss`() {
        assertEquals("0:01", formatCountdown(1))
    }

    @Test
    fun `formatCountdown 59 seconds shows 0 colon 59`() {
        assertEquals("0:59", formatCountdown(59))
    }

    @Test
    fun `formatCountdown exactly 1 minute`() {
        assertEquals("1:00", formatCountdown(60))
    }

    @Test
    fun `formatCountdown 59 minutes 59 seconds still in m colon ss form`() {
        assertEquals("59:59", formatCountdown(59 * 60 + 59))
    }

    @Test
    fun `formatCountdown exactly 1 hour crosses into h colon mm colon ss form`() {
        assertEquals("1:00:00", formatCountdown(3600))
    }

    @Test
    fun `formatCountdown 1 hour 1 minute 1 second pads minutes and seconds`() {
        assertEquals("1:01:01", formatCountdown(3600 + 60 + 1))
    }

    @Test
    fun `formatCountdown 23h 59m 59s just under a day`() {
        assertEquals("23:59:59", formatCountdown(86_399))
    }

    @Test
    fun `formatCountdown exactly 1 day crosses into Dj form`() {
        assertEquals("1j 00:00:00", formatCountdown(86_400))
    }

    @Test
    fun `formatCountdown 1 day 1 hour 1 minute 1 second`() {
        assertEquals("1j 01:01:01", formatCountdown(86_400 + 3600 + 60 + 1))
    }

    @Test
    fun `formatCountdown 7 days and some`() {
        assertEquals("7j 12:34:56", formatCountdown(7 * 86_400 + 12 * 3600 + 34 * 60 + 56))
    }

    @Test
    fun `formatCountdown 365 days is fine`() {
        // Year-long countdown — matches a creator scheduling for the next year.
        // No overflow, no weird formatting.
        val seconds = 365L * 86_400
        val result = formatCountdown(seconds)
        assertTrue("Expected 365-day format, got $result", result.startsWith("365j "))
        assertTrue(result.endsWith(" 00:00:00"))
    }

    @Test
    fun `formatCountdown 10_000 days does not crash`() {
        // Paranoid: some bad state could feed a huge value. Don't crash.
        val seconds = 10_000L * 86_400
        val result = formatCountdown(seconds)
        assertTrue(result.contains("10000j"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatCountdown rejects negative input`() {
        formatCountdown(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `formatCountdown rejects large negative input`() {
        formatCountdown(-100_000)
    }

    // ── secondsRemainingUntil ────────────────────────────────────────────────

    @Test
    fun `secondsRemainingUntil future gives positive seconds`() {
        val now = 1_700_000_000_000L
        val start = now + 90_000L  // 90 seconds in the future
        assertEquals(90L, secondsRemainingUntil(start, now))
    }

    @Test
    fun `secondsRemainingUntil exactly now returns zero`() {
        val now = 1_700_000_000_000L
        assertEquals(0L, secondsRemainingUntil(now, now))
    }

    @Test
    fun `secondsRemainingUntil past returns zero (clock skew guard)`() {
        // User lingers on the confirmation screen past game-start, OR the
        // device clock drifted forward past the server-set start. We show
        // zero rather than a negative countdown.
        val now = 1_700_000_000_000L
        val start = now - 5_000L
        assertEquals(0L, secondsRemainingUntil(start, now))
    }

    @Test
    fun `secondsRemainingUntil far past returns zero`() {
        // User left the app open overnight — start was 12 h ago.
        val now = 1_700_000_000_000L
        val start = now - 12L * 3_600_000L
        assertEquals(0L, secondsRemainingUntil(start, now))
    }

    @Test
    fun `secondsRemainingUntil truncates sub-second remainder`() {
        // 999 ms remaining → 0 full seconds, not 1. Avoids flicker at the boundary.
        val now = 1_700_000_000_000L
        val start = now + 999L
        assertEquals(0L, secondsRemainingUntil(start, now))
    }

    @Test
    fun `secondsRemainingUntil 1500 ms gives 1 second`() {
        val now = 1_700_000_000_000L
        val start = now + 1_500L
        assertEquals(1L, secondsRemainingUntil(start, now))
    }

    @Test
    fun `secondsRemainingUntil handles very large future deltas`() {
        val now = 1_700_000_000_000L
        val start = now + 30L * 86_400_000L // 30 days
        assertEquals(30L * 86_400L, secondsRemainingUntil(start, now))
    }

    @Test
    fun `secondsRemainingUntil with zero startMs returns zero (garbage start)`() {
        // A completely unset / corrupted startMs (e.g. Firestore Timestamp
        // default) should not produce a negative countdown.
        assertEquals(0L, secondsRemainingUntil(0, 1_700_000_000_000L))
    }

    // ── ConfirmationKind.fromArg ─────────────────────────────────────────────

    @Test
    fun `ConfirmationKind fromArg creator_forfait`() {
        assertEquals(ConfirmationKind.CREATOR_FORFAIT, ConfirmationKind.fromArg("creator_forfait"))
    }

    @Test
    fun `ConfirmationKind fromArg hunter_caution`() {
        assertEquals(ConfirmationKind.HUNTER_CAUTION, ConfirmationKind.fromArg("hunter_caution"))
    }

    @Test
    fun `ConfirmationKind fromArg unknown defaults to creator`() {
        assertEquals(ConfirmationKind.CREATOR_FORFAIT, ConfirmationKind.fromArg("whatever"))
    }

    @Test
    fun `ConfirmationKind fromArg empty string defaults to creator`() {
        assertEquals(ConfirmationKind.CREATOR_FORFAIT, ConfirmationKind.fromArg(""))
    }

    @Test
    fun `ConfirmationKind fromArg mixed case does not match`() {
        // We compare strings literally, so "Creator_Forfait" is NOT creator.
        // This is intentional — routes are code-generated lowercase, and
        // accepting casing drift would hide a real mismatch bug.
        assertEquals(ConfirmationKind.CREATOR_FORFAIT, ConfirmationKind.fromArg("Creator_Forfait"))
        // (Falls back to default, which *is* CREATOR_FORFAIT — so this asserts
        //  that mixed case doesn't land on HUNTER_CAUTION either.)
        assertEquals(ConfirmationKind.CREATOR_FORFAIT, ConfirmationKind.fromArg("HUNTER_CAUTION"))
    }

    @Test
    fun `ConfirmationKind fromArg with leading space does not match`() {
        // We don't trim — a route arg with leading space is a routing bug.
        assertEquals(ConfirmationKind.CREATOR_FORFAIT, ConfirmationKind.fromArg(" creator_forfait"))
    }

    // ── PaymentConfirmationUiState invariants ────────────────────────────────

    @Test
    fun `initial UI state has null game`() {
        val state = PaymentConfirmationUiState()
        assertNull(state.game)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `stream emitting game overwrites prior snapshot`() {
        val old = Game(id = "g1", name = "Old")
        val new = Game(id = "g1", name = "New")
        var state = PaymentConfirmationUiState(game = old)
        state = state.copy(game = new)
        assertEquals("New", state.game?.name)
    }

    @Test
    fun `pending payment transition to waiting is reflected`() {
        // Initial snapshot is pending_payment (pre-webhook). Stream then
        // delivers waiting. UI logic reads gameStatusEnum on each render.
        val pending = Game(id = "g1", status = GameStatus.PENDING_PAYMENT.firestoreValue)
        val waiting = pending.copy(status = GameStatus.WAITING.firestoreValue)

        val initial = PaymentConfirmationUiState(game = pending)
        assertEquals(GameStatus.PENDING_PAYMENT, initial.game?.gameStatusEnum)

        val updated = initial.copy(game = waiting)
        assertEquals(GameStatus.WAITING, updated.game?.gameStatusEnum)
    }

    @Test
    fun `game cancelled mid view is reflected`() {
        // Creator cancels the game from another device while the user is
        // staring at the confirmation screen. Stream delivers status = done.
        val initial = Game(id = "g1", status = GameStatus.WAITING.firestoreValue)
        val cancelled = initial.copy(status = GameStatus.DONE.firestoreValue)

        var state = PaymentConfirmationUiState(game = initial)
        state = state.copy(game = cancelled)
        assertEquals(GameStatus.DONE, state.game?.gameStatusEnum)
    }

    @Test
    fun `payment failed transition is reflected (webhook rejected the charge)`() {
        val pending = Game(id = "g1", status = GameStatus.PENDING_PAYMENT.firestoreValue)
        val failed = pending.copy(status = GameStatus.PAYMENT_FAILED.firestoreValue)

        var state = PaymentConfirmationUiState(game = pending)
        state = state.copy(game = failed)
        assertEquals(GameStatus.PAYMENT_FAILED, state.game?.gameStatusEnum)
    }

    @Test
    fun `loadFailed defaults false and can be toggled when getConfig returns null`() {
        var state = PaymentConfirmationUiState()
        assertFalse(state.loadFailed)
        state = state.copy(loadFailed = true)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `loadFailed is cleared when stream eventually emits a game`() {
        // Simulates the real sequence: getConfig fails (loadFailed=true),
        // then gameConfigFlow catches up and emits a game (loadFailed=false).
        var state = PaymentConfirmationUiState(loadFailed = true)
        state = state.copy(game = Game(id = "g1"), loadFailed = false)
        assertFalse(state.loadFailed)
        assertNotNull(state.game)
    }

    @Test
    fun `nowMs can be updated without clobbering game`() {
        val game = Game(id = "g1", name = "Stable")
        var state = PaymentConfirmationUiState(game = game, nowMs = 100L)
        state = state.copy(nowMs = 200L)
        assertEquals(200L, state.nowMs)
        assertEquals("Stable", state.game?.name)
    }
}
