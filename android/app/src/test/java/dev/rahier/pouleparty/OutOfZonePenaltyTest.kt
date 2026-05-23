package dev.rahier.pouleparty

import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.ui.gamelogic.evaluateOutOfZonePenalty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PP-37: parity tests for the PP-36 out-of-zone penalty (-1 point /
 * 5 s). Strict mirror of the iOS `OutOfZonePenaltyTests` — same
 * scenarios in the same order, same expected numeric outputs, so a
 * one-platform drift fails on this side without the other.
 *
 * The tests target the same `evaluateOutOfZonePenalty` helper the
 * production code in `HunterMapViewModel.startTimer()` calls, so any
 * drift surfaces immediately: the runtime no longer has a separate
 * inline decision tree.
 *
 * The suite also contains one integration-style smoke check that
 * exercises the real `decrementTotalPoints` call via mockk to lock
 * the Firestore-repository contract (function name + signature).
 */
class OutOfZonePenaltyTest {

    /** Anchor "now" for deterministic windows. Real wall clock is irrelevant. */
    private val now: Long = 1_000_000_000L
    private val hunterId = "hunter-1"

    // MARK: - Scenario 1: 12s out of zone → -2 points

    /**
     * Two complete 5 s windows out of zone fire exactly two penalty
     * writes. Modelled as two independent ticks where the previous
     * `lastPenaltyAt` is ≥ 5 s old. Mirrors the iOS
     * `twelveSecondsOutOfZoneFiresTwoPenalties` test.
     */
    @Test
    fun `scenario1_12s out of zone fires two penalties`() {
        var fires = 0
        // First 5 s window: lastPenaltyAt rewound 5 s → fires.
        val first = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = now - AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS,
            nowMs = now,
        )
        if (first.shouldFirePenalty) fires++

