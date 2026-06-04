package dev.rahier.pouleparty.data

import android.util.Log
import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.Date
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeCompletion
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.ChallengeType
import dev.rahier.pouleparty.model.SubmissionMediaType
import dev.rahier.pouleparty.model.SubmissionStatus
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.gamelogic.PlayerRole
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
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
    private val functions: com.google.firebase.functions.FirebaseFunctions,
    private val storage: com.google.firebase.storage.FirebaseStorage,
    private val database: FirebaseDatabase,
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

    /**
     * PP-zone-stored: fetch the immutable ordered circle schedule from
     * `/games/{id}/zone/schedule` (written server-side by `onGameCreated`).
     * Read once when the map mounts — the doc never changes after creation,
     * so no stream is needed. Returns empty on miss/timeout; callers treat
     * an empty list as "no zone to render" (no legacy fallback compute).
     */
    suspend fun fetchZoneSchedule(gameId: String): List<dev.rahier.pouleparty.model.ZoneCircle> {
        if (gameId.isEmpty()) return emptyList()
        return try {
            val doc = withTimeoutOrNull(READ_TIMEOUT_MS) {
                firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                    .collection("zone").document("schedule").get().await()
            } ?: run {
                Log.w(TAG, "fetchZoneSchedule($gameId) timed out after ${READ_TIMEOUT_MS}ms")
                return emptyList()
            }
            @Suppress("UNCHECKED_CAST")
            val raw = doc.get("circles") as? List<Map<String, Any?>> ?: return emptyList()
            raw.mapNotNull { m ->
                val radius = (m["radiusMeters"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lat = (m["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (m["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                val order = (m["order"] as? Number)?.toInt() ?: 0
                dev.rahier.pouleparty.model.ZoneCircle(order = order, radiusMeters = radius, lat = lat, lng = lng)
            }.sortedBy { it.order }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch zone schedule $gameId", e)
            emptyList()
        }
    }

    data class ActiveGameResult(
        val game: Game,
        val role: dev.rahier.pouleparty.ui.gamelogic.PlayerRole,
        val phase: dev.rahier.pouleparty.ui.gamelogic.GamePhase,
    )

    suspend fun findActiveGame(userId: String): ActiveGameResult? {
        if (userId.isEmpty()) return null
        try {
            val candidates = mutableListOf<Pair<Game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole>>()

            // PP-107: membership now lives in `/users/{uid}/memberships`
            // (one doc per game the user belongs to, `{ gameId, role }`),
            // written server-side by the role callables. The old three
            // parallel array-contains queries on `hunterIds` / `chickenId` /
            // `gameMasterIds` are gone — those fields no longer exist on the
            // game doc.
            val membershipSnapshot = firestore.collection(AppConstants.COLLECTION_USERS)
                .document(userId)
                .collection(AppConstants.SUBCOLLECTION_MEMBERSHIPS)
                .get()
                .await()

            val gameIds = membershipSnapshot.documents.mapNotNull { doc ->
                (doc.getString("gameId")) ?: doc.id.ifEmpty { null }
            }.toSet()

            coroutineScope {
                gameIds.map { gameId ->
                    async {
                        val snap = runCatching {
                            firestore.collection(AppConstants.COLLECTION_GAMES)
                                .document(gameId).get().await()
                        }.getOrNull() ?: return@async null
                        safeToObject<Game>(snap, "findActiveGame membership")?.copy(id = snap.id)
                    }
                }.awaitAll()
            }.filterNotNull().forEach { game ->
                // Resolve the role from the authoritative `roles` map. A
                // membership with no matching role (stale doc, game left) is
                // dropped.
                val role = when {
                    game.isChicken(userId) -> dev.rahier.pouleparty.ui.gamelogic.PlayerRole.CHICKEN
                    game.isGameMaster(userId) -> dev.rahier.pouleparty.ui.gamelogic.PlayerRole.GAME_MASTER
                    game.isHunter(userId) -> dev.rahier.pouleparty.ui.gamelogic.PlayerRole.HUNTER
                    else -> return@forEach
                }
                candidates.add(Pair(game, role))
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
                    .collection(AppConstants.SUBCOLLECTION_PLAYERS).document(userId)
                    .get()
                    .await()
            } ?: run {
                Log.w(TAG, "findRegistration($gameId/$userId) timed out after ${READ_TIMEOUT_MS}ms")
                return null
            }
            if (!doc.exists()) return null
            // PP-107: `/players` docs carry only `{ teamName, joinedAt }`;
            // the uid is the doc id, so backfill it after decoding.
            safeToObject<Registration>(doc, "findRegistration($gameId/$userId)")?.copy(userId = doc.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find registration $gameId/$userId", e)
            null
        }
    }

    suspend fun fetchAllRegistrations(gameId: String): List<Registration> {
        if (gameId.isEmpty()) return emptyList()
        return try {
            val snapshot = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_PLAYERS)
                .get()
                .await()
            snapshot.documents.mapNotNull {
                safeToObject<Registration>(it, "fetchAllRegistrations($gameId)")?.copy(userId = it.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch registrations for game $gameId", e)
            emptyList()
        }
    }

    /**
     * Live stream of every doc under `/games/{gameId}/players` (PP-107
     * rename from `registrations`). Used by the GameMaster map (PP-86) so
     * the hunter counter + drawer team-name list refresh the moment a new
     * hunter joins, instead of staying frozen on the snapshot loaded once
     * at screen entry.
     */
    fun registrationsFlow(gameId: String): Flow<List<Registration>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_PLAYERS)
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
                    safeToObject<Registration>(doc, "Registrations (game $gameId)")?.copy(userId = doc.id)
                }
                trySend(regs)
            }

        awaitClose { listener.remove() }
    }

    /**
     * PP-107: joins the caller as a hunter. Replaces the old client-side
     * `hunterIds` arrayUnion + `/registrations` doc write. The `joinGame`
     * callable writes the hunter role into the `roles` map, the
     * `/players/{uid}` team-name doc, and the `/users/{uid}/memberships`
     * index in one atomic, idempotent server step. Re-running it on
     * "Reprendre la partie" (or an old game) is the safety net that keeps
     * the GameMaster marker labeled.
     */
    suspend fun joinGame(gameId: String, teamName: String) {
        if (gameId.isEmpty()) {
            Log.w(TAG, "joinGame skipped — empty gameId")
            return
        }
        functions
            .getHttpsCallable("joinGame")
            .call(mapOf("gameId" to gameId, "teamName" to teamName))
            .await()
    }

    /**
     * PP-107: leaves the game — removes the caller's role from `roles`,
     * their `/players/{uid}` doc, and their membership index, server-side.
     */
    suspend fun leaveGame(gameId: String) {
        if (gameId.isEmpty()) {
            Log.w(TAG, "leaveGame skipped — empty gameId")
            return
        }
        functions
            .getHttpsCallable("leaveGame")
            .call(mapOf("gameId" to gameId))
            .await()
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
        // PP-102: presence moved to RTDB. Mark the chicken online and arm an
        // onDisconnect so a dropped connection flips it offline server-side
        // immediately. Re-armed each tick; retry keeps a transient failure
        // from making the chicken look offline.
        withRetry("updateHeartbeat($gameId)") {
            val ref = database.getReference(
                "${AppConstants.COLLECTION_GAMES}/$gameId/presence/chicken"
            )
            ref.onDisconnect()
                .setValue(mapOf("online" to false, "ts" to ServerValue.TIMESTAMP))
                .await()
            ref.setValue(mapOf("online" to true, "ts" to ServerValue.TIMESTAMP))
                .await()
        }
    }

    // ── Chicken location ──────────────────────────────────

    fun setChickenLocation(gameId: String, point: Point, invisible: Boolean = false) {
        // PP-102: fire-and-forget RTDB write. `ts` is a server timestamp; the
        // `invisible` flag (PP-87) rides along and gates hunter reads.
        database.getReference(
            "${AppConstants.COLLECTION_GAMES}/$gameId/${AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS}/latest"
        ).setValue(
            mapOf(
                "lat" to point.latitude(),
                "lng" to point.longitude(),
                "ts" to ServerValue.TIMESTAMP,
                "invisible" to invisible,
            )
        ).addOnFailureListener { e -> Log.e(TAG, "Failed to set chicken location for game $gameId", e) }
    }

    fun chickenLocationFlow(gameId: String): Flow<ChickenLocation?> = callbackFlow {
        val ref = database.getReference(
            "${AppConstants.COLLECTION_GAMES}/$gameId/${AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS}/latest"
        )
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(if (snapshot.exists()) ChickenLocation.fromRtdb(snapshot) else null)
            }

            override fun onCancelled(error: DatabaseError) {
                logListenerError("Chicken location (game $gameId)", error.toException())
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
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
        // PP-102: fire-and-forget RTDB write. The hunterId is the RTDB key.
        database.getReference(
            "${AppConstants.COLLECTION_GAMES}/$gameId/${AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS}/$hunterId"
        ).setValue(
            mapOf(
                "lat" to point.latitude(),
                "lng" to point.longitude(),
                "ts" to ServerValue.TIMESTAMP,
            )
        ).addOnFailureListener { e -> Log.e(TAG, "Failed to set hunter location $hunterId in game $gameId", e) }
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
     * Activates a collected power-up via the `activatePowerUp` callable.
     * Trailing [activeEffectField] / [expiresAt] are ignored — duration
     * is server-authoritative.
     */
    suspend fun activatePowerUp(
        gameId: String,
        powerUpId: String,
        @Suppress("UNUSED_PARAMETER") activeEffectField: String?,
        @Suppress("UNUSED_PARAMETER") expiresAt: Timestamp
    ) {
        withRetry("activatePowerUp($gameId, $powerUpId)") {
            functions
                .getHttpsCallable("activatePowerUp")
                .call(mapOf(
                    "gameId" to gameId,
                    "powerUpId" to powerUpId,
                ))
                .await()
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
        val ref = database.getReference(
            "${AppConstants.COLLECTION_GAMES}/$gameId/${AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS}"
        )
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val hunters = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    HunterLocation.fromRtdb(key, child)
                }
                trySend(hunters)
            }

            override fun onCancelled(error: DatabaseError) {
                logListenerError("Hunter locations (game $gameId)", error.toException())
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Fetches the user's games: games they created (still a top-level
     * `creatorId` field) AND games they belong to via the PP-107
     * `/users/{uid}/memberships` index. The old `hunterIds` array query is
     * gone — that field no longer exists. Creator takes precedence on
     * dedupe. Sorted by start date descending, limited to 20.
     */
    suspend fun fetchMyGames(userId: String): List<dev.rahier.pouleparty.model.MyGame> {
        val createdTask = firestore.collection(AppConstants.COLLECTION_GAMES)
            .whereEqualTo("creatorId", userId)
            .limit(30)
            .get()

        val membershipTask = firestore.collection(AppConstants.COLLECTION_USERS)
            .document(userId)
            .collection(AppConstants.SUBCOLLECTION_MEMBERSHIPS)
            .limit(30)
            .get()

        val createdSnap = createdTask.await()
        val membershipSnap = membershipTask.await()

        val result = mutableListOf<dev.rahier.pouleparty.model.MyGame>()
        val seenIds = mutableSetOf<String>()

        for (doc in createdSnap.documents) {
            val game = safeToObject<Game>(doc, "fetchMyGames created")?.copy(id = doc.id) ?: continue
            if (seenIds.add(game.id)) {
                result.add(dev.rahier.pouleparty.model.MyGame(game, dev.rahier.pouleparty.model.MyGameRole.CREATOR))
            }
        }

        // Fetch each membership's game doc (skipping ones already added as
        // creator). Creator takes precedence if the same user appears twice.
        val membershipGameIds = membershipSnap.documents.mapNotNull { doc ->
            (doc.getString("gameId")) ?: doc.id.ifEmpty { null }
        }.toSet().filter { it !in seenIds }

        coroutineScope {
            membershipGameIds.map { gameId ->
                async {
                    val snap = runCatching {
                        firestore.collection(AppConstants.COLLECTION_GAMES)
                            .document(gameId).get().await()
                    }.getOrNull() ?: return@async null
                    safeToObject<Game>(snap, "fetchMyGames joined")?.copy(id = snap.id)
                }
            }.awaitAll()
        }.filterNotNull().forEach { game ->
            if (seenIds.add(game.id)) {
                result.add(dev.rahier.pouleparty.model.MyGame(game, dev.rahier.pouleparty.model.MyGameRole.HUNTER))
            }
        }

        return result
            .sortedByDescending { it.game.startDate.time }
            .take(20)
    }

    // ── Challenges ───────────────────────────────────────

    fun challengesStream(gameId: String): Flow<List<Challenge>> = callbackFlow {
        if (gameId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.COLLECTION_CHALLENGES)
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
    /** PP-103: live challenge leaderboard, read from the single
     *  `aggregates/leaderboard` doc instead of streaming the whole
     *  challengeCompletions collection. Entries carry only hunterId /
     *  teamName / totalPoints. */
    fun leaderboardFlow(gameId: String): Flow<List<ChallengeCompletion>> = callbackFlow {
        if (gameId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection("aggregates").document("leaderboard")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Leaderboard (game $gameId)", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val entries = snapshot?.get("entries") as? Map<String, Map<String, Any?>> ?: emptyMap()
                val completions = entries.map { (hunterId, entry) ->
                    ChallengeCompletion(
                        hunterId = hunterId,
                        totalPoints = (entry["totalPoints"] as? Number)?.toInt() ?: 0,
                        teamName = (entry["teamName"] as? String) ?: "",
                    )
                }.sortedWith(
                    compareByDescending<ChallengeCompletion> { it.totalPoints }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.teamName }
                        .thenBy { it.hunterId }
                )
                trySend(completions)
            }
        awaitClose { listener.remove() }
    }

    /** PP-103: the current hunter's OWN completion doc (validatedChallengeIds
     *  etc.), a single-doc listener for their live "validated" checkmarks. */
    fun myCompletionFlow(gameId: String, hunterId: String): Flow<ChallengeCompletion?> = callbackFlow {
        if (gameId.isEmpty() || hunterId.isEmpty()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHALLENGE_COMPLETIONS).document(hunterId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("My completion (game $gameId)", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                val completion = if (snapshot != null && snapshot.exists()) {
                    safeToObject<ChallengeCompletion>(snapshot, "My completion (game $gameId)")?.copy(hunterId = snapshot.id)
                } else null
                trySend(completion)
            }
        awaitClose { listener.remove() }
    }

    suspend fun decrementTotalPoints(gameId: String, hunterId: String) {
        if (gameId.isEmpty() || hunterId.isEmpty()) {
            Log.w(
                TAG,
                "decrementTotalPoints skipped — gameId: '$gameId', hunterId: '$hunterId'"
            )
            return
        }
        withRetry("applyOutOfZonePenalty($gameId, $hunterId)") {
            functions
                .getHttpsCallable("applyOutOfZonePenalty")
                .call(mapOf("gameId" to gameId))
                .await()
        }
    }

    fun pendingSubmissionsFlow(gameId: String): Flow<List<ChallengeSubmission>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHALLENGE_SUBMISSIONS)
            .whereEqualTo("status", SubmissionStatus.PENDING.firestoreValue)
            .orderBy("submittedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Pending submissions ($gameId)", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val subs = snapshot.documents.mapNotNull { doc ->
                    safeToObject<ChallengeSubmission>(doc, "Pending submissions ($gameId)")?.copy(id = doc.id)
                }
                trySend(subs)
            }
        awaitClose { listener.remove() }
    }

    suspend fun validateChallengeSubmission(gameId: String, submissionId: String, accept: Boolean) {
        withRetry("validateChallengeSubmission($gameId, $submissionId, $accept)") {
            functions
                .getHttpsCallable("validateChallengeSubmission")
                .call(mapOf(
                    "gameId" to gameId,
                    "submissionId" to submissionId,
                    "accept" to accept,
                ))
                .await()
        }
    }

    fun hunterSubmissionsFlow(gameId: String, hunterId: String): Flow<List<ChallengeSubmission>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHALLENGE_SUBMISSIONS)
            .whereEqualTo("hunterId", hunterId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Hunter submissions ($gameId, $hunterId)", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val submissions = snapshot.documents.mapNotNull { doc ->
                    safeToObject<ChallengeSubmission>(doc, "Hunter submissions ($gameId, $hunterId)")?.copy(id = doc.id)
                }
                trySend(submissions)
            }
        awaitClose { listener.remove() }
    }

    suspend fun submitChallenge(
        gameId: String,
        challengeId: String,
        hunterId: String,
        type: ChallengeType,
        mediaBytes: ByteArray,
        mediaType: SubmissionMediaType,
    ): ChallengeSubmission {
        val submissionsRef = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHALLENGE_SUBMISSIONS)
        val existing = submissionsRef
            .whereEqualTo("hunterId", hunterId)
            .whereEqualTo("challengeId", challengeId)
            .limit(10)
            .get()
            .await()
        for (doc in existing.documents) {
            val sub = safeToObject<ChallengeSubmission>(doc, "submitChallenge($gameId, $hunterId)")
                ?: continue
            if (sub.statusEnum == SubmissionStatus.PENDING) {
                throw IllegalStateException("A submission for this challenge is already pending.")
            }
            if (type == ChallengeType.ONE_SHOT && sub.statusEnum == SubmissionStatus.VALIDATED) {
                throw IllegalStateException("Challenge already validated.")
            }
        }
        val newDoc = submissionsRef.document()
        val submissionId = newDoc.id
        val extension = if (mediaType == SubmissionMediaType.VIDEO) "mp4" else "jpg"
        val contentType = if (mediaType == SubmissionMediaType.VIDEO) "video/mp4" else "image/jpeg"
        val storageRef = storage.reference.child("gameSubmissions/$gameId/$submissionId.$extension")
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType(contentType)
            .build()
        storageRef.putBytes(mediaBytes, metadata).await()
        val mediaUrl = storageRef.downloadUrl.await().toString()
        val submission = ChallengeSubmission(
            id = submissionId,
            challengeId = challengeId,
            hunterId = hunterId,
            type = type.firestoreValue,
            submittedAt = Timestamp.now(),
            mediaUrl = mediaUrl,
            mediaType = mediaType.firestoreValue,
            status = SubmissionStatus.PENDING.firestoreValue,
        )
        val data = mapOf(
            "challengeId" to submission.challengeId,
            "hunterId" to submission.hunterId,
            "type" to submission.type,
            "submittedAt" to submission.submittedAt,
            "mediaUrl" to submission.mediaUrl,
            "mediaType" to submission.mediaType,
            "status" to submission.status,
        )
        newDoc.set(data).await()
        return submission
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

    /** PP-52: outcome of [validateRegistrationCode]. Mirrors the server-side
     *  `{ status }` discriminated result and the iOS `ValidationCodeResult`. */
    enum class ValidationCodeResult { VALID, INVALID, ALREADY_USED, ERROR }

    /**
     * PP-52: validates the unique registration code for a paid-event game and
     * single-use-claims it server-side. Called from the JoinFlow only when the
     * resolved game has a `registrationBatchId`. Never reads `/eventRegistrations`
     * directly (rules lock it); the callable returns only a status, never PII.
     * Any thrown error (network, rate-limit lockout) maps to [ValidationCodeResult.ERROR].
     */
    suspend fun validateRegistrationCode(batchId: String, code: String): ValidationCodeResult {
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            try {
                val result = functions
                    .getHttpsCallable("validateRegistrationCode")
                    .call(mapOf("batchId" to batchId, "code" to code))
                    .await()
                @Suppress("UNCHECKED_CAST")
                val data = result.getData() as? Map<String, Any?> ?: emptyMap()
                when (data["status"] as? String) {
                    "valid" -> ValidationCodeResult.VALID
                    "alreadyUsed" -> ValidationCodeResult.ALREADY_USED
                    "invalid" -> ValidationCodeResult.INVALID
                    else -> ValidationCodeResult.ERROR
                }
            } catch (e: Exception) {
                Log.e(TAG, "validateRegistrationCode failed", e)
                ValidationCodeResult.ERROR
            }
        } ?: ValidationCodeResult.ERROR
    }

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
     * PP-86 / PP-107: GameMaster (or creator as fallback) designates a
     * hunter as the new chicken. Roles are server-owned now — the
     * `designateChicken` callable atomically moves the old chicken to
     * `hunter` and `newChickenUid` to `chicken` in the `roles` map (and
     * enforces `status == waiting` + caller-is-creator-or-GM server-side).
     */
    suspend fun designateChicken(gameId: String, newChickenUid: String) {
        functions
            .getHttpsCallable("designateChicken")
            .call(mapOf("gameId" to gameId, "newChickenUid" to newChickenUid))
            .await()
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

    /**
     * PP-71: promotes a `readyToLaunch` game to `inProgress`, stamps
     * `timing.actualStart` server-side, recomputes `timing.end`, and
     * enqueues the runtime Cloud Tasks deferred at creation. Throws if
     * the caller isn't the creator or a GameMaster, or if the game is
     * not in `readyToLaunch`.
     */
    suspend fun launchGame(gameId: String) {
        functions
            .getHttpsCallable("launchGame")
            .call(hashMapOf("gameId" to gameId))
            .await()
    }

    /**
     * QA-only (debug games): force-advance a phase via the `debugAdvanceGame`
     * callable. `action` is `"endNow"` or `"spawnPowerUp"`. The callable
     * refuses any game where `isDebugGame != true` server-side.
     */
    suspend fun debugAdvanceGame(gameId: String, action: String) {
        functions
            .getHttpsCallable("debugAdvanceGame")
            .call(hashMapOf("gameId" to gameId, "action" to action))
            .await()
    }
}
