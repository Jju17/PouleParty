package dev.rahier.pouleparty.data

import com.google.firebase.firestore.DocumentReference
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeCompletion
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GamePhase
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.MyGame
import dev.rahier.pouleparty.model.MyGameRole
import dev.rahier.pouleparty.model.PlayerRole
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.model.SubmissionStatus
import dev.rahier.pouleparty.model.ZoneCircle
import dev.rahier.pouleparty.powerups.model.PowerUp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveGameResult(
    val game: Game,
    val role: PlayerRole,
    val phase: GamePhase,
)

/**
 * Firestore documents and streams of a game. Reads throw on failure so the UI
 * can show an error instead of an empty state; `null` only means "absent".
 * Streams resubscribe after a listener error and keep their last value.
 */
interface GameRepository {
    fun newGameId(): String
    suspend fun getConfig(gameId: String): Game?
    suspend fun fetchZoneSchedule(gameId: String): List<ZoneCircle>
    suspend fun findActiveGame(userId: String): ActiveGameResult?
    suspend fun findGameByCode(code: String): Game?
    suspend fun setConfig(game: Game)
    suspend fun updateGameStatus(gameId: String, status: GameStatus)
    fun gameConfigFlow(gameId: String): Flow<Game?>
    fun powerUpsFlow(gameId: String): Flow<List<PowerUp>>
    suspend fun fetchMyGames(userId: String): List<MyGame>
    suspend fun findRegistration(gameId: String, userId: String): Registration?
    suspend fun fetchAllRegistrations(gameId: String): List<Registration>
    fun registrationsFlow(gameId: String): Flow<List<Registration>>
    fun challengesStream(gameId: String): Flow<List<Challenge>>
    fun leaderboardFlow(gameId: String): Flow<List<ChallengeCompletion>>
    fun myCompletionFlow(gameId: String, hunterId: String): Flow<ChallengeCompletion?>
    fun pendingSubmissionsFlow(gameId: String): Flow<List<ChallengeSubmission>>
    fun hunterSubmissionsFlow(gameId: String, hunterId: String): Flow<List<ChallengeSubmission>>
    suspend fun reportPlayer(reporterId: String, reportedUserId: String, reportedNickname: String, gameId: String)
}

/** Sorting shared by every leaderboard: points, then team name, then id. */
val leaderboardOrder: Comparator<ChallengeCompletion> =
    compareByDescending<ChallengeCompletion> { it.totalPoints }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.teamName }
        .thenBy { it.hunterId }

/** Picks the game the Home banner should surface among the user's games. */
fun selectActiveGame(candidates: List<Pair<Game, PlayerRole>>, now: Date): ActiveGameResult? {
    candidates
        .filter { it.first.gameStatusEnum == GameStatus.IN_PROGRESS && it.first.endDate.after(now) }
        .maxByOrNull { it.first.startDate.time }
        ?.let { return ActiveGameResult(it.first, it.second, GamePhase.IN_PROGRESS) }
    candidates
        .filter { it.first.gameStatusEnum == GameStatus.WAITING && it.first.startDate.after(now) }
        .minByOrNull { it.first.startDate.time }
        ?.let { return ActiveGameResult(it.first, it.second, GamePhase.UPCOMING) }
    return null
}

fun roleOf(game: Game, userId: String): PlayerRole? = when {
    game.isChicken(userId) -> PlayerRole.CHICKEN
    game.isGameMaster(userId) -> PlayerRole.GAME_MASTER
    game.isHunter(userId) -> PlayerRole.HUNTER
    else -> null
}

