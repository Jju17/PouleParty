package dev.rahier.pouleparty.data

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.model.Winner
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
        withRetry("registerHunter($gameId, $hunterId)") {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update("hunterIds", FieldValue.arrayUnion(hunterId))
                .await()
        }
    }

    suspend fun updateGameStatus(gameId: String, status: GameStatus) {
        try {
            firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
                .update("status", status.firestoreValue)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update game status $gameId → $status", e)
        }
    }

    // ── Chicken location ──────────────────────────────────

    fun setChickenLocation(gameId: String, latLng: LatLng) {
        val data = ChickenLocation(
            location = GeoPoint(latLng.latitude, latLng.longitude),
            timestamp = Timestamp.now()
        )
        firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS)
            .document()
            .set(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to set chicken location for game $gameId", e) }
    }

    fun chickenLocationFlow(gameId: String): Flow<LatLng?> = callbackFlow {
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
                        LatLng(
                            chickenLocation.location.latitude,
                            chickenLocation.location.longitude
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

    fun setHunterLocation(gameId: String, hunterId: String, latLng: LatLng) {
        val data = HunterLocation(
            hunterId = hunterId,
            location = GeoPoint(latLng.latitude, latLng.longitude),
            timestamp = Timestamp.now()
        )
        firestore.collection(AppConstants.COLLECTION_GAMES).document(gameId)
            .collection(AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS)
            .document(hunterId)
            .set(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to set hunter location $hunterId in game $gameId", e) }
    }

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
}
