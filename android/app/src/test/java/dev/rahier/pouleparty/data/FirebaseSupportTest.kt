package dev.rahier.pouleparty.data

import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.ChallengeType
import dev.rahier.pouleparty.model.SubmissionStatus
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FirebaseSupportTest {

    @Test
    fun `resubscribe delay doubles and is capped`() {
        assertEquals(1_000L, resubscribeDelayMs(0, 30_000L))
        assertEquals(2_000L, resubscribeDelayMs(1, 30_000L))
        assertEquals(16_000L, resubscribeDelayMs(4, 30_000L))
        assertEquals(30_000L, resubscribeDelayMs(5, 30_000L))
        assertEquals(5_000L, resubscribeDelayMs(50, 5_000L))
    }

    @Test
    fun `a failing stream is resubscribed and keeps emitting`() = runTest {
        var subscriptions = 0
        val source = flow {
            subscriptions++
            emit(subscriptions)
            if (subscriptions < 3) error("listener cancelled")
            emit(99)
        }
        val values = source.resubscribeOnError().take(4).toList()
        assertEquals(listOf(1, 2, 3, 99), values)
        assertEquals(3, subscriptions)
    }

    @Test
    fun `backoff restarts after a successful value`() = runTest {
        var subscriptions = 0
        val source = flow {
            subscriptions++
            emit(subscriptions)
            error("drop")
        }
        source.resubscribeOnError().take(3).toList()
        assertEquals(2_000L, currentTime)
    }

    private fun submission(status: SubmissionStatus) = ChallengeSubmission(status = status.firestoreValue)

    @Test
    fun `a pending proof blocks a new submission`() {
        assertEquals(
            SubmissionRejectedException.Reason.ALREADY_PENDING,
            blockingSubmission(listOf(submission(SubmissionStatus.PENDING)), ChallengeType.REPEATABLE),
        )
    }

    @Test
    fun `a validated one-shot cannot be resubmitted but a repeatable can`() {
        val validated = listOf(submission(SubmissionStatus.VALIDATED))
        assertEquals(SubmissionRejectedException.Reason.ALREADY_VALIDATED, blockingSubmission(validated, ChallengeType.ONE_SHOT))
        assertNull(blockingSubmission(validated, ChallengeType.REPEATABLE))
        assertNull(blockingSubmission(emptyList(), ChallengeType.ONE_SHOT))
    }
}
