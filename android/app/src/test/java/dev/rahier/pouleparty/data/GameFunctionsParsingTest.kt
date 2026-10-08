package dev.rahier.pouleparty.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GameFunctionsParsingTest {

    @Test
    fun `found code success and every rejection reason are parsed`() {
        assertEquals(SubmitFoundCodeResult.Success, parseSubmitFoundCode(mapOf("success" to true)))
        val cases = mapOf(
            "invalidCode" to SubmitFoundCodeReason.InvalidCode,
            "notAHunter" to SubmitFoundCodeReason.NotAHunter,
            "alreadyWinner" to SubmitFoundCodeReason.AlreadyWinner,
            "gameNotInProgress" to SubmitFoundCodeReason.GameNotInProgress,
            "somethingNew" to SubmitFoundCodeReason.MalformedResponse,
        )
        cases.forEach { (wire, reason) ->
            assertEquals(SubmitFoundCodeResult.Failure(reason), parseSubmitFoundCode(mapOf("success" to false, "reason" to wire)))
        }
    }

    @Test
    fun `found code cooldown carries the server lock`() {
        val result = parseSubmitFoundCode(mapOf("success" to false, "reason" to "cooldown", "lockedUntil" to 1_700_000_000_000L))
        assertEquals(SubmitFoundCodeResult.Failure(SubmitFoundCodeReason.Cooldown, 1_700_000_000_000L), result)
    }

    @Test
    fun `a missing payload is a malformed response`() {
        assertEquals(SubmitFoundCodeResult.Failure(SubmitFoundCodeReason.MalformedResponse), parseSubmitFoundCode(null))
        assertEquals(ValidationCodeResult.ERROR, parseValidationCode(null))
    }

    @Test
    fun `registration code statuses are parsed`() {
        assertEquals(ValidationCodeResult.VALID, parseValidationCode(mapOf("status" to "valid")))
        assertEquals(ValidationCodeResult.INVALID, parseValidationCode(mapOf("status" to "invalid")))
        assertEquals(ValidationCodeResult.ALREADY_USED, parseValidationCode(mapOf("status" to "alreadyUsed")))
        assertEquals(ValidationCodeResult.ERROR, parseValidationCode(mapOf("status" to 3)))
    }

    @Test
    fun `game master join result reads attempts and lock`() {
        val failed = parseJoinAsGameMaster(mapOf("success" to false, "attemptsRemaining" to 2L, "lockedUntil" to 99L))
        assertFalse(failed.success)
        assertEquals(2, failed.attemptsRemaining)
        assertEquals(99L, failed.lockedUntilMs)
        val ok = parseJoinAsGameMaster(mapOf("success" to true, "attemptsRemaining" to 5))
        assertTrue(ok.success)
        assertNull(ok.lockedUntilMs)
    }

    @Test
    fun `callable errors map to their stable code`() {
        assertEquals(ApiErrorCode.POWER_UP_TOO_FAR, apiErrorCodeFor("powerUpTooFar", "FAILED_PRECONDITION"))
        assertEquals(ApiErrorCode.NETWORK, apiErrorCodeFor(null, "UNAVAILABLE"))
        assertEquals(ApiErrorCode.NETWORK, apiErrorCodeFor(null, "DEADLINE_EXCEEDED"))
        assertEquals(ApiErrorCode.TOO_MANY_ATTEMPTS, apiErrorCodeFor(null, "RESOURCE_EXHAUSTED"))
        assertEquals(ApiErrorCode.UNAUTHENTICATED, apiErrorCodeFor(null, "UNAUTHENTICATED"))
        assertEquals(ApiErrorCode.UNKNOWN, apiErrorCodeFor(null, "INTERNAL"))
    }

    @Test
    fun `transport errors map to network and the rest to unknown`() {
        assertEquals(ApiErrorCode.NETWORK, IOException("offline").toApiErrorCode())
        assertEquals(ApiErrorCode.UNKNOWN, IllegalStateException().toApiErrorCode())
        assertEquals(ApiErrorCode.GAME_FULL, ApiException(ApiErrorCode.GAME_FULL).toApiErrorCode())
    }

    @Test
    fun `unknown wire codes do not crash`() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromWire("brandNew"))
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromWire(null))
        ApiErrorCode.entries.forEach { assertEquals(it, ApiErrorCode.fromWire(it.wire)) }
    }
}
