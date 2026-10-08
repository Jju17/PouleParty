package dev.rahier.pouleparty.data

import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.util.getTrimmedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push registration by Firebase installation id. The id can arrive before the
 * anonymous sign-in, so the last one is kept until a user exists to own it.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth,
    private val profiles: UserProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onRegistered(registrationId: String) {
        prefs.edit().putString(AppConstants.PREF_PUSH_REGISTRATION_ID, registrationId).apply()
        scope.launch { saveForCurrentUser() }
    }

    /** Called once the user is signed in; asks FCM to (re)register, which calls [onRegistered]. */
    suspend fun syncAfterSignIn(requestRegistration: suspend () -> Unit = { FirebaseMessaging.getInstance().register().await() }) {
        saveForCurrentUser()
        try {
            requestRegistration()
        } catch (e: Exception) {
            Log.w(TAG, "[push] registration request failed", e)
        }
    }

    internal suspend fun saveForCurrentUser() {
        val userId = auth.currentUser?.uid ?: return
        val registrationId = prefs.getString(AppConstants.PREF_PUSH_REGISTRATION_ID, null) ?: return
        try {
            profiles.savePushRegistration(userId, registrationId, prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME))
        } catch (e: Exception) {
            Log.w(TAG, "[push] registration save failed", e)
        }
    }

    private companion object {
        const val TAG = "PushRegistrar"
    }
}
