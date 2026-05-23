package dev.rahier.pouleparty.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.util.ProfanityFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val nickname: String = "",
    val showLocationAlert: Boolean = false,
    val showProfanityAlert: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Seed permission flags synchronously before any composable reads
        // the state, otherwise the first frame renders the "Allow Location
        // Access" button even when the user already granted Always in a
        // previous install, and they have to tap through a no-op dialog.
        // The `LaunchedEffect(Unit)` in the screen still runs and covers
        // the freshly-installed case.
        refreshPermissions()
    }

    /** Single entry point for every user interaction. */
    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.NextPage -> nextPage()
            OnboardingIntent.PreviousPage -> previousPage()
            OnboardingIntent.DismissLocationAlert -> dismissLocationAlert()
            OnboardingIntent.DismissProfanityAlert -> dismissProfanityAlert()
            OnboardingIntent.RefreshPermissions -> refreshPermissions()
            OnboardingIntent.RefreshNotificationPermission -> refreshNotificationPermission()
            OnboardingIntent.OnboardingCompletedLogged -> logOnboardingCompleted()
            is OnboardingIntent.PageSet -> setPage(intent.page)
            is OnboardingIntent.NicknameChanged -> onNicknameChanged(intent.name)
        }
    }

    private fun logOnboardingCompleted() {
        analyticsRepository.onboardingCompleted()
    }

    private fun refreshPermissions() {
        _uiState.update {
            it.copy(
                hasFineLocation = locationRepository.hasFineLocationPermission(),
                hasBackgroundLocation = locationRepository.hasBackgroundLocationPermission(),
                hasNotificationPermission = hasNotificationPermission()
            )
        }
    }

    private fun refreshNotificationPermission() {
        _uiState.update {
            it.copy(hasNotificationPermission = hasNotificationPermission())
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Below Android 13, notifications are enabled by default
        }
    }

    private fun nextPage() {
        val current = _uiState.value.currentPage
        // Apple 5.1.5 parity: every slide is skippable. Location is
        // requested contextually at Create / Join / Start; an empty
        // nickname is auto-generated in `canCompleteOnboarding`. The
        // only remaining gate is profanity on a manually-typed nickname.
        if (current == 5) {
            val trimmed = _uiState.value.nickname.trim()
            if (trimmed.isNotEmpty() && ProfanityFilter.containsProfanity(trimmed)) {
                _uiState.update { it.copy(showProfanityAlert = true) }
                return
            }
        }
        if (current < TOTAL_PAGES - 1) {
            _uiState.update { it.copy(currentPage = current + 1) }
        }
    }

    private fun previousPage() {
        val current = _uiState.value.currentPage
        if (current > 0) {
            _uiState.update { it.copy(currentPage = current - 1) }
        }
    }

    private fun setPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }

    private fun onNicknameChanged(name: String) {
        _uiState.update { it.copy(nickname = name.take(NICKNAME_MAX_LENGTH)) }
    }

    private fun dismissLocationAlert() {
        _uiState.update { it.copy(showLocationAlert = false) }
    }

    private fun dismissProfanityAlert() {
        _uiState.update { it.copy(showProfanityAlert = false) }
    }

    /**
     * Returns the final nickname to persist when the user taps "Let's Go".
     * Returns null if the typed nickname triggers the profanity filter (the
     * caller stays on the screen so the user can fix it). An empty nickname
     * is auto-generated via [RandomNickname] — Apple 5.1.5 makes every
     * slide skippable, and the player always needs a teamName.
     */
    fun resolveFinalNickname(): String? {
        val trimmed = _uiState.value.nickname.trim()
        if (trimmed.isNotEmpty() && ProfanityFilter.containsProfanity(trimmed)) {
            _uiState.update { it.copy(showProfanityAlert = true) }
            return null
        }
        return if (trimmed.isEmpty()) dev.rahier.pouleparty.util.RandomNickname.generate() else trimmed
    }

    val isLastPage: Boolean
        get() = _uiState.value.currentPage == TOTAL_PAGES - 1

    companion object {
        const val TOTAL_PAGES = 7
        const val NICKNAME_MAX_LENGTH = 20
    }
}
