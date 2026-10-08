package dev.rahier.pouleparty.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

internal const val DATA_TAG = "Data"

/** Single reads give up instead of freezing the UI on a dead connection. */
internal const val READ_TIMEOUT_MS = 15_000L

private const val MAX_RETRIES = 3
private const val INITIAL_DELAY_MS = 500L

internal suspend fun <T> withRetry(operation: String, block: suspend () -> T): T {
    var lastException: Exception? = null
    repeat(MAX_RETRIES) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            Log.w(DATA_TAG, "$operation failed (attempt ${attempt + 1}/$MAX_RETRIES)", e)
            if (attempt < MAX_RETRIES - 1) delay(INITIAL_DELAY_MS shl attempt)
        }
    }
    throw lastException ?: IllegalStateException("$operation failed without an exception")
}

/** Permission errors are expected while the auth token refreshes; anything else is a warning. */
internal fun logListenerError(operation: String, error: Throwable?) {
    error ?: return
    if ((error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        Log.d(DATA_TAG, "$operation listener permission denied: ${error.message}")
    } else {
        Log.w(DATA_TAG, "$operation listener error", error)
    }
}

/** A schema drift must not crash the Firestore listener thread. */
internal inline fun <reified T : Any> safeToObject(doc: DocumentSnapshot, operation: String): T? =
    runCatching { doc.toObject(T::class.java) }
        .onFailure { Log.e(DATA_TAG, "$operation: failed to decode ${doc.id}", it) }
        .getOrNull()

/** Stable error codes returned by the callables in `details.code`. */
enum class ApiErrorCode(val wire: String) {
    UNAUTHENTICATED("unauthenticated"),
    INVALID_ARGUMENT("invalidArgument"),
    GAME_NOT_FOUND("gameNotFound"),
    GAME_NOT_JOINABLE("gameNotJoinable"),
    GAME_FULL("gameFull"),
    ALREADY_HAS_ROLE("alreadyHasRole"),
    REGISTRATION_REQUIRED("registrationRequired"),
    NOT_ALLOWED("notAllowed"),
    NOT_A_HUNTER("notAHunter"),
    NOT_IN_PROGRESS("notInProgress"),
    GAME_OVER("gameOver"),
    TOO_MANY_ATTEMPTS("tooManyAttempts"),
    GAME_MASTER_DISABLED("gameMasterDisabled"),
    NOT_WAITING("notWaiting"),
    CHICKEN_CANNOT_LEAVE("chickenCannotLeave"),
    POWER_UP_NOT_FOUND("powerUpNotFound"),
    POWER_UP_TAKEN("powerUpTaken"),
    POWER_UP_WRONG_ROLE("powerUpWrongRole"),
    POWER_UP_TOO_FAR("powerUpTooFar"),
    POWER_UP_ALREADY_ACTIVE("powerUpAlreadyActive"),
    POSITION_UNKNOWN("positionUnknown"),
    SUBMISSION_NOT_FOUND("submissionNotFound"),
    SUBMISSION_ALREADY_HANDLED("submissionAlreadyHandled"),
    CHALLENGE_NOT_FOUND("challengeNotFound"),
    ALREADY_LAUNCHED("alreadyLaunched"),
    NOT_READY_TO_LAUNCH("notReadyToLaunch"),
    NOT_A_DEBUG_GAME("notADebugGame"),
    NETWORK("network"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): ApiErrorCode = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** A failure the UI can translate; never carries a server message to display. */
class ApiException(val code: ApiErrorCode, cause: Throwable? = null) : Exception(code.wire, cause)

internal fun Throwable.toApiErrorCode(): ApiErrorCode = when (this) {
    is ApiException -> code
    is FirebaseFunctionsException -> {
        val wire = (details as? Map<*, *>)?.get("code") as? String
        when {
            wire != null -> ApiErrorCode.fromWire(wire)
            code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> ApiErrorCode.NETWORK
            code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ApiErrorCode.TOO_MANY_ATTEMPTS
            else -> ApiErrorCode.UNKNOWN
        }
    }
    is FirebaseFirestoreException ->
        if (code == FirebaseFirestoreException.Code.UNAVAILABLE) ApiErrorCode.NETWORK else ApiErrorCode.UNKNOWN
    is java.io.IOException -> ApiErrorCode.NETWORK
    else -> ApiErrorCode.UNKNOWN
}

/** `lockedUntil` epoch millis attached to rate-limit errors, when present. */
internal fun Throwable.lockedUntilMs(): Long? =
    ((this as? FirebaseFunctionsException)?.details as? Map<*, *>)?.get("lockedUntil")?.let { (it as? Number)?.toLong() }

internal const val DEFAULT_RESUBSCRIBE_CAP_MS = 30_000L

internal fun resubscribeDelayMs(failures: Int, capMs: Long): Long =
    minOf(capMs, 1_000L shl minOf(failures, 10))

/**
 * Firebase listeners stop for good after an error. Resubscribing keeps the
 * last value on screen and resumes as soon as the backend accepts the read
 * again (network back, auth refreshed, radar ping granted).
 */
internal fun <T> Flow<T>.resubscribeOnError(capMs: Long = DEFAULT_RESUBSCRIBE_CAP_MS): Flow<T> {
    val upstream = this
    return flow {
        var failures = 0
        emitAll(
            upstream
                .onEach { failures = 0 }
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) return@retryWhen false
                    delay(resubscribeDelayMs(failures++, capMs))
                    true
                },
        )
    }
}
