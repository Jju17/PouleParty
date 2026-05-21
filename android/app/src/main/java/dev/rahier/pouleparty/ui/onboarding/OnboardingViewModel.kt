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
        if (current == 3 && !_uiState.value.hasFineLocation) return
        if (current == 5) {
            val trimmed = _uiState.value.nickname.trim()
            if (trimmed.isEmpty()) return
            if (ProfanityFilter.containsProfanity(trimmed)) {
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

    fun canCompleteOnboarding(): Boolean {
        if (!_uiState.value.hasFineLocation) {
            _uiState.update { it.copy(showLocationAlert = true) }
            return false
        }
        val trimmed = _uiState.value.nickname.trim()
        if (trimmed.isEmpty()) {
            // Silently refuse, the "Let's Go" button is already gated on
            // the nickname page, so this branch only fires if the UI state
            // drifted out of sync. No alert needed; the user will see the
            // nickname field empty and try again.
            return false
        }
        if (ProfanityFilter.containsProfanity(trimmed)) {
            _uiState.update { it.copy(showProfanityAlert = true) }
            return false
        }
        return true
    }

    val isLastPage: Boolean
        get() = _uiState.value.currentPage == TOTAL_PAGES - 1

    companion object {
        const val TOTAL_PAGES = 7
        const val NICKNAME_MAX_LENGTH = 20
    }
}
