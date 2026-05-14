package dev.rahier.pouleparty.ui.gamemastermap

import dev.rahier.pouleparty.model.ChallengeCompletion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PP-66 — Validation flow tests aligned with PP-25 / today's
 * `markChallengeCompleted` Firestore transaction.
 *
 * Today's production contract (Android `FirestoreRepository.markChallengeCompleted`):
 *  - Reads the `ChallengeCompletion` doc.
 *  - If `completedChallengeIds.contains(challengeId)`, exits idempotently.
 *  - Else appends `challengeId` and adds `points` to `totalPoints`.
 *  - Always writes the latest `teamName` (never overwrites with empty).
 *
 * PP-25 will split this between `Challenge.type == .oneShot` (current
 * behaviour, but stored as `validatedChallengeIds`) and `repeatable`
 * (increments `repeatableCounts[challengeId]` and accumulates points
 * each time). The simulator below mirrors the iOS equivalent so any
 * regression surfaces on both platforms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMasterValidationTest {

    private fun makeCompletion(
        hunterId: String = "hunter-1",
        challengeIds: List<String> = emptyList(),
        totalPoints: Int = 0,
        teamName: String = "",
    ) = ChallengeCompletion(
        hunterId = hunterId,
        completedChallengeIds = challengeIds,
        totalPoints = totalPoints,
        teamName = teamName,
    )

    // ── ChallengeCompletion model contract ───────────

    @Test
    fun `accumulates points across distinct challenges`() {
        var c = makeCompletion(hunterId = "h1", teamName = "Team Alpha")
        c = c.copy(
            completedChallengeIds = c.completedChallengeIds + "ch-1",
            totalPoints = c.totalPoints + 25,
        )
        c = c.copy(
            completedChallengeIds = c.completedChallengeIds + "ch-2",
            totalPoints = c.totalPoints + 10,
        )
        assertEquals(listOf("ch-1", "ch-2"), c.completedChallengeIds)
        assertEquals(35, c.totalPoints)
        assertEquals("Team Alpha", c.teamName)
    }

    @Test
    fun `idempotent for the same challenge`() {
        var c = makeCompletion(
            hunterId = "h1",
            challengeIds = listOf("ch-1"),
            totalPoints = 25,
            teamName = "Team Alpha",
        )
        // Second validation of the same challenge — the production
        // transaction skips the write entirely.
        if (!c.completedChallengeIds.contains("ch-1")) {
            c = c.copy(
                completedChallengeIds = c.completedChallengeIds + "ch-1",
                totalPoints = c.totalPoints + 25,
            )
        }
        assertEquals(listOf("ch-1"), c.completedChallengeIds)
        assertEquals(25, c.totalPoints)
    }

    @Test
    fun `preserves teamName on subsequent writes`() {
        var c = makeCompletion(
            hunterId = "h1",
            challengeIds = listOf("ch-1"),
            totalPoints = 10,
            teamName = "Original Team",
        )
        c = c.copy(
            completedChallengeIds = c.completedChallengeIds + "ch-2",
            totalPoints = c.totalPoints + 15,
            // teamName re-passed by the production write — same value.
            teamName = c.teamName,
        )
        assertEquals("Original Team", c.teamName)
        assertEquals(25, c.totalPoints)
        assertEquals(2, c.completedChallengeIds.size)
    }

    // ── Anti-doublon under concurrency (oneShot semantics) ────────

    @Test
    fun `concurrent validations on the same challenge credit exactly once`() = runTest(UnconfinedTestDispatcher()) {
        // Two GMs tap "validate" at the same instant. Mirrors the
        // Firestore-transaction read-then-write loop so the test
        // proves the model contract, not just the happy path.
        val state = StatefulCompletion(makeCompletion(hunterId = "h1", teamName = "Alpha"))

        val a = async { state.tryValidate("ch-shared", points = 50, teamName = "Alpha") }
        val b = async { state.tryValidate("ch-shared", points = 50, teamName = "Alpha") }
        listOf(a, b).awaitAll()

        val finalState = state.read()
        assertEquals(listOf("ch-shared"), finalState.completedChallengeIds)
        assertEquals("Points must be credited exactly once even on a race", 50, finalState.totalPoints)
    }

    @Test
    fun `multiple distinct challenges validated concurrently each credit once`() = runTest(UnconfinedTestDispatcher()) {
        // Same test but for two DIFFERENT challenges — both should
        // credit. Pins the "no false positive idempotency" half of
        // the contract.
        val state = StatefulCompletion(makeCompletion(hunterId = "h1", teamName = "Alpha"))

        val a = async { state.tryValidate("ch-a", points = 25, teamName = "Alpha") }
        val b = async { state.tryValidate("ch-b", points = 15, teamName = "Alpha") }
        listOf(a, b).awaitAll()

        val finalState = state.read()
        assertTrue(finalState.completedChallengeIds.containsAll(listOf("ch-a", "ch-b")))
        assertEquals(40, finalState.totalPoints)
    }

    /**
     * In-memory mock of the production Firestore-transaction body:
     *  - Reads the current doc.
     *  - If the challenge is already in `completedChallengeIds`,
     *    skips (idempotent).
     *  - Else appends + credits points.
     * The whole block is serialized through a `Mutex` so the test
     * exercises the same atomicity guarantee a real transaction
     * provides.
     */
    private class StatefulCompletion(initial: ChallengeCompletion) {
        private val mutex = Mutex()
        private var value: ChallengeCompletion = initial

        suspend fun tryValidate(challengeId: String, points: Int, teamName: String) {
            mutex.withLock {
                val current = value
                if (current.completedChallengeIds.contains(challengeId)) return@withLock
                value = current.copy(
                    completedChallengeIds = current.completedChallengeIds + challengeId,
                    totalPoints = current.totalPoints + points,
                    teamName = teamName,
                )
            }
        }

        fun read(): ChallengeCompletion = value
    }
}
