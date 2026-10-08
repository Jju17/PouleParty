package dev.rahier.pouleparty.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.rahier.pouleparty.AppConstants
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The player's `/users/{uid}` profile: nickname and push registration. */
interface UserProfileRepository {
    suspend fun saveNickname(userId: String, nickname: String)
    suspend fun savePushRegistration(userId: String, registrationId: String, nickname: String?)
    suspend fun deleteProfile(userId: String)
}

/** The fields written for a push registration; the server reads `token`. */
internal fun pushRegistrationFields(registrationId: String, nickname: String?): Map<String, Any> = buildMap {
    put("token", registrationId)
    put("platform", "android")
    put("updatedAt", FieldValue.serverTimestamp())
    nickname?.takeIf { it.isNotBlank() }?.let { put("nickname", it) }
}

@Singleton
class FirestoreUserProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserProfileRepository {

    private fun profile(userId: String) = firestore.collection(AppConstants.COLLECTION_USERS).document(userId)

    override suspend fun saveNickname(userId: String, nickname: String) {
        if (userId.isEmpty()) return
        withRetry("saveNickname") {
            profile(userId).set(mapOf("nickname" to nickname, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        }
    }

    override suspend fun savePushRegistration(userId: String, registrationId: String, nickname: String?) {
        if (userId.isEmpty() || registrationId.isEmpty()) return
        withRetry("savePushRegistration") {
            profile(userId).set(pushRegistrationFields(registrationId, nickname), SetOptions.merge()).await()
        }
    }

    override suspend fun deleteProfile(userId: String) {
        profile(userId).delete().await()
    }
}
