package dev.rahier.pouleparty.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.ChallengeType
import dev.rahier.pouleparty.model.SubmissionMediaType
import dev.rahier.pouleparty.model.SubmissionStatus
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class SubmissionRejectedException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { ALREADY_PENDING, ALREADY_VALIDATED }
}

/** Uploads a proof and creates the pending submission the validators review. */
interface ChallengeSubmissionRepository {
    suspend fun submitChallenge(
        gameId: String,
        challengeId: String,
        hunterId: String,
        type: ChallengeType,
        mediaBytes: ByteArray,
        mediaType: SubmissionMediaType,
    ): ChallengeSubmission
}

/** Refuses a duplicate before anything is uploaded. */
internal fun blockingSubmission(existing: List<ChallengeSubmission>, type: ChallengeType): SubmissionRejectedException.Reason? {
    if (existing.any { it.statusEnum == SubmissionStatus.PENDING }) return SubmissionRejectedException.Reason.ALREADY_PENDING
    if (type == ChallengeType.ONE_SHOT && existing.any { it.statusEnum == SubmissionStatus.VALIDATED }) {
        return SubmissionRejectedException.Reason.ALREADY_VALIDATED
    }
    return null
}

@Singleton
class FirebaseChallengeSubmissionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : ChallengeSubmissionRepository {

    override suspend fun submitChallenge(
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
            .documents
            .mapNotNull { safeToObject<ChallengeSubmission>(it, "submitChallenge($gameId)") }
        blockingSubmission(existing, type)?.let { throw SubmissionRejectedException(it) }

        val newDoc = submissionsRef.document()
        val isVideo = mediaType == SubmissionMediaType.VIDEO
        val storageRef = storage.reference.child("gameSubmissions/$gameId/${newDoc.id}.${if (isVideo) "mp4" else "jpg"}")
        val metadata = StorageMetadata.Builder().setContentType(if (isVideo) "video/mp4" else "image/jpeg").build()
        storageRef.putBytes(mediaBytes, metadata).await()
        val mediaUrl = storageRef.downloadUrl.await().toString()
        val submission = ChallengeSubmission(
            id = newDoc.id,
            challengeId = challengeId,
            hunterId = hunterId,
            type = type.firestoreValue,
            submittedAt = Timestamp.now(),
            mediaUrl = mediaUrl,
            mediaType = mediaType.firestoreValue,
            status = SubmissionStatus.PENDING.firestoreValue,
        )
        newDoc.set(
            mapOf(
                "challengeId" to submission.challengeId,
                "hunterId" to submission.hunterId,
                "type" to submission.type,
                "submittedAt" to submission.submittedAt,
                "mediaUrl" to submission.mediaUrl,
                "mediaType" to submission.mediaType,
                "status" to submission.status,
            ),
        ).await()
        return submission
    }
}
