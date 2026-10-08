package dev.rahier.pouleparty.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

sealed class SubmitFoundCodeResult {
    data object Success : SubmitFoundCodeResult()
    data class Failure(val reason: SubmitFoundCodeReason, val lockedUntilMs: Long? = null) : SubmitFoundCodeResult()
}

enum class SubmitFoundCodeReason { InvalidCode, Cooldown, NotAHunter, AlreadyWinner, GameNotInProgress, MalformedResponse }

data class JoinAsGameMasterResult(
    val success: Boolean,
    val attemptsRemaining: Int,
    val lockedUntilMs: Long?,
)

enum class ValidationCodeResult { VALID, INVALID, ALREADY_USED, ERROR }

enum class DebugAction(val wire: String) { ADVANCE_STEP("advanceStep"), END_NOW("endNow") }

/**
 * Every server-authoritative action. Failures surface as [ApiException] so
 * the UI translates a stable code instead of showing a server message.
 */
interface GameFunctions {
    suspend fun submitFoundCode(gameId: String, foundCode: String, hunterName: String): SubmitFoundCodeResult
    suspend fun getFoundCode(gameId: String): String
    suspend fun joinGame(gameId: String, teamName: String)
    suspend fun leaveGame(gameId: String)
    suspend fun collectPowerUp(gameId: String, powerUpId: String, latitude: Double, longitude: Double)
    suspend fun activatePowerUp(gameId: String, powerUpId: String)
    suspend fun applyOutOfZonePenalty(gameId: String)
    suspend fun validateChallengeSubmission(gameId: String, submissionId: String, accept: Boolean)
    suspend fun validateRegistrationCode(batchId: String, code: String): ValidationCodeResult
    suspend fun setGameMasterPassword(gameId: String, password: String)
    suspend fun designateChicken(gameId: String, newChickenUid: String)
    suspend fun joinAsGameMaster(gameId: String, password: String): JoinAsGameMasterResult
    suspend fun launchGame(gameId: String)
    suspend fun debugAdvanceGame(gameId: String, action: DebugAction)
}

internal fun parseSubmitFoundCode(raw: Map<*, *>?): SubmitFoundCodeResult {
    raw ?: return SubmitFoundCodeResult.Failure(SubmitFoundCodeReason.MalformedResponse)
    if (raw["success"] == true) return SubmitFoundCodeResult.Success
    val reason = when (raw["reason"] as? String) {
        "invalidCode" -> SubmitFoundCodeReason.InvalidCode
        "cooldown" -> SubmitFoundCodeReason.Cooldown
        "notAHunter" -> SubmitFoundCodeReason.NotAHunter
        "alreadyWinner" -> SubmitFoundCodeReason.AlreadyWinner
        "gameNotInProgress" -> SubmitFoundCodeReason.GameNotInProgress
        else -> SubmitFoundCodeReason.MalformedResponse
    }
    return SubmitFoundCodeResult.Failure(reason, (raw["lockedUntil"] as? Number)?.toLong())
}

internal fun parseValidationCode(raw: Map<*, *>?): ValidationCodeResult = when (raw?.get("status") as? String) {
    "valid" -> ValidationCodeResult.VALID
    "alreadyUsed" -> ValidationCodeResult.ALREADY_USED
    "invalid" -> ValidationCodeResult.INVALID
    else -> ValidationCodeResult.ERROR
}

internal fun parseJoinAsGameMaster(raw: Map<*, *>?): JoinAsGameMasterResult = JoinAsGameMasterResult(
    success = raw?.get("success") == true,
    attemptsRemaining = (raw?.get("attemptsRemaining") as? Number)?.toInt() ?: 0,
    lockedUntilMs = (raw?.get("lockedUntil") as? Number)?.toLong(),
)

@Singleton
class FirebaseGameFunctions @Inject constructor(
    private val functions: FirebaseFunctions,
) : GameFunctions {

    private suspend fun call(name: String, payload: Map<String, Any?>): Map<*, *>? = try {
        withTimeout(READ_TIMEOUT_MS) { functions.getHttpsCallable(name).call(payload).await() }.getData() as? Map<*, *>
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ApiException(e.toApiErrorCode(), e)
    }

    override suspend fun submitFoundCode(gameId: String, foundCode: String, hunterName: String): SubmitFoundCodeResult = try {
        parseSubmitFoundCode(call("submitFoundCode", mapOf("gameId" to gameId, "foundCode" to foundCode, "hunterName" to hunterName)))
    } catch (e: ApiException) {
        val lockedUntil = e.cause?.lockedUntilMs()
        if (e.code == ApiErrorCode.TOO_MANY_ATTEMPTS) SubmitFoundCodeResult.Failure(SubmitFoundCodeReason.Cooldown, lockedUntil) else throw e
    }

    override suspend fun getFoundCode(gameId: String): String =
        (call("getFoundCode", mapOf("gameId" to gameId))?.get("foundCode") as? String).orEmpty()

    override suspend fun joinGame(gameId: String, teamName: String) {
        call("joinGame", mapOf("gameId" to gameId, "teamName" to teamName))
    }

    override suspend fun leaveGame(gameId: String) {
        call("leaveGame", mapOf("gameId" to gameId))
    }

    override suspend fun collectPowerUp(gameId: String, powerUpId: String, latitude: Double, longitude: Double) {
        call(
            "collectPowerUp",
            mapOf("gameId" to gameId, "powerUpId" to powerUpId, "lat" to latitude, "lng" to longitude),
        )
    }

    override suspend fun activatePowerUp(gameId: String, powerUpId: String) {
        call("activatePowerUp", mapOf("gameId" to gameId, "powerUpId" to powerUpId))
    }

    override suspend fun applyOutOfZonePenalty(gameId: String) {
        call("applyOutOfZonePenalty", mapOf("gameId" to gameId))
    }

    override suspend fun validateChallengeSubmission(gameId: String, submissionId: String, accept: Boolean) {
        call("validateChallengeSubmission", mapOf("gameId" to gameId, "submissionId" to submissionId, "accept" to accept))
    }

    override suspend fun validateRegistrationCode(batchId: String, code: String): ValidationCodeResult =
        parseValidationCode(call("validateRegistrationCode", mapOf("batchId" to batchId, "code" to code)))

    override suspend fun setGameMasterPassword(gameId: String, password: String) {
        call("setGameMasterPassword", mapOf("gameId" to gameId, "password" to password))
    }

    override suspend fun designateChicken(gameId: String, newChickenUid: String) {
        call("designateChicken", mapOf("gameId" to gameId, "newChickenUid" to newChickenUid))
    }

    override suspend fun joinAsGameMaster(gameId: String, password: String): JoinAsGameMasterResult =
        parseJoinAsGameMaster(call("joinAsGameMaster", mapOf("gameId" to gameId, "password" to password)))

    override suspend fun launchGame(gameId: String) {
        call("launchGame", mapOf("gameId" to gameId))
    }

    override suspend fun debugAdvanceGame(gameId: String, action: DebugAction) {
        call("debugAdvanceGame", mapOf("gameId" to gameId, "action" to action.wire))
    }
}
