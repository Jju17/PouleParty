package dev.rahier.pouleparty.data

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.model.Winner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // ── CRUD ──────────────────────────────────────────────

    suspend fun deleteConfig(gameId: String) {
        firestore.collection("games").document(gameId).delete().await()
    }

    suspend fun getConfig(gameId: String): Game? {
        val doc = firestore.collection("games").document(gameId).get().await()
        return doc.toObject(Game::class.java)?.copy(id = doc.id)
    }

    suspend fun findGameByCode(code: String): Game? {
        val snapshot = firestore.collection("games").get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Game::class.java)?.copy(id = doc.id)
        }.firstOrNull { game ->
            game.gameCode == code.uppercase()
        }
    }

    fun setConfig(game: Game) {
        firestore.collection("games").document(game.id).set(game)
    }

    suspend fun addWinner(gameId: String, winner: Winner) {
        val winnerMap = mapOf(
            "hunterId" to winner.hunterId,
            "hunterName" to winner.hunterName,
            "timestamp" to winner.timestamp
        )
        firestore.collection("games").document(gameId)
            .update("winners", FieldValue.arrayUnion(winnerMap))
            .await()
    }

    // ── Chicken location ──────────────────────────────────

    fun setChickenLocation(gameId: String, latLng: LatLng) {
        val data = ChickenLocation(
            location = GeoPoint(latLng.latitude, latLng.longitude),
            timestamp = Timestamp.now()
        )
        firestore.collection("games").document(gameId)
            .collection("chickenLocations")
            .document()
            .set(data)
    }

    fun chickenLocationFlow(gameId: String): Flow<LatLng?> = callbackFlow {
        val listener = firestore.collection("games").document(gameId)
            .collection("chickenLocations")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
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
        val listener = firestore.collection("games").document(gameId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
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
        firestore.collection("games").document(gameId)
            .collection("hunterLocations")
            .document(hunterId)
            .set(data)
    }

    fun hunterLocationsFlow(gameId: String): Flow<List<HunterLocation>> = callbackFlow {
        val listener = firestore.collection("games").document(gameId)
            .collection("hunterLocations")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
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
