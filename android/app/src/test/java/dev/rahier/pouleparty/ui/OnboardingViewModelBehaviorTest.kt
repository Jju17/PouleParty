package dev.rahier.pouleparty.ui

import android.content.Context
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.onboarding.OnboardingIntent
import dev.rahier.pouleparty.ui.onboarding.OnboardingViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behaviour tests for the MVI surface of [OnboardingViewModel] — verify
 * each [OnboardingIntent] mutates the state as expected.
 */
class OnboardingViewModelBehaviorTest {

    private lateinit var locationRepository: LocationRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        locationRepository = mockk(relaxed = true)
        analyticsRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
    }

    private fun createViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            locationRepository = locationRepository,
            analyticsRepository = analyticsRepository,
            context = context
        )
    }

    @Test
    fun `NicknameChanged truncates to NICKNAME_MAX_LENGTH`() {
        val vm = createViewModel()
        val long = "a".repeat(50)
        vm.onIntent(OnboardingIntent.NicknameChanged(long))
        assertEquals(OnboardingViewModel.NICKNAME_MAX_LENGTH, vm.uiState.value.nickname.length)
    }

    @Test
    fun `PageSet jumps directly to that page`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(4))
        assertEquals(4, vm.uiState.value.currentPage)
    }

    @Test
    fun `PreviousPage decrements current page`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(3))
        vm.onIntent(OnboardingIntent.PreviousPage)
        assertEquals(2, vm.uiState.value.currentPage)
    }

    @Test
    fun `PreviousPage on page 0 does not go below 0`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PreviousPage)
        assertEquals(0, vm.uiState.value.currentPage)
    }

    @Test
    fun `NextPage on location slide blocked when permission missing`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.RefreshPermissions)
        vm.onIntent(OnboardingIntent.PageSet(3))
        vm.onIntent(OnboardingIntent.NextPage)
        // Should still be on page 3 because location is not granted
        assertEquals(3, vm.uiState.value.currentPage)
    }

    @Test
    fun `NextPage on location slide proceeds when permission granted`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.RefreshPermissions)
        vm.onIntent(OnboardingIntent.PageSet(3))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(4, vm.uiState.value.currentPage)
    }

    @Test
    fun `NextPage on nickname slide blocked when nickname empty`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(5))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(5, vm.uiState.value.currentPage)
    }

    @Test
    fun `NextPage on nickname slide blocked on profanity`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(5))
        vm.onIntent(OnboardingIntent.NicknameChanged("fuck"))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(5, vm.uiState.value.currentPage)
        assertTrue(vm.uiState.value.showProfanityAlert)
    }

    @Test
    fun `NextPage on nickname slide proceeds with valid nickname`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(5))
        vm.onIntent(OnboardingIntent.NicknameChanged("Julien"))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(6, vm.uiState.value.currentPage)
    }

    @Test
    fun `NextPage capped at last page`() {
        val vm = createViewModel()
        every { locationRepository.hasFineLocationPermission() } returns true
        vm.onIntent(OnboardingIntent.PageSet(OnboardingViewModel.TOTAL_PAGES - 1))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(OnboardingViewModel.TOTAL_PAGES - 1, vm.uiState.value.currentPage)
    }

    @Test
    fun `DismissLocationAlert hides the alert`() {
        val vm = createViewModel()
        // Force the alert to show via canCompleteOnboarding (no permission)
        every { locationRepository.hasFineLocationPermission() } returns false
        vm.canCompleteOnboarding()
        assertTrue(vm.uiState.value.showLocationAlert)
        vm.onIntent(OnboardingIntent.DismissLocationAlert)
        assertFalse(vm.uiState.value.showLocationAlert)
    }

    @Test
    fun `DismissProfanityAlert hides the alert`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(5))
        vm.onIntent(OnboardingIntent.NicknameChanged("fuck"))
        vm.onIntent(OnboardingIntent.NextPage)
        assertTrue(vm.uiState.value.showProfanityAlert)
        vm.onIntent(OnboardingIntent.DismissProfanityAlert)
        assertFalse(vm.uiState.value.showProfanityAlert)
    }

    @Test
    fun `OnboardingCompletedLogged forwards to analytics`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.OnboardingCompletedLogged)
        verify { analyticsRepository.onboardingCompleted() }
    }

    @Test
    fun `RefreshPermissions reads location and notification status`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        every { locationRepository.hasBackgroundLocationPermission() } returns false
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.RefreshPermissions)
        assertTrue(vm.uiState.value.hasFineLocation)
        assertFalse(vm.uiState.value.hasBackgroundLocation)
    }

    // ── Edge cases ─────────────────────────────────────────

    @Test
    fun `NicknameChanged with whitespace-only value blocks NextPage on slide 5`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(5))
        vm.onIntent(OnboardingIntent.NicknameChanged("    "))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(5, vm.uiState.value.currentPage)
    }

    @Test
    fun `NicknameChanged with exactly NICKNAME_MAX_LENGTH stays at the cap`() {
        val vm = createViewModel()
        val exact = "a".repeat(OnboardingViewModel.NICKNAME_MAX_LENGTH)
        vm.onIntent(OnboardingIntent.NicknameChanged(exact))
        assertEquals(OnboardingViewModel.NICKNAME_MAX_LENGTH, vm.uiState.value.nickname.length)
    }

    @Test
    fun `NicknameChanged below cap keeps full value`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.NicknameChanged("Jo"))
        assertEquals("Jo", vm.uiState.value.nickname)
    }

    @Test
    fun `PageSet to negative value still records it (no clamp)`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(-1))
        assertEquals(-1, vm.uiState.value.currentPage)
    }

    @Test
    fun `PageSet beyond TOTAL_PAGES still records (no clamp)`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(99))
        assertEquals(99, vm.uiState.value.currentPage)
    }

    @Test
    fun `NextPage from page 4 is non-blocking even without notification permission`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.PageSet(4))
        vm.onIntent(OnboardingIntent.NextPage)
        assertEquals(5, vm.uiState.value.currentPage)
    }

    @Test
    fun `multiple PreviousPage on page 0 stays clamped`() {
        val vm = createViewModel()
        repeat(5) { vm.onIntent(OnboardingIntent.PreviousPage) }
        assertEquals(0, vm.uiState.value.currentPage)
    }

    @Test
    fun `OnboardingCompletedLogged invoked twice fires analytics twice`() {
        val vm = createViewModel()
        vm.onIntent(OnboardingIntent.OnboardingCompletedLogged)
        vm.onIntent(OnboardingIntent.OnboardingCompletedLogged)
        verify(exactly = 2) { analyticsRepository.onboardingCompleted() }
    }

    @Test
    fun `canCompleteOnboarding shows profanity alert and returns false`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        val vm = createViewModel()
        // canCompleteOnboarding reads cached hasFineLocation — refresh it first
        vm.onIntent(OnboardingIntent.RefreshPermissions)
        vm.onIntent(OnboardingIntent.NicknameChanged("fuck"))
        val ok = vm.canCompleteOnboarding()
        assertFalse(ok)
        assertTrue(vm.uiState.value.showProfanityAlert)
    }
}
