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
    val hasBackgroundLocation: Boolean = false
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

    val isLastPage: Boolean
        get() = _uiState.value.currentPage == TOTAL_PAGES - 1

    companion object {
        const val TOTAL_PAGES = 5
    }
}
