package dev.rahier.pouleparty.navigation

import androidx.core.content.edit
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.PushRegistrar
import dev.rahier.pouleparty.data.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "AppSession"

data class AppSessionState(
    val isReady: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
)

/** Anonymous sign-in, push registration and the first-run profile write, out of the navigation graph. */
@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val prefs: SharedPreferences,
    private val profiles: UserProfileRepository,
    private val pushRegistrar: PushRegistrar,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppSessionState(
            isReady = auth.currentUser != null,
            hasCompletedOnboarding = prefs.getBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, false),
        ),
    )
    val state: StateFlow<AppSessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val isNewUser = signInIfNeeded()
            _state.update { it.copy(isReady = true, hasCompletedOnboarding = it.hasCompletedOnboarding && !isNewUser) }
            pushRegistrar.syncAfterSignIn()
        }
    }

    fun onOnboardingCompleted(nickname: String) {
        prefs.edit { putBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, true); putString(AppConstants.PREF_USER_NICKNAME, nickname) }
        _state.value = _state.value.copy(hasCompletedOnboarding = true)
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                profiles.saveNickname(userId, nickname)
            } catch (e: Exception) {
                Log.w(TAG, "[profile] nickname sync failed", e)
            }
        }
    }

    /** True when a fresh uid was created: it has no profile, so the nickname must be entered again. */
    private suspend fun signInIfNeeded(): Boolean {
        if (auth.currentUser != null) return false
        return try {
            val isNewUser = auth.signInAnonymously().await().additionalUserInfo?.isNewUser == true
            if (isNewUser) prefs.edit { putBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, false) }
            isNewUser
        } catch (e: Exception) {
            Log.e(TAG, "[auth] anonymous sign-in failed", e)
            false
        }
    }
}
