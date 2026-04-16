package dev.rahier.pouleparty.ui

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.home.HomeIntent
import dev.rahier.pouleparty.ui.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        auth = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockAuthUser(uid: String?) {
        if (uid != null) {
            val mockUser = mockk<FirebaseUser>()
            every { mockUser.uid } returns uid
            every { auth.currentUser } returns mockUser
        } else {
            every { auth.currentUser } returns null
        }
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = mockk<dev.rahier.pouleparty.data.AnalyticsRepository>(relaxed = true),
            prefs = prefs,
            auth = auth
        )
    }

    // ── checkForActiveGame (called in init) ──

    @Test
    fun `checkForActiveGame with no user does not update state`() {
        mockAuthUser(null)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.activeGame)
        assertNull(vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds hunter game`() {
        mockAuthUser("user-123")
        val game = Game.mock
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(game, PlayerRole.HUNTER)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(game, vm.uiState.value.activeGame)
        assertEquals(PlayerRole.HUNTER, vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds chicken game`() {
        mockAuthUser("user-123")
        val game = Game.mock
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(game, PlayerRole.CHICKEN)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(game, vm.uiState.value.activeGame)
        assertEquals(PlayerRole.CHICKEN, vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds no game`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns null
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.activeGame)
        assertNull(vm.uiState.value.activeGameRole)
    }

    // ── rejoinGame ──

    // ── Location permission ──

    @Test
    fun `hasLocationPermission delegates to location repository when granted`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.hasLocationPermission())
    }

    @Test
    fun `hasLocationPermission delegates to location repository when denied`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.hasLocationPermission())
    }

    @Test
    fun `onLocationPermissionDenied shows location required alert`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.LocationPermissionDenied)

        assertTrue(vm.uiState.value.isShowingLocationRequired)
    }

    @Test
    fun `onStartButtonTapped with permission opens join sheet`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)

        assertTrue(vm.uiState.value.isShowingJoinSheet)
        assertFalse(vm.uiState.value.isShowingLocationRequired)
    }

    @Test
    fun `onStartButtonTapped without permission shows location alert and not join sheet`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)

        assertTrue(vm.uiState.value.isShowingLocationRequired)
        assertFalse(vm.uiState.value.isShowingJoinSheet)
    }

    @Test
    fun `onCreatePartyTapped without permission returns false and shows alert`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = vm.canCreateParty()

        assertFalse(result)
        assertTrue(vm.uiState.value.isShowingLocationRequired)
    }

    @Test
    fun `onCreatePartyTapped with permission returns true and does not show alert`() {
        every { locationRepository.hasFineLocationPermission() } returns true
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val result = vm.canCreateParty()

        assertTrue(result)
        assertFalse(vm.uiState.value.isShowingLocationRequired)
    }

    @Test
    fun `onLocationRequiredDismissed clears the alert`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.LocationPermissionDenied)
        assertTrue(vm.uiState.value.isShowingLocationRequired)

        vm.onIntent(HomeIntent.LocationRequiredDismissed)
        assertFalse(vm.uiState.value.isShowingLocationRequired)
    }
}
