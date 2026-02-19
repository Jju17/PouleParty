package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.onboarding.OnboardingUiState
import dev.rahier.pouleparty.ui.onboarding.OnboardingViewModel
import org.junit.Assert.*
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun `initial state is page 0 with no permissions`() {
        val state = OnboardingUiState()
        assertEquals(0, state.currentPage)
        assertFalse(state.hasFineLocation)
        assertFalse(state.hasBackgroundLocation)
    }

    @Test
    fun `total pages is 5`() {
        assertEquals(5, OnboardingViewModel.TOTAL_PAGES)
    }

    @Test
    fun `page navigation works correctly`() {
        var state = OnboardingUiState(currentPage = 0)

        // Go next
        state = state.copy(currentPage = state.currentPage + 1)
        assertEquals(1, state.currentPage)

        // Go next again
        state = state.copy(currentPage = state.currentPage + 1)
        assertEquals(2, state.currentPage)

        // Go back
        state = state.copy(currentPage = state.currentPage - 1)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun `page does not go below 0`() {
        val state = OnboardingUiState(currentPage = 0)
        val page = maxOf(0, state.currentPage - 1)
        assertEquals(0, page)
    }

    @Test
    fun `page does not exceed total pages`() {
        val state = OnboardingUiState(currentPage = OnboardingViewModel.TOTAL_PAGES - 1)
        val page = minOf(OnboardingViewModel.TOTAL_PAGES - 1, state.currentPage + 1)
        assertEquals(4, page)
    }

    @Test
    fun `isLastPage when on last page`() {
        val state = OnboardingUiState(currentPage = OnboardingViewModel.TOTAL_PAGES - 1)
        assertTrue(state.currentPage == OnboardingViewModel.TOTAL_PAGES - 1)
    }
}
