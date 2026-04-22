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
import dev.rahier.pouleparty.model.PartyPlansConfig
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.gamelogic.PlayerRole
import dev.rahier.pouleparty.util.startOfToday
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "FirestoreRepository"
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 500L

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
            val doc = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId).get().await()
            doc.toObject(Game::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get game config $gameId", e)
            null
        }
    }

    suspend fun findActiveGame(userId: String): Pair<Game, PlayerRole>? {
        if (userId.isEmpty()) return null
        val activeStatuses = listOf(
            GameStatus.WAITING.firestoreValue,
            GameStatus.IN_PROGRESS.firestoreValue
        )
        try {
            val candidates = mutableListOf<Pair<Game, PlayerRole>>()

            // Check if user is a hunter in active games
            val hunterSnapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereArrayContains("hunterIds", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()
            hunterSnapshot.documents.forEach { doc ->
                val game = doc.toObject(Game::class.java)?.copy(id = doc.id)
                if (game != null) candidates.add(Pair(game, PlayerRole.HUNTER))
            }

            // Check if user is the chicken (creator) of active games
            val chickenSnapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereEqualTo("creatorId", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()
            chickenSnapshot.documents.forEach { doc ->
                val game = doc.toObject(Game::class.java)?.copy(id = doc.id)
                if (game != null) candidates.add(Pair(game, PlayerRole.CHICKEN))
            }

            // Filter out games whose end time has already passed (status may
            // not have been updated to DONE yet due to network/Cloud Task lag)
            val now = Date()
            val stillActive = candidates.filter { it.first.endDate.after(now) }

            // Return the most recently started game
            return stillActive.maxByOrNull { it.first.startDate.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find active game for user $userId", e)
        }
        return null
    }

    suspend fun findGameByCode(code: String): Game? {
        return try {
            val snapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereEqualTo("gameCode", code.uppercase())
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.let { doc ->
                doc.toObject(Game::class.java)?.copy(id = doc.id)
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

    suspend fun addWinner(gameId: String, winner: Winner) {
        withRetry("addWinner($gameId)") {
            val winnerMap = mapOf(
                "hunterId" to winner.hunterId,
                "hunterName" to winner.hunterName,
                "timestamp" to winner.timestamp
            )
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update("winners", FieldValue.arrayUnion(winnerMap))
                .await()
        }
    }

    // ── Registrations ─────────────────────────────────────

    suspend fun findRegistration(gameId: String, userId: String): Registration? {
        if (gameId.isEmpty() || userId.isEmpty()) return null
        return try {
            val doc = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_REGISTRATIONS).document(userId)
                .get()
                .await()
            if (!doc.exists()) return null
            doc.toObject(Registration::class.java)
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
            snapshot.documents.mapNotNull { it.toObject(Registration::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch registrations for game $gameId", e)
            emptyList()
        }
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

    /** Fire-and-forget heartbeat update so hunters can detect chicken disconnect. */
    fun updateHeartbeat(gameId: String) {
        firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .update("lastHeartbeat", Timestamp.now())
            .addOnFailureListener { e -> Log.w(TAG, "Failed to update heartbeat: $e") }
    }

    // ── Chicken location ──────────────────────────────────

    fun setChickenLocation(gameId: String, point: Point) {
        val data = ChickenLocation(
            location = GeoPoint(point.latitude(), point.longitude()),
            timestamp = Timestamp.now()
        )
        firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS)
            .document("latest")
            .set(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to set chicken location for game $gameId", e) }
    }

    fun chickenLocationFlow(gameId: String): Flow<Point?> = callbackFlow {
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
                if (chickenLocation != null) {
                    trySend(
                        Point.fromLngLat(
                            chickenLocation.location.longitude,
                            chickenLocation.location.latitude
                        )
                    )
                } else {
                    trySend(null)
                }
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

    suspend fun countFreeGamesToday(userId: String): Int {
        return try {
            val startOfDay = Timestamp(startOfToday().time)

            val snapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereEqualTo("creatorId", userId)
                .whereEqualTo("pricing.model", "free")
                .whereGreaterThanOrEqualTo("timing.start", startOfDay)
                .get()
                .await()

            snapshot.documents.size
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count free games today for $userId", e)
            0
        }
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
            val game = doc.toObject(Game::class.java)?.copy(id = doc.id) ?: continue
            if (seenIds.add(game.id)) {
                result.add(dev.rahier.pouleparty.model.MyGame(game, dev.rahier.pouleparty.model.MyGameRole.CREATOR))
            }
        }

        for (doc in joinedSnap.documents) {
            val game = doc.toObject(Game::class.java)?.copy(id = doc.id) ?: continue
            // Creator takes precedence if the user is both creator and hunter on the same game.
            if (seenIds.add(game.id)) {
                result.add(dev.rahier.pouleparty.model.MyGame(game, dev.rahier.pouleparty.model.MyGameRole.HUNTER))
            }
        }

        return result
            .sortedByDescending { it.game.startDate.time }
            .take(20)
    }

    suspend fun fetchPartyPlansConfig(): PartyPlansConfig {
        val doc = firestore.collection("config")
            .document("partyPlans")
            .get()
            .await()
        return doc.toObject(PartyPlansConfig::class.java)
            ?: throw IllegalStateException("Party plans config document is missing or malformed")
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
                    snapshot.toObject(ChallengeCompletion::class.java)
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
}
