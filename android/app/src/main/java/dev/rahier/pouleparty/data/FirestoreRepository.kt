package dev.rahier.pouleparty.data

import android.util.Log
import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import java.util.Date
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.PartyPlansConfig
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.PlayerRole
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
                    delay(INITIAL_DELAY_MS * (1L shl attempt))
                }
            }
        }
        throw lastException!!
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
                    Log.w(TAG, "Chicken location listener error for game $gameId", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val doc = snapshot.documents.firstOrNull()
                val chickenLocation = doc?.toObject(ChickenLocation::class.java)
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
                    Log.w(TAG, "Game config listener error for game $gameId", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val game = snapshot.toObject(Game::class.java)?.copy(id = snapshot.id)
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

    suspend fun spawnPowerUps(gameId: String, powerUps: List<PowerUp>) {
        withRetry("spawnPowerUps($gameId, ${powerUps.size} items)") {
            val batch = firestore.batch()
            for (powerUp in powerUps) {
                val ref = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                    .collection(AppConstants.SUBCOLLECTION_POWER_UPS).document(powerUp.id)
                batch.set(ref, powerUp)
            }
            batch.commit().await()
        }
    }

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

    suspend fun activatePowerUp(gameId: String, powerUpId: String, expiresAt: Timestamp) {
        withRetry("activatePowerUp($gameId, $powerUpId)") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .collection(AppConstants.SUBCOLLECTION_POWER_UPS).document(powerUpId)
                .update(
                    mapOf(
                        "activatedAt" to Timestamp.now(),
                        "expiresAt" to expiresAt
                    )
                ).await()
        }
    }

    suspend fun updateGameActiveEffect(gameId: String, field: String, until: Timestamp) {
        withRetry("updateGameActiveEffect($gameId, $field)") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update(field, until)
                .await()
        }
    }

    fun powerUpsFlow(gameId: String): Flow<List<PowerUp>> = callbackFlow {
        val listener = firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_POWER_UPS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Power-ups listener error for game $gameId", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val powerUps = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(PowerUp::class.java)?.copy(id = doc.id)
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
                    Log.w(TAG, "Hunter locations listener error for game $gameId", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val hunters = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(HunterLocation::class.java)
                }
                trySend(hunters)
            }

        awaitClose { listener.remove() }
    }

    suspend fun countFreeGamesToday(userId: String): Int {
        return try {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startOfDay = Timestamp(calendar.time)

            val snapshot = firestore.collection(AppConstants.COLLECTION_GAMES)
                .whereEqualTo("creatorId", userId)
                .whereEqualTo("pricingModel", "free")
                .whereGreaterThanOrEqualTo("startTimestamp", startOfDay)
                .get()
                .await()

            snapshot.documents.size
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count free games today for $userId", e)
            0
        }
    }

    suspend fun fetchPartyPlansConfig(): PartyPlansConfig {
        val doc = firestore.collection("config")
            .document("partyPlans")
            .get()
            .await()
        return doc.toObject(PartyPlansConfig::class.java)
            ?: throw IllegalStateException("Party plans config document is missing or malformed")
    }
}
