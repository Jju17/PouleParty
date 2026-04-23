package dev.rahier.pouleparty.ui

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.ui.home.HomeIntent
import dev.rahier.pouleparty.ui.home.HomeViewModel
import dev.rahier.pouleparty.ui.home.JoinFlowStep
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * End-to-end MVI tests pinning the Wave 1/2/3 join-flow fixes:
 *  - Self-join (hunter taps own chicken code) → CodeNotFound
 *  - pending_payment / payment_failed code → `isShowingGameNotFound` on Join tap
 *  - rejoinActiveGame refetches and routes to GameNotFound for stale done games
 *  - dismissActiveGame persists and hides subsequent banner
 *  - validateCode cancels the previous in-flight job on re-type
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelJoinFlowTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
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

    private fun mockAuthUser(uid: String?) {
        if (uid != null) {
            val mockUser = mockk<FirebaseUser>()
            every { mockUser.uid } returns uid
            every { auth.currentUser } returns mockUser
        } else {
            every { auth.currentUser } returns null
        }
    }

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        firestoreRepository = firestoreRepository,
        locationRepository = locationRepository,
        analyticsRepository = analyticsRepository,
        prefs = prefs,
        auth = auth,
    )

    // ── Self-join protection ──────────────────────────────

    @Test
    fun `typing own chicken game code ends in CodeNotFound`() {
        val myUid = "user-abc"
        mockAuthUser(myUid)
        val ownGame = Game.mock.copy(creatorId = myUid)
        coEvery { firestoreRepository.findGameByCode("ABC123") } returns ownGame

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)
        vm.onIntent(HomeIntent.GameCodeChanged("abc123"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.joinStep is JoinFlowStep.CodeNotFound)
    }

    @Test
    fun `typing other creator code ends in CodeValidated`() {
        mockAuthUser("user-xyz")
        val game = Game.mock.copy(creatorId = "other")
        coEvery { firestoreRepository.findGameByCode("ABC123") } returns game
        coEvery { firestoreRepository.findRegistration(any(), any()) } returns null

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)
        vm.onIntent(HomeIntent.GameCodeChanged("ABC123"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.joinStep is JoinFlowStep.CodeValidated)
    }

    // ── pending_payment / payment_failed Join rejection ───

    @Test
    fun `tapping Join on pending_payment game sets isShowingGameNotFound`() {
        mockAuthUser("user-xyz")
        val game = Game.mock.copy(creatorId = "other", status = "pending_payment")
        coEvery { firestoreRepository.findGameByCode("ABC123") } returns game

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)
        vm.onIntent(HomeIntent.GameCodeChanged("ABC123"))
        testDispatcher.scheduler.advanceUntilIdle()
        // Even though it reached CodeValidated (the validator doesn't gate
        // on status), tapping Join must route through the alert path.
        vm.onIntent(HomeIntent.JoinValidatedGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isShowingGameNotFound)
        assertTrue(vm.uiState.value.joinStep is JoinFlowStep.EnteringCode)
    }

    @Test
    fun `tapping Join on payment_failed game sets isShowingGameNotFound`() {
        mockAuthUser("user-xyz")
        val game = Game.mock.copy(creatorId = "other", status = "payment_failed")
        coEvery { firestoreRepository.findGameByCode("ABC123") } returns game

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.StartButtonTapped)
        vm.onIntent(HomeIntent.GameCodeChanged("ABC123"))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HomeIntent.JoinValidatedGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isShowingGameNotFound)
    }

    // ── rejoinActiveGame with stale status ────────────────

    @Test
    fun `rejoinActiveGame on stale done game routes to GameNotFound`() {
        mockAuthUser("user-xyz")
        val cachedWaitingGame = Game.mock.copy(id = "g1", status = "waiting")
        val freshDoneGame = Game.mock.copy(id = "g1", status = "done")
        // checkForActiveGame fires at init: return the waiting cached version.
        coEvery { firestoreRepository.findActiveGame("user-xyz") } returns FirestoreRepository.ActiveGameResult(cachedWaitingGame, dev.rahier.pouleparty.ui.gamelogic.PlayerRole.HUNTER, dev.rahier.pouleparty.ui.gamelogic.GamePhase.UPCOMING)
        // rejoinActiveGame then refetches.
        coEvery { firestoreRepository.getConfig("g1") } returns freshDoneGame

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(cachedWaitingGame.id, vm.uiState.value.activeGame?.id)

        vm.onIntent(HomeIntent.RejoinActiveGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isShowingGameNotFound)
        assertNull(vm.uiState.value.activeGame)
    }

    // ── Dismiss active game persistence ───────────────────

    @Test
    fun `dismissing active game adds gameId to dismissed set in prefs`() {
        mockAuthUser("user-xyz")
        val game = Game.mock.copy(id = "dismiss-me", status = "waiting")
        coEvery { firestoreRepository.findActiveGame("user-xyz") } returns FirestoreRepository.ActiveGameResult(game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole.HUNTER, dev.rahier.pouleparty.ui.gamelogic.GamePhase.UPCOMING)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.activeGame)

        val setSlot = slot<Set<String>>()
        every { prefsEditor.putStringSet(eq(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS), capture(setSlot)) } returns prefsEditor

        vm.onIntent(HomeIntent.ActiveGameDismissed)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("dismiss-me" in setSlot.captured)
        assertNull(vm.uiState.value.activeGame)
    }

    @Test
    fun `findActiveGame skips a previously dismissed game`() {
        mockAuthUser("user-xyz")
        val game = Game.mock.copy(id = "dismissed", status = "waiting")
        coEvery { firestoreRepository.findActiveGame("user-xyz") } returns FirestoreRepository.ActiveGameResult(game, dev.rahier.pouleparty.ui.gamelogic.PlayerRole.HUNTER, dev.rahier.pouleparty.ui.gamelogic.GamePhase.UPCOMING)
        every { prefs.getStringSet(eq(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS), any()) } returns setOf("dismissed")

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.activeGame)
    }

    // ── Team name trimming ────────────────────────────────

    @Test
    fun `team name change trims leading whitespace`() {
        mockAuthUser("user-xyz")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.TeamNameChanged("   Foxes"))
        assertEquals("Foxes", vm.uiState.value.teamName)
    }

    @Test
    fun `whitespace-only team name is invalid`() {
        mockAuthUser("user-xyz")
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onIntent(HomeIntent.TeamNameChanged("      "))
        assertFalse(vm.uiState.value.isTeamNameValid)
    }
}
