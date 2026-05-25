package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.gamelogic.PlayerRole

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
import kotlinx.coroutines.flow.first
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
            auth = auth,
            appContext = mockk(relaxed = true),
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
        coEvery { firestoreRepository.findActiveGame("user-123") } returns FirestoreRepository.ActiveGameResult(game, PlayerRole.HUNTER, dev.rahier.pouleparty.ui.gamelogic.GamePhase.IN_PROGRESS)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(game, vm.uiState.value.activeGame)
        assertEquals(PlayerRole.HUNTER, vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds chicken game`() {
        mockAuthUser("user-123")
        val game = Game.mock
        coEvery { firestoreRepository.findActiveGame("user-123") } returns FirestoreRepository.ActiveGameResult(game, PlayerRole.CHICKEN, dev.rahier.pouleparty.ui.gamelogic.GamePhase.IN_PROGRESS)
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
    fun `onStartButtonTapped opens join sheet without gating on location permission`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)

        assertTrue(vm.uiState.value.isShowingJoinSheet)
        assertFalse(vm.uiState.value.isShowingLocationRequired)
    }

    @Test
    fun `canCreateParty always returns true and never shows location alert`() {
        every { locationRepository.hasFineLocationPermission() } returns false
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

    // ── Edge cases ─────────────────────────────────────────

    @Test
    fun `StartButtonTapped opens join sheet and resets fields`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.GameCodeChanged("OLD123"))
        vm.onIntent(HomeIntent.TeamNameChanged("Old"))
        vm.onIntent(HomeIntent.StartButtonTapped)
        assertTrue(vm.uiState.value.isShowingJoinSheet)
        assertEquals("", vm.uiState.value.gameCode)
        assertEquals("", vm.uiState.value.teamName)
    }

    @Test
    fun `JoinSheetDismissed clears all join state`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.StartButtonTapped)
        vm.onIntent(HomeIntent.GameCodeChanged("ABC123"))
        vm.onIntent(HomeIntent.TeamNameChanged("Eagles"))
        vm.onIntent(HomeIntent.JoinSheetDismissed)
        assertFalse(vm.uiState.value.isShowingJoinSheet)
        assertEquals("", vm.uiState.value.gameCode)
        assertEquals("", vm.uiState.value.teamName)
    }

    @Test
    fun `GameCodeChanged below required length stays in EnteringCode step`() {
        val vm = createViewModel()
        vm.onIntent(HomeIntent.GameCodeChanged("ABC"))
        assertTrue(vm.uiState.value.joinStep is dev.rahier.pouleparty.ui.home.JoinFlowStep.EnteringCode)
    }

    @Test
    fun `GameCodeChanged uppercase normalizes lowercase input`() {
        val vm = createViewModel()
        vm.onIntent(HomeIntent.GameCodeChanged("abc"))
        assertEquals("ABC", vm.uiState.value.gameCode)
    }

    @Test
    fun `GameCodeChanged with non-alphanumeric stays in EnteringCode`() {
        val vm = createViewModel()
        vm.onIntent(HomeIntent.GameCodeChanged("AB!@#1"))
        assertTrue(vm.uiState.value.joinStep is dev.rahier.pouleparty.ui.home.JoinFlowStep.EnteringCode)
    }

    @Test
    fun `RejoinActiveGameTapped without active game does nothing`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns null
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.RejoinActiveGameTapped)
        assertNull(vm.uiState.value.activeGame)
    }

    @Test
    fun `ToggleMusic flips persisted preference each call`() {
        every { prefs.getBoolean(AppConstants.PREF_IS_MUSIC_MUTED, false) } returns false
        val vm = createViewModel()
        val initial = vm.uiState.value.isMusicMuted
        vm.onIntent(HomeIntent.ToggleMusic)
        assertEquals(!initial, vm.uiState.value.isMusicMuted)
        vm.onIntent(HomeIntent.ToggleMusic)
        assertEquals(initial, vm.uiState.value.isMusicMuted)
    }

    @Test
    fun `ActiveGameDismissed clears both game and role`() {
        mockAuthUser("user-123")
        val game = dev.rahier.pouleparty.model.Game.mock
        coEvery { firestoreRepository.findActiveGame("user-123") } returns FirestoreRepository.ActiveGameResult(game, PlayerRole.HUNTER, dev.rahier.pouleparty.ui.gamelogic.GamePhase.IN_PROGRESS)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.activeGame)
        vm.onIntent(HomeIntent.ActiveGameDismissed)
        assertNull(vm.uiState.value.activeGame)
        assertNull(vm.uiState.value.activeGameRole)
    }

    // ── PP-45: admin code dialog ──

    @Test
    fun `CreatePartyLongPressed opens admin code dialog without gating on location permission`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.CreatePartyLongPressed)
        assertTrue(vm.uiState.value.isShowingAdminCodeDialog)
        assertFalse(vm.uiState.value.isShowingLocationRequired)
        assertEquals("", vm.uiState.value.adminCodeInput)
    }

    @Test
    fun `validateAdminCode with correct code returns true and clears state`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.CreatePartyLongPressed)
        vm.onIntent(HomeIntent.AdminCodeChanged(dev.rahier.pouleparty.model.AdminCode.VALUE))
        assertTrue(vm.validateAdminCode())
        assertFalse(vm.uiState.value.isShowingAdminCodeDialog)
        assertEquals("", vm.uiState.value.adminCodeInput)
        assertFalse(vm.uiState.value.isShowingAdminCodeError)
    }

    @Test
    fun `validateAdminCode with wrong code returns false and surfaces error`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.CreatePartyLongPressed)
        vm.onIntent(HomeIntent.AdminCodeChanged("nope"))
        assertFalse(vm.validateAdminCode())
        assertFalse(vm.uiState.value.isShowingAdminCodeDialog)
        assertEquals("", vm.uiState.value.adminCodeInput)
        assertTrue(vm.uiState.value.isShowingAdminCodeError)
    }

    @Test
    fun `AdminCodeDismissed clears dialog state`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.CreatePartyLongPressed)
        vm.onIntent(HomeIntent.AdminCodeChanged("abc"))
        vm.onIntent(HomeIntent.AdminCodeDismissed)
        assertFalse(vm.uiState.value.isShowingAdminCodeDialog)
        assertEquals("", vm.uiState.value.adminCodeInput)
    }

    // ── App Review demo code dialog ──

    @Test
    fun `StartButtonLongPressed opens demo code dialog`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.StartButtonLongPressed)
        assertTrue(vm.uiState.value.isShowingDemoCodeDialog)
        assertEquals("", vm.uiState.value.demoCodeInput)
    }

    @Test
    fun `validateDemoCode with correct code clears state and emits NavigateToDemoMode`() = kotlinx.coroutines.test.runTest(testDispatcher) {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.StartButtonLongPressed)
        vm.onIntent(HomeIntent.DemoCodeChanged(dev.rahier.pouleparty.model.DemoCode.VALUE))
        vm.validateDemoCode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isShowingDemoCodeDialog)
        assertEquals("", vm.uiState.value.demoCodeInput)
        assertFalse(vm.uiState.value.isShowingDemoCodeError)
        val effect = vm.effects.first()
        assertEquals(dev.rahier.pouleparty.ui.home.HomeEffect.NavigateToDemoMode, effect)
    }

    @Test
    fun `validateDemoCode with wrong code surfaces error and emits no effect`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.StartButtonLongPressed)
        vm.onIntent(HomeIntent.DemoCodeChanged("nope"))
        vm.validateDemoCode()
        assertFalse(vm.uiState.value.isShowingDemoCodeDialog)
        assertEquals("", vm.uiState.value.demoCodeInput)
        assertTrue(vm.uiState.value.isShowingDemoCodeError)
    }

    @Test
    fun `DemoCodeDismissed clears dialog state`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.StartButtonLongPressed)
        vm.onIntent(HomeIntent.DemoCodeChanged("abc"))
        vm.onIntent(HomeIntent.DemoCodeDismissed)
        assertFalse(vm.uiState.value.isShowingDemoCodeDialog)
        assertEquals("", vm.uiState.value.demoCodeInput)
    }

}
