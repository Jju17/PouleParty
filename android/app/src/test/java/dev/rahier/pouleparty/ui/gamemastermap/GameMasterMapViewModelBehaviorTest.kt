package dev.rahier.pouleparty.ui.gamemastermap

import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.common.UiText
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.PresenceRepository
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.data.ChallengeSubmissionRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.model.Registration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class GameMasterMapViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gameRepository: GameRepository
    private lateinit var presenceRepository: PresenceRepository
    private lateinit var gameFunctions: GameFunctions
    private lateinit var challengeSubmissions: ChallengeSubmissionRepository
    private lateinit var auth: FirebaseAuth

    private val gameConfigFlow = MutableStateFlow<Game?>(null)
    private val registrationsFlow = MutableSharedFlow<List<Registration>>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val hunterLocationsFlow = MutableSharedFlow<List<HunterLocation>>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gameRepository = mockk(relaxed = true)
        presenceRepository = mockk(relaxed = true)
        gameFunctions = mockk(relaxed = true)
        challengeSubmissions = mockk(relaxed = true)
        auth = mockk(relaxed = true)

        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "gm-uid"
        every { auth.currentUser } returns mockUser

        // Default stubs, by default no game is configured so
        // `loadGame()` exits early and the infinite-tick loop never
        // starts. Individual tests opt-in to a populated game.
        coEvery { gameRepository.getConfig(any()) } returns null
        every { gameRepository.gameConfigFlow(any()) } returns gameConfigFlow
        every { gameRepository.registrationsFlow(any()) } returns registrationsFlow
        every { presenceRepository.chickenLocationFlow(any()) } returns emptyFlow()
        every { presenceRepository.hunterLocationsFlow(any()) } returns hunterLocationsFlow
        every { gameRepository.powerUpsFlow(any()) } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(gameId: String = "test-id"): GameMasterMapViewModel =
        GameMasterMapViewModel(
            gameRepository = gameRepository,
            presenceRepository = presenceRepository,
            gameFunctions = gameFunctions,
            auth = auth,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId)),
        )

    @Test
    fun `leaving removes the game master server-side then returns to the menu`() = kotlinx.coroutines.test.runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameMasterMapIntent.LeaveGameTapped)
        assertEquals(GameMasterMapEffect.ReturnedToMenu, vm.effects.first())
        coVerify(exactly = 1) { gameFunctions.leaveGame("test-id") }
    }

    @Test
    fun `a failed leave keeps the game master on the map with an error`() = kotlinx.coroutines.test.runTest(testDispatcher) {
        coEvery { gameFunctions.leaveGame(any()) } throws IllegalStateException("boom")
        val vm = createViewModel()
        vm.onIntent(GameMasterMapIntent.LeaveGameTapped)
        testDispatcher.scheduler.runCurrent()
        assertEquals(R.string.api_error_unknown, vm.uiState.value.leaveErrorRes)
        assertFalse(vm.uiState.value.isLeaving)
    }

    // ── Read-only intent surface ────────────────────────

    @Test
    fun `info tapped shows game info`() {
        val vm = createViewModel()
        vm.onIntent(GameMasterMapIntent.InfoTapped)
        assertTrue(vm.uiState.value.showGameInfo)
    }

    @Test
    fun `dismiss game info hides it`() {
        val vm = createViewModel()
        vm.onIntent(GameMasterMapIntent.InfoTapped)
        vm.onIntent(GameMasterMapIntent.DismissGameInfo)
        assertFalse(vm.uiState.value.showGameInfo)
    }

    @Test
    fun `hunters drawer toggle`() {
        val vm = createViewModel()
        vm.onIntent(GameMasterMapIntent.HuntersDrawerTapped)
        assertTrue(vm.uiState.value.showHuntersDrawer)
        vm.onIntent(GameMasterMapIntent.DismissHuntersDrawer)
        assertFalse(vm.uiState.value.showHuntersDrawer)
    }

    @Test
    fun `designate hunter tapped sets pending registration`() {
        val vm = createViewModel()
        val reg = Registration(userId = "uid-1", teamName = "The Foxes")
        vm.onIntent(GameMasterMapIntent.DesignateHunterTapped(reg))
        assertEquals(reg, vm.uiState.value.pendingChickenDesignation)
    }

    @Test
    fun `designate cancel clears pending registration`() {
        val vm = createViewModel()
        val reg = Registration(userId = "uid-1", teamName = "Foxes")
        vm.onIntent(GameMasterMapIntent.DesignateHunterTapped(reg))
        vm.onIntent(GameMasterMapIntent.DesignateCancelTapped)
        assertNull(vm.uiState.value.pendingChickenDesignation)
    }

    @Test
    fun `designate confirm calls designateChicken with the registration uid`() {
        // Seed the game so the VM has an id to forward through.
        val game = Game.mock.copy(id = "game-x")
        coEvery { gameRepository.getConfig("game-x") } returns game
        val vm = createViewModel(gameId = "game-x")
        testDispatcher.scheduler.runCurrent()

        val reg = Registration(userId = "new-chicken-uid", teamName = "Apex")
        vm.onIntent(GameMasterMapIntent.DesignateHunterTapped(reg))
        vm.onIntent(GameMasterMapIntent.DesignateConfirmTapped)
        // `runCurrent` flushes the dispatched continuations without
        // walking through the infinite-tick virtual-time loop.
        testDispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            gameFunctions.designateChicken("game-x", "new-chicken-uid")
        }
        // After success: drawer closes, pending cleared, no error.
        assertFalse(vm.uiState.value.showHuntersDrawer)
        assertNull(vm.uiState.value.pendingChickenDesignation)
        assertNull(vm.uiState.value.designationError)
    }

    @Test
    fun `designation error surfaces a message and is dismissable`() {
        coEvery { gameRepository.getConfig("game-x") } returns Game.mock.copy(id = "game-x")
        coEvery { gameFunctions.designateChicken("game-x", any()) } throws RuntimeException("offline")
        val vm = createViewModel(gameId = "game-x")
        testDispatcher.scheduler.runCurrent()

        vm.onIntent(GameMasterMapIntent.DesignateHunterTapped(Registration(userId = "uid-1", teamName = "Foxes")))
        vm.onIntent(GameMasterMapIntent.DesignateConfirmTapped)
        testDispatcher.scheduler.runCurrent()

        assertNotNull(vm.uiState.value.designationError)
        vm.onIntent(GameMasterMapIntent.DesignationErrorDismissed)
        assertNull(vm.uiState.value.designationError)
    }

    @Test
    fun `hunter annotations use teamName when registration is known`() {
        val game = Game.mock.copy(id = "game-tn")
        coEvery { gameRepository.getConfig("game-tn") } returns game
        val vm = createViewModel(gameId = "game-tn")
        testDispatcher.scheduler.runCurrent()

        // Push registrations first so the look-up table is populated.
        // `tryEmit` is non-suspending and works fine here because the
        // shared flow is bounded with `DROP_OLDEST`.
        registrationsFlow.tryEmit(
            listOf(
                Registration(userId = "uid-1", teamName = "The Foxes"),
                Registration(userId = "uid-2", teamName = "Les Coyotes"),
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Then hunter locations come in.
        hunterLocationsFlow.tryEmit(
            listOf(
                HunterLocation(hunterId = "uid-1", location = GeoPoint(50.0, 4.0), timestamp = Timestamp(Date())),
                HunterLocation(hunterId = "uid-2", location = GeoPoint(50.1, 4.1), timestamp = Timestamp(Date())),
            )
        )
        testDispatcher.scheduler.runCurrent()

        val labels = vm.uiState.value.hunterAnnotations.map { it.displayName }
        assertTrue("Expected 'The Foxes' in $labels", labels.contains(UiText.Verbatim("The Foxes")))
        assertTrue("Expected 'Les Coyotes' in $labels", labels.contains(UiText.Verbatim("Les Coyotes")))
        assertFalse(labels.any { it is UiText.Resource })
    }

    @Test
    fun `hunter annotations fall back to index when registration is missing`() {
        val game = Game.mock.copy(id = "game-noreg")
        coEvery { gameRepository.getConfig("game-noreg") } returns game
        val vm = createViewModel(gameId = "game-noreg")
        testDispatcher.scheduler.runCurrent()

        hunterLocationsFlow.tryEmit(
            listOf(
                HunterLocation(hunterId = "uid-zzz", location = GeoPoint(50.0, 4.0), timestamp = Timestamp(Date())),
                HunterLocation(hunterId = "uid-aaa", location = GeoPoint(50.1, 4.1), timestamp = Timestamp(Date())),
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Sorted by hunterId: uid-aaa = "Hunter 1", uid-zzz = "Hunter 2".
        val byId = vm.uiState.value.hunterAnnotations.associate { it.id to it.displayName }
        assertEquals(dev.rahier.pouleparty.ui.common.uiText(R.string.hunter_number, 1), byId["uid-aaa"])
        assertEquals(dev.rahier.pouleparty.ui.common.uiText(R.string.hunter_number, 2), byId["uid-zzz"])
    }

    @Test
    fun `registrations arriving after locations rebuild labels`() {
        val game = Game.mock.copy(id = "game-late-reg")
        coEvery { gameRepository.getConfig("game-late-reg") } returns game
        val vm = createViewModel(gameId = "game-late-reg")
        testDispatcher.scheduler.runCurrent()

        // First: hunter location lands with no registration.
        hunterLocationsFlow.tryEmit(
            listOf(
                HunterLocation(hunterId = "uid-1", location = GeoPoint(50.0, 4.0), timestamp = Timestamp(Date()))
            )
        )
        testDispatcher.scheduler.runCurrent()
        assertEquals(dev.rahier.pouleparty.ui.common.uiText(R.string.hunter_number, 1), vm.uiState.value.hunterAnnotations.first().displayName)

        // Registration arrives → label must flip to teamName.
        registrationsFlow.tryEmit(listOf(Registration(userId = "uid-1", teamName = "Apex Predators")))
        testDispatcher.scheduler.runCurrent()
        assertEquals(UiText.Verbatim("Apex Predators"), vm.uiState.value.hunterAnnotations.first().displayName)
    }

    // ── Read-only stream surface (no power-up tray) ─────

    @Test
    fun `state exposes empty power-up tray and no isOutsideZone flag`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertTrue(state.availablePowerUps.isEmpty())
        assertTrue(state.collectedPowerUps.isEmpty())
        assertFalse(state.showPowerUpInventory)
        assertNull(state.powerUpNotification)
        assertNull(state.lastActivatedPowerUpType)
        assertFalse(state.isOutsideZone)
    }
}
