package dev.rahier.pouleparty.util

import dev.rahier.pouleparty.model.SubmissionMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofMediaTest {

    @Test
    fun `large photos are scaled to the maximum side and small ones kept`() {
        assertEquals(900 to 675, scaledSize(4000, 3000, 900))
        assertEquals(675 to 900, scaledSize(3000, 4000, 900))
        assertEquals(800 to 600, scaledSize(800, 600, 900))
        assertEquals(0 to 0, scaledSize(0, 0, 900))
    }

    @Test
    fun `only videos above the ceiling are refused`() {
        assertTrue(isProofTooLarge(MAX_VIDEO_PROOF_BYTES + 1, SubmissionMediaType.VIDEO))
        assertFalse(isProofTooLarge(MAX_VIDEO_PROOF_BYTES, SubmissionMediaType.VIDEO))
        assertFalse(isProofTooLarge(MAX_VIDEO_PROOF_BYTES * 2, SubmissionMediaType.IMAGE))
    }
}
