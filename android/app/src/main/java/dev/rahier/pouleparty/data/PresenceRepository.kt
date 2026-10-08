package dev.rahier.pouleparty.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.HunterLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Realtime Database positions and chicken presence. */
interface PresenceRepository {
    fun setChickenLocation(gameId: String, point: Point, invisible: Boolean = false)
    fun chickenLocationFlow(gameId: String): Flow<ChickenLocation?>
    fun setHunterLocation(gameId: String, hunterId: String, point: Point)
    fun hunterLocationsFlow(gameId: String): Flow<List<HunterLocation>>
    suspend fun updateHeartbeat(gameId: String)
}

/** A radar ping grants the read for a short window, so retry quickly. */
private const val PRESENCE_RESUBSCRIBE_CAP_MS = 5_000L

@Singleton
class RealtimePresenceRepository @Inject constructor(
    private val database: FirebaseDatabase,
) : PresenceRepository {

    private fun node(gameId: String, path: String): DatabaseReference =
        database.getReference("${AppConstants.COLLECTION_GAMES}/$gameId/$path")

    override fun setChickenLocation(gameId: String, point: Point, invisible: Boolean) {
        node(gameId, "${AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS}/latest").setValue(
            mapOf(
                "lat" to point.latitude(),
                "lng" to point.longitude(),
                "ts" to ServerValue.TIMESTAMP,
                "invisible" to invisible,
            ),
        ).addOnFailureListener { Log.w(DATA_TAG, "[presence] chicken location write failed for $gameId", it) }
    }

    override fun chickenLocationFlow(gameId: String): Flow<ChickenLocation?> = valueFlow(
        "Chicken location ($gameId)",
        node(gameId, "${AppConstants.SUBCOLLECTION_CHICKEN_LOCATIONS}/latest"),
        whenDenied = null,
    ) { snapshot -> if (snapshot.exists()) ChickenLocation.fromRtdb(snapshot) else null }

    override fun setHunterLocation(gameId: String, hunterId: String, point: Point) {
        if (gameId.isEmpty() || hunterId.isEmpty()) return
        node(gameId, "${AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS}/$hunterId").setValue(
            mapOf("lat" to point.latitude(), "lng" to point.longitude(), "ts" to ServerValue.TIMESTAMP),
        ).addOnFailureListener { Log.w(DATA_TAG, "[presence] hunter location write failed for $gameId", it) }
    }

    override fun hunterLocationsFlow(gameId: String): Flow<List<HunterLocation>> = valueFlow(
        "Hunter locations ($gameId)",
        node(gameId, AppConstants.SUBCOLLECTION_HUNTER_LOCATIONS),
        whenDenied = emptyList(),
    ) { snapshot -> snapshot.children.mapNotNull { child -> child.key?.let { HunterLocation.fromRtdb(it, child) } } }

    override suspend fun updateHeartbeat(gameId: String) {
        withRetry("updateHeartbeat($gameId)") {
            val ref = node(gameId, "presence/chicken")
            ref.onDisconnect().setValue(mapOf("online" to false, "ts" to ServerValue.TIMESTAMP)).await()
            ref.setValue(mapOf("online" to true, "ts" to ServerValue.TIMESTAMP)).await()
        }
    }

    /**
     * The rules grant these reads only while the position is visible to the
     * caller (radar ping, invisibility), so a revoked read hides the marker
     * through [whenDenied] and the subscription is retried.
     */
    private fun <T> valueFlow(
        operation: String,
        ref: DatabaseReference,
        whenDenied: T,
        decode: (DataSnapshot) -> T,
    ): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(decode(snapshot))
            }

            override fun onCancelled(error: DatabaseError) {
                logListenerError(operation, error.toException())
                trySend(whenDenied)
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.resubscribeOnError(PRESENCE_RESUBSCRIBE_CAP_MS)
}