/** Decodes the stored zone circles; malformed entries are skipped. */
fun decodeZoneCircles(raw: Any?): List<ZoneCircle> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { entry ->
        val m = entry as? Map<*, *> ?: return@mapNotNull null
        val radius = (m["radiusMeters"] as? Number)?.toDouble() ?: return@mapNotNull null
        val lat = (m["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
        val lng = (m["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
        ZoneCircle(order = (m["order"] as? Number)?.toInt() ?: 0, radiusMeters = radius, lat = lat, lng = lng)
    }.sortedBy { it.order }
}

/** Decodes the leaderboard read-model document. */
fun decodeLeaderboard(raw: Any?): List<ChallengeCompletion> {
    val entries = raw as? Map<*, *> ?: return emptyList()
    return entries.mapNotNull { (hunterId, entry) ->
        val m = entry as? Map<*, *> ?: return@mapNotNull null
        ChallengeCompletion(
            hunterId = hunterId as? String ?: return@mapNotNull null,
            totalPoints = (m["totalPoints"] as? Number)?.toInt() ?: 0,
            teamName = m["teamName"] as? String ?: "",
        )
    }.sortedWith(leaderboardOrder)
}

@Singleton
class FirestoreGameRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : GameRepository {

    private fun games() = firestore.collection(AppConstants.COLLECTION_GAMES)
    private fun game(gameId: String) = games().document(gameId)

    private suspend fun getDocument(ref: DocumentReference): DocumentSnapshot =
        withTimeout(READ_TIMEOUT_MS) { ref.get().await() }

    override fun newGameId(): String = games().document().id

    override suspend fun getConfig(gameId: String): Game? {
        val doc = getDocument(game(gameId))
        if (!doc.exists()) return null
        return safeToObject<Game>(doc, "getConfig($gameId)")?.copy(id = doc.id)
    }

    override suspend fun fetchZoneSchedule(gameId: String): List<ZoneCircle> {
        val doc = getDocument(game(gameId).collection("zone").document("schedule"))
        return decodeZoneCircles(doc.get("circles"))
    }

    override suspend fun findActiveGame(userId: String): ActiveGameResult? {
        if (userId.isEmpty()) return null
        val memberships = withTimeout(READ_TIMEOUT_MS) {
            firestore.collection(AppConstants.COLLECTION_USERS).document(userId)
                .collection(AppConstants.SUBCOLLECTION_MEMBERSHIPS).get().await()
        }
        val gameIds = memberships.documents.mapNotNull { it.getString("gameId") ?: it.id.ifEmpty { null } }.toSet()
        val games = coroutineScope {
            gameIds.map { id -> async { runCatching { getConfig(id) }.onFailure { Log.w(DATA_TAG, "findActiveGame: $id unreadable", it) }.getOrNull() } }.awaitAll()
        }.filterNotNull()
        val candidates = games.mapNotNull { g -> roleOf(g, userId)?.let { g to it } }
        return selectActiveGame(candidates, Date())
    }

    override suspend fun findGameByCode(code: String): Game? {
        val normalized = code.trim().uppercase()
        val index = getDocument(firestore.collection(AppConstants.COLLECTION_GAME_CODES).document(normalized))
        val indexedGameId = index.getString("gameId")
        if (indexedGameId != null) return getConfig(indexedGameId)
        // Games created before the code index existed.
        val snapshot = withTimeout(READ_TIMEOUT_MS) {
            games().whereEqualTo("gameCode", normalized).limit(1).get().await()
        }
        return snapshot.documents.firstOrNull()?.let { doc -> safeToObject<Game>(doc, "findGameByCode")?.copy(id = doc.id) }
    }

    override suspend fun setConfig(game: Game) {
        withRetry("setConfig(${game.id})") { game(game.id).set(game).await() }
    }

    override suspend fun updateGameStatus(gameId: String, status: GameStatus) {
        withRetry("updateGameStatus($gameId, $status)") {
            game(gameId).update("status", status.firestoreValue).await()
        }
    }

    override fun gameConfigFlow(gameId: String): Flow<Game?> = callbackFlow {
        val listener = game(gameId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                logListenerError("Game config ($gameId)", error)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            safeToObject<Game>(snapshot, "Game config ($gameId)")?.let { trySend(it.copy(id = snapshot.id)) }
        }
        awaitClose { listener.remove() }
    }.resubscribeOnError()

    override fun powerUpsFlow(gameId: String): Flow<List<PowerUp>> = collectionFlow(
        "Power-ups ($gameId)", game(gameId).collection(AppConstants.SUBCOLLECTION_POWER_UPS),
    ) { doc -> safeToObject<PowerUp>(doc, "Power-ups ($gameId)")?.copy(id = doc.id) }

    override suspend fun fetchMyGames(userId: String): List<MyGame> {
        val (created, memberships) = coroutineScope {
            val createdTask = async {
                withTimeout(READ_TIMEOUT_MS) { games().whereEqualTo("creatorId", userId).limit(30).get().await() }
            }
            val membershipTask = async {
                withTimeout(READ_TIMEOUT_MS) {
                    firestore.collection(AppConstants.COLLECTION_USERS).document(userId)
                        .collection(AppConstants.SUBCOLLECTION_MEMBERSHIPS).limit(30).get().await()
                }
            }
            createdTask.await() to membershipTask.await()
        }
        val result = mutableListOf<MyGame>()
        val seen = mutableSetOf<String>()
        created.documents.forEach { doc ->
            val g = safeToObject<Game>(doc, "fetchMyGames created")?.copy(id = doc.id) ?: return@forEach
            if (seen.add(g.id)) result.add(MyGame(g, MyGameRole.CREATOR))
        }
        val joinedIds = memberships.documents.mapNotNull { it.getString("gameId") ?: it.id.ifEmpty { null } }
            .toSet().filter { it !in seen }
        coroutineScope {
            joinedIds.map { id -> async { runCatching { getConfig(id) }.onFailure { Log.w(DATA_TAG, "fetchMyGames: $id unreadable", it) }.getOrNull() } }.awaitAll()
        }.filterNotNull().forEach { g -> if (seen.add(g.id)) result.add(MyGame(g, MyGameRole.HUNTER)) }
        return result.sortedByDescending { it.game.startDate.time }.take(20)
    }

    override suspend fun findRegistration(gameId: String, userId: String): Registration? {
        if (gameId.isEmpty() || userId.isEmpty()) return null
        val doc = getDocument(game(gameId).collection(AppConstants.SUBCOLLECTION_PLAYERS).document(userId))
        if (!doc.exists()) return null
        return safeToObject<Registration>(doc, "findRegistration")?.copy(userId = doc.id)
    }

    override suspend fun fetchAllRegistrations(gameId: String): List<Registration> {
        if (gameId.isEmpty()) return emptyList()
        val snapshot = withTimeout(READ_TIMEOUT_MS) {
            game(gameId).collection(AppConstants.SUBCOLLECTION_PLAYERS).get().await()
        }
        return snapshot.documents.mapNotNull { safeToObject<Registration>(it, "fetchAllRegistrations")?.copy(userId = it.id) }
    }

    override fun registrationsFlow(gameId: String): Flow<List<Registration>> = collectionFlow(
        "Players ($gameId)", game(gameId).collection(AppConstants.SUBCOLLECTION_PLAYERS),
    ) { doc -> safeToObject<Registration>(doc, "Players ($gameId)")?.copy(userId = doc.id) }

    override fun challengesStream(gameId: String): Flow<List<Challenge>> = collectionFlow(
        "Challenges ($gameId)", game(gameId).collection(AppConstants.COLLECTION_CHALLENGES),
    ) { doc -> safeToObject<Challenge>(doc, "Challenges ($gameId)")?.copy(id = doc.id) }

    override fun leaderboardFlow(gameId: String): Flow<List<ChallengeCompletion>> = callbackFlow {
        val listener = game(gameId).collection("aggregates").document("leaderboard")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("Leaderboard ($gameId)", error)
                    close(error)
                    return@addSnapshotListener
                }
                trySend(decodeLeaderboard(snapshot?.get("entries")))
            }
        awaitClose { listener.remove() }
    }.resubscribeOnError()

    override fun myCompletionFlow(gameId: String, hunterId: String): Flow<ChallengeCompletion?> = callbackFlow {
        val listener = game(gameId).collection(AppConstants.SUBCOLLECTION_CHALLENGE_COMPLETIONS).document(hunterId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logListenerError("My completion ($gameId)", error)
                    close(error)
                    return@addSnapshotListener
                }
                val completion = if (snapshot != null && snapshot.exists()) {
                    safeToObject<ChallengeCompletion>(snapshot, "My completion")?.copy(hunterId = snapshot.id)
                } else null
                trySend(completion)
            }
        awaitClose { listener.remove() }
    }.resubscribeOnError()

    override fun pendingSubmissionsFlow(gameId: String): Flow<List<ChallengeSubmission>> = collectionFlow(
        "Pending submissions ($gameId)",
        game(gameId).collection(AppConstants.SUBCOLLECTION_CHALLENGE_SUBMISSIONS)
            .whereEqualTo("status", SubmissionStatus.PENDING.firestoreValue)
            .orderBy("submittedAt", Query.Direction.ASCENDING),
    ) { doc -> safeToObject<ChallengeSubmission>(doc, "Pending submissions")?.copy(id = doc.id) }

    override fun hunterSubmissionsFlow(gameId: String, hunterId: String): Flow<List<ChallengeSubmission>> = collectionFlow(
        "Hunter submissions ($gameId)",
        game(gameId).collection(AppConstants.SUBCOLLECTION_CHALLENGE_SUBMISSIONS).whereEqualTo("hunterId", hunterId),
    ) { doc -> safeToObject<ChallengeSubmission>(doc, "Hunter submissions")?.copy(id = doc.id) }

    override suspend fun reportPlayer(reporterId: String, reportedUserId: String, reportedNickname: String, gameId: String) {
        firestore.collection(AppConstants.COLLECTION_REPORTS).add(
            mapOf(
                "reporterId" to reporterId,
                "reportedUserId" to reportedUserId,
                "reportedNickname" to reportedNickname.take(60),
                "gameId" to gameId,
                "createdAt" to Timestamp.now(),
            ),
        ).await()
    }

    private fun <T> collectionFlow(
        operation: String,
        query: Query,
        decode: (DocumentSnapshot) -> T?,
    ): Flow<List<T>> = callbackFlow {
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                logListenerError(operation, error)
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull(decode) ?: emptyList())
        }
        awaitClose { listener.remove() }
    }.resubscribeOnError()
}
