package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeSubmissionModelTest {

    @Test
    fun `default submission is pending oneShot`() {
        val sub = ChallengeSubmission()
        assertEquals(SubmissionStatus.PENDING, sub.statusEnum)
        assertEquals(ChallengeType.ONE_SHOT, sub.typeEnum)
        assertEquals("pending", sub.status)
        assertEquals("oneShot", sub.type)
        assertNull(sub.submittedAt)
        assertNull(sub.validatedBy)
        assertNull(sub.validatedAt)
    }

    @Test
    fun `statusEnum maps known raw values`() {
        assertEquals(SubmissionStatus.PENDING, ChallengeSubmission(status = "pending").statusEnum)
        assertEquals(SubmissionStatus.VALIDATED, ChallengeSubmission(status = "validated").statusEnum)
        assertEquals(SubmissionStatus.REJECTED, ChallengeSubmission(status = "rejected").statusEnum)
    }

    @Test
    fun `statusEnum falls back to PENDING for unknown raw value`() {
        assertEquals(SubmissionStatus.PENDING, ChallengeSubmission(status = "future-state").statusEnum)
    }

    @Test
    fun `statusEnum falls back to PENDING for null raw value`() {
        assertEquals(SubmissionStatus.PENDING, SubmissionStatus.fromFirestore(null))
    }

    @Test
    fun `typeEnum maps oneShot and repeatable`() {
        assertEquals(ChallengeType.ONE_SHOT, ChallengeSubmission(type = "oneShot").typeEnum)
        assertEquals(ChallengeType.REPEATABLE, ChallengeSubmission(type = "repeatable").typeEnum)
    }

    @Test
    fun `SubmissionStatus raw values match iOS`() {
        assertEquals("pending", SubmissionStatus.PENDING.firestoreValue)
        assertEquals("validated", SubmissionStatus.VALIDATED.firestoreValue)
        assertEquals("rejected", SubmissionStatus.REJECTED.firestoreValue)
        assertEquals(3, SubmissionStatus.values().size)
    }

    @Test
    fun `validated submission carries validator id and timestamp`() {
        val now = Timestamp.now()
        val sub = ChallengeSubmission(
            id = "sub-1",
            challengeId = "c-1",
            hunterId = "h-1",
            type = "oneShot",
            submittedAt = now,
            photoUrl = "gs://bucket/x.jpg",
            status = "validated",
            validatedBy = "gm-uid",
            validatedAt = now,
        )
        assertEquals(SubmissionStatus.VALIDATED, sub.statusEnum)
        assertEquals("gm-uid", sub.validatedBy)
        assertEquals(now, sub.validatedAt)
    }
}