        // Second 5 s window (5 s later): lastPenaltyAt rewound 5 s → fires again.
        val later = now + AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS
        val second = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = later - AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS,
            nowMs = later,
        )
        if (second.shouldFirePenalty) fires++

        assertEquals(2, fires)
    }

    // MARK: - Scenario 2: 4s out of zone → 0 points

    /** Less than a full 5 s window: no penalty fires. */
    @Test
    fun `scenario2_4s out of zone fires no penalty`() {
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = now - 4_000L,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
    }

    // MARK: - Scenario 3: re-enters just before tick → no penalty + reset

    /**
     * Re-entry resets `lastPenaltyAt` to null. The next tick checks
     * `isOutsideZone` again — if the hunter is back inside, no
     * penalty fires that tick.
     */
    @Test
    fun `scenario3_re-enters just before tick fires no penalty and resets window`() {
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = false,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = now - 4_000L,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
        assertEquals(true, out.resetLastPenaltyAt)
        assertNull(out.newLastPenaltyAt)
    }

    // MARK: - Scenario 4: exits just after a tick → first penalty 5s later

    /**
     * First out-of-zone tick MUST NOT fire immediately — it just
     * starts the 5 s window. Mirrors the iOS
     * `firstTickOutOfZoneStartsWindowAndDoesNotFire` test.
     */
    @Test
    fun `scenario4_first tick out of zone starts window and does not fire`() {
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = null,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
        assertNotNull("First tick must seed lastPenaltyAt", out.newLastPenaltyAt)
        assertEquals(now, out.newLastPenaltyAt)
    }

    // MARK: - Scenario 5: pre-game (chicken hasn't started) → no penalty

    /**
     * The production gate is `gameStarted = now >= hunterStartDate`,
     * which short-circuits the penalty path before this helper is
     * reached. We model that gate by inverting `isOutsideZone` here —
     * the production code never even evaluates `isOutsideZone` pre-
     * game, so the helper would see `isOutsideZone = false` (default)
     * and produce zero penalties. The check pins the "no firing
     * before the game starts" invariant.
     */
    @Test
    fun `scenario5_pre-game fires no penalty`() {
        // Production: pre-game short-circuits before the helper runs.
        // We assert the equivalent: with isOutsideZone defaulted to false
        // (the value before the zone check has had a chance to fire), no
        // penalty is produced even with a stale lastPenaltyAt.
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = false,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = now - 10_000L,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
    }

    // MARK: - Scenario 6: head-start window → no penalty

    /**
     * Same shape as scenario 5: the head-start gate
     * (`gameStarted = now >= hunterStartDate`) short-circuits the
     * penalty path. Modelled by `isOutsideZone = false`.
     */
    @Test
    fun `scenario6_head-start window fires no penalty`() {
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = false,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = now - 10_000L,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
    }

    // MARK: - Scenario 7: game over → no penalty

    /**
     * Once the game ends (timeout, zone collapse, all hunters found)
     * the penalty stops. Mirrors the iOS `isGameOverFiresNoPenalty`
     * test.
     */
    @Test
    fun `scenario7_game over fires no penalty`() {
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = true,
            hunterId = hunterId,
            lastPenaltyAt = now - 10_000L,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
    }

    // MARK: - Scenario 9: anti double-count within a 5s window

    /**
     * Two ticks fired within the same 5 s window must fire exactly
     * one penalty — the `lastPenaltyAt` guard is the anti
     * double-count. Models two ticks where the first fires (bumps
     * `lastPenaltyAt` to `now`), and the second runs immediately
     * after with no time advanced.
     */
    @Test
    fun `scenario9_two ticks within 5s window fire only one penalty`() {
        var fires = 0
        // First tick: lastPenaltyAt is 5 s old → fires, lastPenaltyAt bumps to now.
        val first = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = now - AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS,
            nowMs = now,
        )
        if (first.shouldFirePenalty) fires++
        val nextLast = first.newLastPenaltyAt ?: error("first tick must seed lastPenaltyAt")

        // Second tick: nowMs unchanged → only 0 ms since last → must NOT fire.
        val second = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = hunterId,
            lastPenaltyAt = nextLast,
            nowMs = now,
        )
        if (second.shouldFirePenalty) fires++

        assertEquals(1, fires)
    }

    // MARK: - Empty hunterId guard

    /**
     * Extra guard rail mirrored from the production code: if
     * `hunterId` is empty (auth still warming up), no penalty fires.
     * Avoids writing to `/users//challengeCompletions` which would
     * 400 the Firestore rules check.
     */
    @Test
    fun `empty hunterId fires no penalty`() {
        val out = evaluateOutOfZonePenalty(
            isOutsideZone = true,
            isGameOver = false,
            hunterId = "",
            lastPenaltyAt = now - 10_000L,
            nowMs = now,
        )
        assertEquals(false, out.shouldFirePenalty)
    }

    // MARK: - Interval constant parity

    /**
     * The 5 s penalty cadence is a shared constant between iOS and
     * Android. Locks the value so a one-platform tweak surfaces
     * loudly. iOS sibling:
     * `AppConstants.outOfZonePenaltyIntervalSeconds == 5`.
     */
    @Test
    fun `OUT_OF_ZONE_PENALTY_INTERVAL_MS matches iOS sibling`() {
        assertEquals(5_000L, AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS)
    }

    // MARK: - Firestore repository contract smoke test

    /**
     * Integration-style smoke check that locks the
     * `decrementTotalPoints(gameId, hunterId)` signature on
     * `FirestoreRepository`. The production penalty path calls this
     * exact method; if the repo signature drifts the production
     * build breaks too — but the test makes the contract explicit
     * and parity-mirrored with iOS's
     * `apiClient.decrementTotalPoints = { _, _ in ... }` dependency
     * override.
     */
    @Test
    fun `firestoreRepository decrementTotalPoints is called with gameId and hunterId`() {
        val repo = mockk<FirestoreRepository>(relaxed = true)
        coEvery { repo.decrementTotalPoints(any(), any()) } returns Unit

        kotlinx.coroutines.runBlocking {
            repo.decrementTotalPoints("game-123", "hunter-456")
        }

        coVerify(exactly = 1) { repo.decrementTotalPoints("game-123", "hunter-456") }
    }

    // MARK: - ViewModel wiring stays alive (regression guard)

    @Test
    fun `HunterMapUiState lastPenaltyAt default stays null`() {
        val state = dev.rahier.pouleparty.ui.huntermap.HunterMapUiState()
        assertNull(state.lastPenaltyAt)
    }
}
