package dev.rahier.pouleparty.ui

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.PresenceRepository
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.data.ChallengeSubmissionRepository
import dev.rahier.pouleparty.data.ActiveGameResult
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GamePhase
import dev.rahier.pouleparty.model.PlayerRole
import dev.rahier.pouleparty.ui.home.HomeIntent
import dev.rahier.pouleparty.ui.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class HomeActivePhaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gameRepository: GameRepository
    private lateinit var presenceRepository: PresenceRepository
    private lateinit var gameFunctions: GameFunctions
    private lateinit var challengeSubmissions: ChallengeSubmissionRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gameRepository = mockk(relaxed = true)
        presenceRepository = mockk(relaxed = true)
        gameFunctions = mockk(relaxed = true)
        challengeSubmissions = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        analyticsRepository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        prefsEditor = mockk(relaxed = true)
        every { prefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { prefsEditor.putStringSet(any(), any()) } returns prefsEditor
        every { prefsEditor.putBoolean(any(), any()) } returns prefsEditor
        every { prefsEditor.putLong(any(), any()) } returns prefsEditor
        every { prefsEditor.remove(any()) } returns prefsEditor
        every { prefs.getBoolean(any(), any()) } returns false
        every { prefs.getLong(any(), any()) } returns 0L
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getStringSet(any(), any()) } returns emptySet()
        every { locationRepository.hasFineLocationPermission() } returns true
        auth = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockAuthUser(uid: String) {
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns uid
        every { auth.currentUser } returns mockUser
    }

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        gameRepository = gameRepository,
        gameFunctions = gameFunctions,
        locationRepository = locationRepository,
        analyticsRepository = analyticsRepository,
        prefs = prefs,
        auth = auth,
        appContext = mockk(relaxed = true),
        remoteConfig = mockk(relaxed = true) {
            io.mockk.every { adminCode } returns dev.rahier.pouleparty.model.AdminCode.VALUE
            io.mockk.every { codeMaxWrongAttempts } returns dev.rahier.pouleparty.AppConstants.CODE_MAX_WRONG_ATTEMPTS
            io.mockk.every { codeCooldownMs } returns dev.rahier.pouleparty.AppConstants.CODE_COOLDOWN_MS
            io.mockk.every { defaultInitialRadius } returns dev.rahier.pouleparty.AppConstants.DEFAULT_INITIAL_RADIUS
        },
    )

    // ── Phase wiring ──────────────────────────────────────

    @Test
    fun `inProgress phase is propagated to UI state`() {
        mockAuthUser("user-1")
        val game = Game.mock.copy(id = "g-ip")
        coEvery { gameRepository.findActiveGame("user-1") } returns
            ActiveGameResult(game, PlayerRole.HUNTER, GamePhase.IN_PROGRESS)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GamePhase.IN_PROGRESS, vm.uiState.value.activeGamePhase)
        assertEquals(PlayerRole.HUNTER, vm.uiState.value.activeGameRole)
    }

    @Test
    fun `upcoming phase is propagated to UI state`() {
        mockAuthUser("user-1")
        val game = Game.mock.copy(id = "g-up", status = "waiting")
        coEvery { gameRepository.findActiveGame("user-1") } returns
            ActiveGameResult(game, PlayerRole.CHICKEN, GamePhase.UPCOMING)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GamePhase.UPCOMING, vm.uiState.value.activeGamePhase)
        assertEquals(PlayerRole.CHICKEN, vm.uiState.value.activeGameRole)
    }

    // ── Dismiss / rejoin Set semantics ────────────────────

    @Test
    fun `dismiss adds gameId to the persisted set`() {
        mockAuthUser("user-1")
        val game = Game.mock.copy(id = "g-dismiss")
        coEvery { gameRepository.findActiveGame("user-1") } returns
            ActiveGameResult(game, PlayerRole.HUNTER, GamePhase.IN_PROGRESS)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val slot = slot<Set<String>>()
        every { prefsEditor.putStringSet(eq(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS), capture(slot)) } returns prefsEditor

        vm.onIntent(HomeIntent.ActiveGameDismissed)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("dismiss set must contain g-dismiss", "g-dismiss" in slot.captured)
    }

    @Test
    fun `dismiss preserves previously-dismissed ids (set union)`() {
        mockAuthUser("user-1")
        val game = Game.mock.copy(id = "new-one")
        coEvery { gameRepository.findActiveGame("user-1") } returns
            ActiveGameResult(game, PlayerRole.HUNTER, GamePhase.IN_PROGRESS)
        // Pre-existing dismiss: stored set already has one id.
        every { prefs.getStringSet(any(), any()) } returns setOf("prior-game")

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // findActiveGame resolved to 'new-one' (not in dismiss set) → banner visible
        assertEquals("new-one", vm.uiState.value.activeGame?.id)

        val slot = slot<Set<String>>()
        every { prefsEditor.putStringSet(eq(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS), capture(slot)) } returns prefsEditor

        vm.onIntent(HomeIntent.ActiveGameDismissed)
        testDispatcher.scheduler.advanceUntilIdle()

        // Both ids are in the persisted set after the dismiss.
        assertTrue("prior-game" in slot.captured)
        assertTrue("new-one" in slot.captured)
    }

    @Test
    fun `rejoin removes gameId from dismiss set without touching others`() {
        mockAuthUser("user-1")
        val game = Game.mock.copy(id = "now-rejoining")
        coEvery { gameRepository.findActiveGame("user-1") } returns
            ActiveGameResult(game, PlayerRole.HUNTER, GamePhase.IN_PROGRESS)
        every { prefs.getStringSet(any(), any()) } returns setOf("now-rejoining", "other-ghost")

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // Game was in dismiss set → findActiveGame returned null to UI.
        assertNull(vm.uiState.value.activeGame)

        // Simulate the user re-seeing the banner somehow (e.g. opening via deep link)
        // and tapping rejoin. For this test we short-circuit by calling rejoin via
        // forcing the state directly, not ideal but the goal is to verify the
        // prefs write path, which tests at the ViewModel level.
        //
        // We mock the API so the banner shows again (opt out of dismiss).
        every { prefs.getStringSet(any(), any()) } returns emptySet()
        // Re-trigger fetch: state should repopulate.
        vm.onIntent(HomeIntent.RefreshActiveGame)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("now-rejoining", vm.uiState.value.activeGame?.id)

        val slot = slot<Set<String>>()
        // Mock prefs to hold the "before rejoin" set: both ghosts.
        every { prefs.getStringSet(any(), any()) } returns setOf("now-rejoining", "other-ghost")
        every { prefsEditor.putStringSet(eq(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS), capture(slot)) } returns prefsEditor

        vm.onIntent(HomeIntent.RejoinActiveGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("now-rejoining should be removed", "now-rejoining" in slot.captured)
        assertTrue("other-ghost must stay", "other-ghost" in slot.captured)
    }
}
