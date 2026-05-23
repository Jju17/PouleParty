package dev.rahier.pouleparty.data

import android.util.Log
import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import java.util.Date
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeCompletion
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.gamelogic.PlayerRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: com.google.firebase.functions.FirebaseFunctions
) {

    companion object {
        private const val TAG = "FirestoreRepository"
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 500L

        // Unit reads (single `.get().await()`) block the UI for as long as
        // Firestore + the network take to reply. In a bad connectivity state
        // that can be "forever" — e.g. the hunter join flow stays on a
        // spinner with no way out. Cap single-doc fetches so the UI can
        // surface an error instead of freezing.
        private const val READ_TIMEOUT_MS = 15_000L

        /**
         * Logs a Firestore snapshot-listener error. `PERMISSION_DENIED` is a
         * transient hiccup during network wobbles / auth token refreshes —
         * the listener recovers automatically, so we log it at debug level to
         * avoid noisy warnings in production. Other errors stay at warn.
         */
        internal fun logListenerError(operation: String, error: Throwable?) {
            error ?: return
            if ((error as? FirebaseFirestoreException)?.code ==
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                Log.d(TAG, "$operation listener transient permission-denied (expected during auth refresh): ${error.message}")
            } else {
                Log.w(TAG, "$operation listener error", error)
            }
        }

        /**
         * Safe wrapper around `DocumentSnapshot.toObject`. Schema drift (e.g.
         * a field stored as a HashMap when the model expects a GeoPoint, as in
         * the 1.9.0 build 24 crash) makes `toObject` throw synchronously. When
         * that throw happens inside an `addSnapshotListener` callback the
         * exception bubbles up to the Firestore executor thread and kills the
         * whole process. Mirror iOS's ApiClient pattern: catch, log, return
         * null so downstream UI degrades gracefully.
         */
        internal inline fun <reified T : Any> safeToObject(
            doc: com.google.firebase.firestore.DocumentSnapshot,
            operation: String,
        ): T? = runCatching { doc.toObject(T::class.java) }
            .onFailure { Log.e(TAG, "$operation: failed to decode ${doc.id}", it) }
            .getOrNull()
    }

    private suspend fun <T> withRetry(operation: String, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "$operation failed (attempt ${attempt + 1}/$MAX_RETRIES)", e)
                if (attempt < MAX_RETRIES - 1) {
                    // Cap the shift so a future bump of MAX_RETRIES past 62
                    // can't overflow a Long. With MAX_RETRIES = 3 the cap is
                    // a no-op but keeps the call site safe by construction.
                    val shift = minOf(attempt, 20)
                    delay(INITIAL_DELAY_MS * (1L shl shift))
                }
            }
        }
        throw lastException ?: IllegalStateException("Retry exhausted with no exception")
    }

    // ── CRUD ──────────────────────────────────────────────

    suspend fun deleteConfig(gameId: String) {
        try {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete game $gameId", e)
            throw e
        }
    }

    suspend fun getConfig(gameId: String): Game? {
        return try {
            val doc = withTimeoutOrNull(READ_TIMEOUT_MS) {
                firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId).get().await()
            } ?: run {
                Log.w(TAG, "getConfig($gameId) timed out after ${READ_TIMEOUT_MS}ms")
                return null
            }
            safeToObject<Game>(doc, "getConfig($gameId)")?.copy(id = doc.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get game config $gameId", e)
            null
        }
    }

    data class ActiveGameResult(
        val game: Game,
        val role: dev.rahier.pouleparty.ui.gamelogic.PlayerRole,
        val phase: dev.rahier.pouleparty.ui.gamelogic.GamePhase,
    )

    suspend fun findActiveGame(userId: String): ActiveGameResult? {
        if (userId.isEmpty()) return null
        val activeStatuses = listOf(
            GameStatus.WAITING.firestoreValue,
            GameStatus.IN_PROGRESS.firestoreValue
        )
        try {
            val candidates = mutableListOf<Pair<Game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole>>()

            // Check if user is a hunter in active games
            val hunterSnapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereArrayContains("hunterIds", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()
            hunterSnapshot.documents.forEach { doc ->
                val game = safeToObject<Game>(doc, "findActiveGame hunter")?.copy(id = doc.id)
                if (game != null) candidates.add(Pair(game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole.HUNTER))
            }

            // Check if user is the chicken of active games (PP-26: query
            // `chickenId` instead of `creatorId` so a GM-designated chicken
            // resumes correctly).
            val chickenSnapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereEqualTo("chickenId", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()
            chickenSnapshot.documents.forEach { doc ->
                val game = safeToObject<Game>(doc, "findActiveGame chicken")?.copy(id = doc.id)
                if (game != null) candidates.add(Pair(game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole.CHICKEN))
            }

            // Check if user is a GameMaster of active games (PP-24).
            // The chicken / hunter buckets take priority if the same UID
            // ends up in multiple lists (defense in depth — the create
            // rule + the GM join CF already prevent that, see PP-23).
            val gmSnapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereArrayContains("gameMasterIds", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()
            gmSnapshot.documents.forEach { doc ->
                val game = safeToObject<Game>(doc, "findActiveGame gm")?.copy(id = doc.id)
                if (game != null) candidates.add(Pair(game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole.GAME_MASTER))
            }

            val now = Date()
            // Priority 1: games already in progress (most urgent). Filter out
            // those whose endDate has passed (transition Cloud Task delayed).
            val inProgress = candidates.filter {
                it.first.gameStatusEnum == GameStatus.IN_PROGRESS && it.first.endDate.after(now)
            }
            val latestInProgress = inProgress.maxByOrNull { it.first.startDate.time }
            if (latestInProgress != null) {
                return ActiveGameResult(
                    latestInProgress.first,
                    latestInProgress.second,
                    dev.rahier.pouleparty.ui.gamelogic.GamePhase.IN_PROGRESS,
                )
            }

            // Priority 2: upcoming (waiting + start in the future). Pick the
            // one starting soonest so the user's next deadline is surfaced.
            val upcoming = candidates.filter {
                it.first.gameStatusEnum == GameStatus.WAITING && it.first.startDate.after(now)
            }
            val earliestUpcoming = upcoming.minByOrNull { it.first.startDate.time }
            if (earliestUpcoming != null) {
                return ActiveGameResult(
                    earliestUpcoming.first,
                    earliestUpcoming.second,
                    dev.rahier.pouleparty.ui.gamelogic.GamePhase.UPCOMING,
                )
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find active game for user $userId", e)
        }
        return null
    }

    suspend fun findGameByCode(code: String): Game? {
        return try {
            val snapshot = withTimeoutOrNull(READ_TIMEOUT_MS) {
                firestore.collection(AppConstants.COLLECTION_GAMES)
                    .whereEqualTo("gameCode", code.uppercase())
                    .limit(1)
                    .get()
                    .await()
            } ?: run {
                Log.w(TAG, "findGameByCode($code) timed out after ${READ_TIMEOUT_MS}ms")
                return null
            }
            snapshot.documents.firstOrNull()?.let { doc ->
                safeToObject<Game>(doc, "findGameByCode($code)")?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find game by code $code", e)
            null
        }
    }

    suspend fun setConfig(game: Game) {
        withRetry("setConfig(${game.id})") {
            val ref = firestore.collection(AppConstants.COLLECTION_GAMES).document(game.id)
            val batch = firestore.batch()
            batch.set(ref, game)
            batch.update(ref, "gameCode", game.gameCode)
            batch.commit().await()
        }
    }

    /** CRIT-3 (audit 2026-05-17): submit the typed-in 4-digit code to the
     *  `submitFoundCode` Cloud Function. The CF verifies caller-is-hunter +
     *  foundCode-matches inside a Firestore transaction before appending to
     *  `winners`. firestore.rules denies all client writes to `winners`, so
     *  this is now the only path. Returns a [SubmitFoundCodeResult] for the
     *  caller to render the right UX.
     */
    suspend fun submitFoundCode(
        gameId: String,
        foundCode: String,
        hunterName: String,
    ): SubmitFoundCodeResult {
        val callable = functions
            .getHttpsCallable("submitFoundCode")
            .call(mapOf(
                "gameId" to gameId,
                "foundCode" to foundCode,
                "hunterName" to hunterName,
            ))
            .await()
        @Suppress("UNCHECKED_CAST")
        val raw = callable.getData() as? Map<String, Any?>
            ?: return SubmitFoundCodeResult.Failure(SubmitFoundCodeReason.MalformedResponse)
        val success = raw["success"] as? Boolean ?: false
        if (success) return SubmitFoundCodeResult.Success
        val reason = when (raw["reason"] as? String) {
            "invalidCode" -> SubmitFoundCodeReason.InvalidCode
            "notAHunter" -> SubmitFoundCodeReason.NotAHunter
            "alreadyWinner" -> SubmitFoundCodeReason.AlreadyWinner
            "gameNotInProgress" -> SubmitFoundCodeReason.GameNotInProgress
            else -> SubmitFoundCodeReason.MalformedResponse
        }
        return SubmitFoundCodeResult.Failure(reason)
    }

    /** CRIT-2 (audit 2026-05-17): fetch the game's 4-digit foundCode. The
     *  CF returns the code only if the caller is `chickenId` — the value
     *  lives in `/games/{id}/private/security` (admin-SDK only) since
     *  V2.3 so hunters can't read it off the public Game doc and
     *  self-declare victory. Returns "" if no code is set.
     */
    suspend fun getFoundCode(gameId: String): String {
        val callable = functions
            .getHttpsCallable("getFoundCode")
            .call(mapOf("gameId" to gameId))
            .await()
        @Suppress("UNCHECKED_CAST")
        val raw = callable.getData() as? Map<String, Any?> ?: return ""
        return (raw["foundCode"] as? String).orEmpty()
    }

    /** Outcome of [submitFoundCode]. */
    sealed class SubmitFoundCodeResult {
        object Success : SubmitFoundCodeResult()
        data class Failure(val reason: SubmitFoundCodeReason) : SubmitFoundCodeResult()
    }

    /** Distinct rejection reasons from the `submitFoundCode` CF. */
    enum class SubmitFoundCodeReason {
        InvalidCode,
        NotAHunter,
        AlreadyWinner,
        GameNotInProgress,
        MalformedResponse,
    }

    // ── Registrations ─────────────────────────────────────

    suspend fun findRegistration(gameId: String, userId: String): Registration? {
        if (gameId.isEmpty() || userId.isEmpty()) return null
        return try {
            val doc = withTimeoutOrNull(READ_TIMEOUT_MS) {
                firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                    .collection(AppConstants.SUBCOLLECTION_REGISTRATIONS).document(userId)
                    .get()
                    .await()
            } ?: run {
                Log.w(TAG, "findRegistration($gameId/$userId) timed out after ${READ_TIMEOUT_MS}ms")
                return null
            }
            if (!doc.exists()) return null
            safeToObject<Registration>(doc, "findRegistration($gameId/$userId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find registration $gameId/$userId", e)
            null
        }
    }

    suspend fun fetchAllRegistrations(gameId: String): List<Registration> {
        if (gameId.isEmpty()) return emptyList()
        return try {
            val snapshot = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_REGISTRATIONS)
                .get()
                .await()
            snapshot.documents.mapNotNull {
                safeToObject<Registration>(it, "fetchAllRegistrations($gameId)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch registrations for game $gameId", e)
            emptyList()
        }
    }

    /**
     * Live stream of every doc under `/games/{gameId}/registrations`. Used by
     * the GameMaster map (PP-86) so the hunter counter + drawer team-name
     * list refresh the moment a new hunter joins, instead of staying frozen
     * on the snapshot loaded once at screen entry.
     */
    fun registrationsFlow(gameId: String): Flow<List<Registration>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_REGISTRATIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Registrations (game $gameId)", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val regs = snapshot.documents.mapNotNull { doc ->
                    safeToObject<Registration>(doc, "Registrations (game $gameId)")
                }
                trySend(regs)
            }

        awaitClose { listener.remove() }
    }

    suspend fun createRegistration(gameId: String, registration: Registration) {
        if (gameId.isEmpty() || registration.userId.isEmpty()) {
            Log.w(TAG, "createRegistration skipped — gameId: '$gameId', userId: '${registration.userId}'")
            return
        }
        withRetry("createRegistration($gameId, ${registration.userId})") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_REGISTRATIONS).document(registration.userId)
                .set(registration)
                .await()
        }
    }

    suspend fun registerHunter(gameId: String, hunterId: String) {
        if (gameId.isEmpty() || hunterId.isEmpty()) {
            Log.w(TAG, "registerHunter skipped — gameId: '$gameId', hunterId: '$hunterId'")
            return
        }
        withRetry("registerHunter($gameId, $hunterId)") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update("hunterIds", FieldValue.arrayUnion(hunterId))
                .await()
        }
    }

    suspend fun updateGameStatus(gameId: String, status: GameStatus) {
        withRetry("updateGameStatus($gameId, $status)") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update("status", status.firestoreValue)
                .await()
        }
    }

    /** Heartbeat update so hunters can detect chicken disconnect.
     *  HIGH-18 (audit 2026-05-17): wrapped in withRetry to match iOS. A single
     *  transient write failure must not make the chicken look offline — hunters
     *  flip to "disconnected" after 60s of stale heartbeat and the loop only
     *  fires every 30s, so one lost write is enough to trip the UI. */
    suspend fun updateHeartbeat(gameId: String) {
        withRetry("updateHeartbeat($gameId)") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update("lastHeartbeat", Timestamp.now())
                .await()
        }
    }

    // ── Chicken location ──────────────────────────────────

    fun setChickenLocation(gameId: String, point: Point, invisible: Boolean = false) {
        val data = ChickenLocation(
            location = GeoPoint(point.latitude(), point.longitude()),
            timestamp = Timestamp.now(),
            invisible = invisible
        )
        firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS)
            .document("latest")
            .set(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to set chicken location for game $gameId", e) }
    }

    fun chickenLocationFlow(gameId: String): Flow<ChickenLocation?> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Chicken location (game $gameId)", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val doc = snapshot.documents.firstOrNull()
                val chickenLocation = doc?.let {
                    safeToObject<ChickenLocation>(it, "Chicken location (game $gameId)")
                }
                trySend(chickenLocation)
            }

        awaitClose { listener.remove() }
    }

    // ── Game config stream ────────────────────────────────

    fun gameConfigFlow(gameId: String): Flow<Game?> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Game config (game $gameId)", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                // `toObject` throws synchronously on a schema drift
                // (e.g. zone.center stored as a HashMap rather than a GeoPoint,
                // the 1.9.0 build 24 crash). Running inside an
                // addSnapshotListener callback means the throw propagates all
                // the way up to the Firestore executor thread and crashes the
                // whole process. Mirror iOS's ApiClient.gameConfigStream
                // pattern: catch, log with the gameId + field trace, emit null
                // so the downstream UI degrades gracefully instead of the app
                // being torn down.
                val game = runCatching {
                    snapshot.toObject(Game::class.java)?.copy(id = snapshot.id)
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "Failed to decode Game config for $gameId (exists=${snapshot.exists()})",
                        error,
                    )
                }.getOrNull()
                trySend(game)
            }

        awaitClose { listener.remove() }
    }

    // ── Hunter locations ──────────────────────────────────

    fun setHunterLocation(gameId: String, hunterId: String, point: Point) {
        if (gameId.isEmpty() || hunterId.isEmpty()) {
            Log.w(TAG, "setHunterLocation skipped — gameId: '$gameId', hunterId: '$hunterId'")
            return
        }
        val data = HunterLocation(
            hunterId = hunterId,
            location = GeoPoint(point.latitude(), point.longitude()),
            timestamp = Timestamp.now()
        )
        firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS)
            .document(hunterId)
            .set(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to set hunter location $hunterId in game $gameId", e) }
    }

    // ── Power-ups ──────────────────────────────────────

    suspend fun collectPowerUp(gameId: String, powerUpId: String, userId: String) {
        withRetry("collectPowerUp($gameId, $powerUpId)") {
            val docRef = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_POWER_UPS).document(powerUpId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val alreadyCollected = snapshot.getString("collectedBy")
                if (alreadyCollected != null) {
                    throw IllegalStateException("Power-up already collected by $alreadyCollected")
                }
                transaction.update(docRef, mapOf(
                    "collectedBy" to userId,
                    "collectedAt" to Timestamp.now()
                ))
            }.await()
        }
    }

    /**
     * Activates a collected power-up atomically. Sets `activatedAt` / `expiresAt`
     * on the power-up doc AND, if [activeEffectField] is non-null, sets
     * `powerUps.activeEffects.<field>` on the game doc — both in a single
     * Firestore transaction so the state never drifts (no half-activated
     * power-up with no visible effect).
     */
    suspend fun activatePowerUp(
        gameId: String,
        powerUpId: String,
        activeEffectField: String?,
        expiresAt: Timestamp
    ) {
        withRetry("activatePowerUp($gameId, $powerUpId)") {
            val puRef = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_POWER_UPS).document(powerUpId)
            val gameRef = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            firestore.runTransaction { txn ->
                val now = Timestamp.now()
                txn.update(puRef, mapOf(
                    "activatedAt" to now,
                    "expiresAt" to expiresAt
                ))
                if (activeEffectField != null) {
                    txn.update(gameRef, activeEffectField, expiresAt)
                }
                null
            }.await()
        }
    }

    fun powerUpsFlow(gameId: String): Flow<List<PowerUp>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_POWER_UPS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Power-ups (game $gameId)", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val powerUps = snapshot.documents.mapNotNull { doc ->
                    safeToObject<PowerUp>(doc, "Power-ups (game $gameId)")?.copy(id = doc.id)
                }
                trySend(powerUps)
            }

        awaitClose { listener.remove() }
    }

    // ── Hunter locations ──────────────────────────────────

    fun hunterLocationsFlow(gameId: String): Flow<List<HunterLocation>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Hunter locations (game $gameId)", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val hunters = snapshot.documents.mapNotNull { doc ->
                    safeToObject<HunterLocation>(doc, "Hunter locations (game $gameId)")
                }
                trySend(hunters)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Fetches the user's games: games they created AND games they joined as a hunter.
     * We run two separate queries (no orderBy to avoid needing composite indexes) and sort
     * client-side by start date descending. Limit to 20 after merging.
     */
    suspend fun fetchMyGames(userId: String): List<dev.rahier.pouleparty.model.MyGame> {
        val createdTask = firestore.collection(AppConstants.COLLECTION_GAMES)
            .whereEqualTo("creatorId", userId)
            .limit(30)
            .get()

        val joinedTask = firestore.collection(AppConstants.COLLECTION_GAMES)
            .whereArrayContains("hunterIds", userId)
            .limit(30)
            .get()

        val createdSnap = createdTask.await()
        val joinedSnap = joinedTask.await()

        val result = mutableListOf<dev.rahier.pouleparty.model.MyGame>()
        val seenIds = mutableSetOf<String>()

        for (doc in createdSnap.documents) {
            val game = safeToObject<Game>(doc, "fetchMyGames created")?.copy(id = doc.id) ?: continue
            if (seenIds.add(game.id)) {
                result.add(dev.rahier.pouleparty.model.MyGame(game, dev.rahier.pouleparty.model.MyGameRole.CREATOR))
            }
        }

        for (doc in joinedSnap.documents) {
            val game = safeToObject<Game>(doc, "fetchMyGames joined")?.copy(id = doc.id) ?: continue
            // Creator takes precedence if the user is both creator and hunter on the same game.
            if (seenIds.add(game.id)) {
                result.add(dev.rahier.pouleparty.model.MyGame(game, dev.rahier.pouleparty.model.MyGameRole.HUNTER))
            }
        }

        return result
            .sortedByDescending { it.game.startDate.time }
            .take(20)
    }

    // ── Challenges ───────────────────────────────────────

    /** Live stream of the global `/challenges` collection, ordered by points desc. */
    fun challengesStream(): Flow<List<Challenge>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_CHALLENGES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Challenges", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val challenges = snapshot.documents.mapNotNull { doc ->
                    safeToObject<Challenge>(doc, "Challenges")?.copy(id = doc.id)
                }
                trySend(challenges)
            }
        awaitClose { listener.remove() }
    }

    /** Live stream of `/games/{gameId}/challengeCompletions` — one doc per hunter. */
    fun challengeCompletionsStream(gameId: String): Flow<List<ChallengeCompletion>> = callbackFlow {
        if (gameId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHALLENGE_COMPLETIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Challenge completions (game $gameId)", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val completions = snapshot.documents.mapNotNull { doc ->
                    safeToObject<ChallengeCompletion>(doc, "Challenge completions (game $gameId)")?.copy(hunterId = doc.id)
                }
                trySend(completions)
            }
        awaitClose { listener.remove() }
    }

    /**
     * PP-36: decrement the hunter's `totalPoints` by 1 atomically. Used by
     * the out-of-zone penalty timer (-1 point every 5 s while the hunter is
     * outside the zone). Wrapped in a Firestore transaction so a concurrent
     * `markChallengeCompleted` from a parallel challenge submission can't
     * clobber the decrement (or vice versa). If no doc exists yet (the
     * hunter has never completed a challenge), we still seed one at -1 so
     * the leaderboard reflects the penalty. firestore.rules permits the
     * hunter to write their own doc as long as `totalPoints` is
     * monotonically non-increasing — see the PP-36 rule update.
     */
    suspend fun decrementTotalPoints(gameId: String, hunterId: String) {
        if (gameId.isEmpty() || hunterId.isEmpty()) {
            Log.w(
                TAG,
                "decrementTotalPoints skipped — gameId: '$gameId', hunterId: '$hunterId'"
            )
            return
        }
        withRetry("decrementTotalPoints($gameId, $hunterId)") {
            val docRef = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_CHALLENGE_COMPLETIONS).document(hunterId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val existing = if (snapshot.exists()) {
                    safeToObject<ChallengeCompletion>(
                        snapshot,
                        "decrementTotalPoints($gameId, $hunterId)"
                    )
                } else {
                    null
                }
                val data = mapOf(
                    "hunterId" to hunterId,
                    "completedChallengeIds" to (existing?.completedChallengeIds ?: emptyList()),
                    "totalPoints" to ((existing?.totalPoints ?: 0) - 1),
                    "teamName" to (existing?.teamName ?: "")
                )
                transaction.set(docRef, data)
                null
            }.await()
        }
    }

    /**
     * Marks a challenge as completed for the given hunter. Uses a Firestore transaction
     * so concurrent writes don't lose updates. If the hunter already completed this
     * challenge, it's a no-op (idempotent).
     */
    suspend fun markChallengeCompleted(
        gameId: String,
        hunterId: String,
        teamName: String,
        challengeId: String,
        points: Int
    ) {
        if (gameId.isEmpty() || hunterId.isEmpty() || challengeId.isEmpty()) {
            Log.w(
                TAG,
                "markChallengeCompleted skipped — gameId: '$gameId', hunterId: '$hunterId', challengeId: '$challengeId'"
            )
            return
        }
        withRetry("markChallengeCompleted($gameId, $hunterId, $challengeId)") {
            val docRef = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_CHALLENGE_COMPLETIONS).document(hunterId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val existing = if (snapshot.exists()) {
                    safeToObject<ChallengeCompletion>(
                        snapshot,
                        "markChallengeCompleted($gameId, $hunterId)"
                    )
                } else {
                    null
                }
                if (existing != null && existing.completedChallengeIds.contains(challengeId)) {
                    // Idempotent: already completed.
                    return@runTransaction null
                }
                val updatedIds = (existing?.completedChallengeIds ?: emptyList()) + challengeId
                val updatedTotal = (existing?.totalPoints ?: 0) + points
                val data = mapOf(
                    "hunterId" to hunterId,
                    "completedChallengeIds" to updatedIds,
                    "totalPoints" to updatedTotal,
                    "teamName" to teamName
                )
                transaction.set(docRef, data)
                null
            }.await()
        }
    }

    /**
     * Fetches the nickname for each of the given user ids. Missing users
     * simply don't appear in the returned map.
     */
    suspend fun fetchNicknames(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (userId in userIds.distinct()) {
            if (userId.isEmpty()) continue
            try {
                val doc = firestore.collection(AppConstants.COLLECTION_USERS)
                    .document(userId)
                    .get()
                    .await()
                val nickname = doc.getString("nickname")
                if (!nickname.isNullOrEmpty()) {
                    result[userId] = nickname
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch nickname for $userId", e)
            }
        }
        return result
    }

    suspend fun saveNickname(userId: String, nickname: String) {
        try {
            firestore.collection("users")
                .document(userId)
                .set(
                    mapOf(
                        "nickname" to nickname,
                        "updatedAt" to Timestamp.now()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save nickname", e)
        }
    }

    /**
     * Delete the user profile document (`/users/{userId}`). Called from the
     * account-deletion flow before the Firebase Auth user itself is removed,
     * since Firestore rules require `auth.uid == userId` to authorize the delete.
     */
    suspend fun deleteUser(userId: String) {
        firestore.collection("users")
            .document(userId)
            .delete()
            .await()
    }

    /**
     * Submit a report against another player. Writes to the admin-SDK-only
     * `/reports` collection so the team can review user-generated-content abuse
     * (offensive nicknames, cheating, etc.) that surfaces via leaderboards.
     */
    suspend fun reportPlayer(
        reporterId: String,
        reportedUserId: String,
        reportedNickname: String,
        gameId: String
    ) {
        firestore.collection("reports")
            .add(
                mapOf(
                    "reporterId" to reporterId,
                    "reportedUserId" to reportedUserId,
                    "reportedNickname" to reportedNickname,
                    "gameId" to gameId,
                    "createdAt" to Timestamp.now()
                )
            )
            .await()
    }

    // ── GameMaster (PP-70 / PP-88) ────────────────────────

    /** Result of a `joinAsGameMaster` call. */
    data class JoinAsGameMasterResult(
        val success: Boolean,
        val attemptsRemaining: Int,
        val lockedUntilMs: Long?
    )

    /**
     * Calls the `setGameMasterPassword` Cloud Function. Only the
     * game's creator may call this; the CF writes the password to the
     * private subcollection and flips `Game.hasGameMasterPassword`.
     */
    suspend fun setGameMasterPassword(gameId: String, password: String) {
        functions
            .getHttpsCallable("setGameMasterPassword")
            .call(mapOf("gameId" to gameId, "password" to password))
            .await()
    }

    /**
     * Calls the `clearGameMasterPassword` Cloud Function. Only the
     * creator may call this. Existing GMs in `gameMasterIds` are
     * kept.
     */
    suspend fun clearGameMasterPassword(gameId: String) {
        functions
            .getHttpsCallable("clearGameMasterPassword")
            .call(mapOf("gameId" to gameId))
            .await()
    }

    /**
     * Calls the `joinAsGameMaster` Cloud Function. Rate-limited
     * server-side (5 attempts → 5 min lock). Returns
     * [JoinAsGameMasterResult] with the wrong-password attempts left
     * and the lock expiry timestamp when the lock kicked in.
     */
    /**
     * PP-86: GameMaster (or creator as fallback) designates a hunter as
     * the new chicken. Atomic Firestore transaction: sets
     * `chickenId = newUid` and pulls `newUid` out of `hunterIds`.
     * The PP-26 firestore.rule enforces `status == waiting` and the
     * caller-is-creator-or-GM guard server-side; we pre-check
     * `status == waiting` client-side for fast feedback.
     */
    suspend fun designateChicken(gameId: String, newChickenUid: String) {
        firestore.runTransaction { tx ->
            val ref = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            val snap = tx.get(ref)
            val data = snap.data ?: throw IllegalStateException("Game not found")
            val status = data["status"] as? String
            if (status != GameStatus.WAITING.firestoreValue) {
                throw IllegalStateException("Chicken can only be re-designated while the game is waiting")
            }
            @Suppress("UNCHECKED_CAST")
            val hunterIds = (data["hunterIds"] as? List<String>).orEmpty().toMutableList()
            hunterIds.remove(newChickenUid)
            tx.update(ref, mapOf(
                "chickenId" to newChickenUid,
                "hunterIds" to hunterIds,
            ))
            null
        }.await()
    }

    suspend fun joinAsGameMaster(gameId: String, password: String): JoinAsGameMasterResult {
        val callableResult = functions
            .getHttpsCallable("joinAsGameMaster")
            .call(mapOf("gameId" to gameId, "password" to password))
            .await()
        @Suppress("UNCHECKED_CAST")
        val raw = callableResult.getData() as? Map<String, Any?> ?: emptyMap()
        return JoinAsGameMasterResult(
            success = (raw["success"] as? Boolean) ?: false,
            attemptsRemaining = (raw["attemptsRemaining"] as? Number)?.toInt() ?: 0,
            lockedUntilMs = (raw["lockedUntil"] as? Number)?.toLong()
        )
    }

    // ── Zone configuration (PP-69) ────────────────────────

    /**
     * Latitude/longitude pair shared by the zone-configuration callable
     * input + output. Kept separate from `com.mapbox.geojson.Point` so
     * the repository layer can stay independent of the Mapbox SDK.
     */
    data class ZoneLatLng(val lat: Double, val lng: Double)

    /** One circle in the precomputed shrink schedule returned by the CF. */
    data class ZoneCircle(val radiusMeters: Double, val center: ZoneLatLng)

    /** Input payload for [computeZoneConfiguration]. */
    data class ComputeZoneConfigurationInput(
        val startPoint: ZoneLatLng,
        val finalPoint: ZoneLatLng?,
        val gameMode: String,
        // 500 / 1000 / 2000 m. Required in followTheChicken.
        val radiusHint: Double?,
        val gameDurationMinutes: Double,
        // `true` from the Shuffle button (PP-14 Phase 2).
        val forceNewSeed: Boolean = false,
        // Pins the returned `driftSeed` when set.
        val existingSeed: Int? = null,
    )

    /** Output of [computeZoneConfiguration]. */
    data class ComputeZoneConfigurationOutput(
        val initialRadius: Double,
        val validatedFinal: ZoneLatLng?,
        val driftSeed: Int,
        val finalZoneRadius: Double,
        val interiorMargin: Double,
        val shrinkIntervalMinutes: Double,
        val shrinkMetersPerUpdate: Double,
        val circles: List<ZoneCircle>,
    )

    /**
     * Calls the `computeZoneConfiguration` Cloud Function (PP-69). The
     * CF is the single source of truth for the radius, drift seed and
     * shrink schedule used by the wizard recap (PP-13) and Shuffle
     * button (PP-14). Once the client-side `computeZoneRadius` /
     * `deterministicDriftCenter` mirrors are deleted in PP-13 Phase 2 /
     * PP-14 Phase 2, this wrapper becomes the only path the recap step
     * walks.
     */
    suspend fun computeZoneConfiguration(
        input: ComputeZoneConfigurationInput,
    ): ComputeZoneConfigurationOutput {
        val payload = HashMap<String, Any?>()
        payload["startPoint"] = mapOf("lat" to input.startPoint.lat, "lng" to input.startPoint.lng)
        payload["finalPoint"] = input.finalPoint?.let { mapOf("lat" to it.lat, "lng" to it.lng) }
        payload["gameMode"] = input.gameMode
        payload["radiusHint"] = input.radiusHint
        payload["gameDurationMinutes"] = input.gameDurationMinutes
        payload["forceNewSeed"] = input.forceNewSeed
        if (input.existingSeed != null) payload["existingSeed"] = input.existingSeed

        val callable = functions
            .getHttpsCallable("computeZoneConfiguration")
            .call(payload)
            .await()
        @Suppress("UNCHECKED_CAST")
        val raw = callable.getData() as? Map<String, Any?>
            ?: throw IllegalStateException("computeZoneConfiguration: malformed response")

        fun asDouble(value: Any?): Double? = (value as? Number)?.toDouble()
        fun asInt(value: Any?): Int? = (value as? Number)?.toInt()
        fun asLatLng(value: Any?): ZoneLatLng? {
            @Suppress("UNCHECKED_CAST")
            val map = value as? Map<String, Any?> ?: return null
            val lat = asDouble(map["lat"]) ?: return null
            val lng = asDouble(map["lng"]) ?: return null
            return ZoneLatLng(lat, lng)
        }

        @Suppress("UNCHECKED_CAST")
        val rawCircles = (raw["circles"] as? List<Map<String, Any?>>).orEmpty()
        val circles = rawCircles.mapNotNull { entry ->
            val radius = asDouble(entry["radiusMeters"]) ?: return@mapNotNull null
            val center = asLatLng(entry["center"]) ?: return@mapNotNull null
            ZoneCircle(radius, center)
        }

        return ComputeZoneConfigurationOutput(
            initialRadius = asDouble(raw["initialRadius"]) ?: 0.0,
            validatedFinal = asLatLng(raw["validatedFinal"]),
            driftSeed = asInt(raw["driftSeed"]) ?: 1,
            finalZoneRadius = asDouble(raw["finalZoneRadius"]) ?: 50.0,
            interiorMargin = asDouble(raw["interiorMargin"]) ?: 200.0,
            shrinkIntervalMinutes = asDouble(raw["shrinkIntervalMinutes"]) ?: 5.0,
            shrinkMetersPerUpdate = asDouble(raw["shrinkMetersPerUpdate"]) ?: 0.0,
            circles = circles,
        )
    }
}
