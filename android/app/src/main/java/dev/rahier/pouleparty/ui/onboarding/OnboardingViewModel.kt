package dev.rahier.pouleparty.ui.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val nickname: String = "",
    val showLocationAlert: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun refreshPermissions() {
        _uiState.value = _uiState.value.copy(
            hasFineLocation = locationRepository.hasFineLocationPermission(),
            hasBackgroundLocation = locationRepository.hasBackgroundLocationPermission()
        )
    }

    fun nextPage() {
        val current = _uiState.value.currentPage
        // Block on location slide (page 3) if fine location not granted
        if (current == 3 && !_uiState.value.hasFineLocation) return
        // Block on nickname slide (page 4) if nickname is empty
        if (current == 4 && _uiState.value.nickname.trim().isEmpty()) return
        if (current < TOTAL_PAGES - 1) {
            _uiState.value = _uiState.value.copy(currentPage = current + 1)
        }
    }

    fun previousPage() {
        val current = _uiState.value.currentPage
        if (current > 0) {
            _uiState.value = _uiState.value.copy(currentPage = current - 1)
        }
    }

    fun setPage(page: Int) {
        _uiState.value = _uiState.value.copy(currentPage = page)
    }

    fun onNicknameChanged(name: String) {
        _uiState.value = _uiState.value.copy(nickname = name.take(NICKNAME_MAX_LENGTH))
    }

    fun dismissLocationAlert() {
        _uiState.value = _uiState.value.copy(showLocationAlert = false)
    }

    /**
     * Check location before completing onboarding.
     * Returns true if location is granted and onboarding can complete.
     */
    fun canCompleteOnboarding(): Boolean {
        return if (_uiState.value.hasFineLocation) {
            true
        } else {
            _uiState.value = _uiState.value.copy(showLocationAlert = true)
            false
        }
    }

    val isLastPage: Boolean
        get() = _uiState.value.currentPage == TOTAL_PAGES - 1

    companion object {
        const val TOTAL_PAGES = 6
        const val NICKNAME_MAX_LENGTH = 20
    }
}
