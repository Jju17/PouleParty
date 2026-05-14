package dev.rahier.pouleparty.ui.gamemastermap

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.data.FirestoreRepository
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

/**
 * PP-66 — Behavioural tests for `GameMasterMapViewModel` (Android
 * mirror of iOS `GameMasterMapFeature`).
 *
 * Covers:
 *  - The read-only intent surface (info / hunters drawer / leave game).
 *  - Designate-chicken flow (pending state, confirm wires through to
 *    `FirestoreRepository.designateChicken`, error path surfaces a
 *    message, cancel clears the pending registration).
 *  - The teamName-everywhere rule (PP-90 / 2026-05-08): hunter markers
 *    surface the registered `teamName`, with a deterministic
 *    `Hunter N` fallback (sorted by hunterId) when registrations
 *    haven't landed yet.
 *  - The "registrations arrive after locations" parity scenario: the
 *    `Hunter N` placeholder must be re-labelled the instant the
 *    registration doc lands.
 *
 * Note: the routing decision (GM lands on `GameMasterMapScreen`, not
 * `ChickenMapScreen`) is covered separately by
 * `GameMasterRoutingTest`. This file focuses on what the screen does
 * once it's on it.
 *
 * Test-runner gotcha: `GameMasterMapViewModel.startStreams` spawns
 * an infinite `while (true) { delay(1000) }` ticker. We MUST use
 * `testDispatcher.scheduler.runCurrent()` here — `advanceUntilIdle()`
 * loops forever through the virtual delays. Pattern matches
 * `ChickenMapViewModelBehaviorTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMasterMapViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
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
        firestoreRepository = mockk(relaxed = true)
        auth = mockk(relaxed = true)

        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "gm-uid"
        every { auth.currentUser } returns mockUser

        // Default stubs — by default no game is configured so
        // `loadGame()` exits early and the infinite-tick loop never
        // starts. Individual tests opt-in to a populated game.
        coEvery { firestoreRepository.getConfig(any()) } returns null
        every { firestoreRepository.gameConfigFlow(any()) } returns gameConfigFlow
        every { firestoreRepository.registrationsFlow(any()) } returns registrationsFlow
        every { firestoreRepository.chickenLocationFlow(any()) } returns emptyFlow()
        every { firestoreRepository.hunterLocationsFlow(any()) } returns hunterLocationsFlow
        every { firestoreRepository.powerUpsFlow(any()) } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(gameId: String = "test-id"): GameMasterMapViewModel =
        GameMasterMapViewModel(
            firestoreRepository = firestoreRepository,
            auth = auth,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId)),
        )

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

    // ── Designate chicken (PP-86) ───────────────────────

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
        coEvery { firestoreRepository.getConfig("game-x") } returns game
        val vm = createViewModel(gameId = "game-x")
        testDispatcher.scheduler.runCurrent()

        val reg = Registration(userId = "new-chicken-uid", teamName = "Apex")
        vm.onIntent(GameMasterMapIntent.DesignateHunterTapped(reg))
        vm.onIntent(GameMasterMapIntent.DesignateConfirmTapped)
        // `runCurrent` flushes the dispatched continuations without
        // walking through the infinite-tick virtual-time loop.
        testDispatcher.scheduler.runCurrent()

        coVerify(exactly = 1) {
            firestoreRepository.designateChicken("game-x", "new-chicken-uid")
        }
        // After success: drawer closes, pending cleared, no error.
        assertFalse(vm.uiState.value.showHuntersDrawer)
        assertNull(vm.uiState.value.pendingChickenDesignation)
        assertNull(vm.uiState.value.designationError)
    }

    @Test
    fun `designation error surfaces a message and is dismissable`() {
        coEvery { firestoreRepository.getConfig("game-x") } returns Game.mock.copy(id = "game-x")
        coEvery { firestoreRepository.designateChicken("game-x", any()) } throws RuntimeException("offline")
        val vm = createViewModel(gameId = "game-x")
        testDispatcher.scheduler.runCurrent()

        vm.onIntent(GameMasterMapIntent.DesignateHunterTapped(Registration(userId = "uid-1", teamName = "Foxes")))
        vm.onIntent(GameMasterMapIntent.DesignateConfirmTapped)
        testDispatcher.scheduler.runCurrent()

        assertNotNull(vm.uiState.value.designationError)
        vm.onIntent(GameMasterMapIntent.DesignationErrorDismissed)
        assertNull(vm.uiState.value.designationError)
    }

    // ── teamName-everywhere (PP-90 / 2026-05-08) ────────

    @Test
    fun `hunter annotations use teamName when registration is known`() {
        val game = Game.mock.copy(id = "game-tn")
        coEvery { firestoreRepository.getConfig("game-tn") } returns game
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
        assertTrue("Expected 'The Foxes' in $labels", labels.contains("The Foxes"))
        assertTrue("Expected 'Les Coyotes' in $labels", labels.contains("Les Coyotes"))
        assertFalse(labels.any { it.startsWith("Hunter ") })
    }

    @Test
    fun `hunter annotations fall back to index when registration is missing`() {
        val game = Game.mock.copy(id = "game-noreg")
        coEvery { firestoreRepository.getConfig("game-noreg") } returns game
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
        assertEquals("Hunter 1", byId["uid-aaa"])
        assertEquals("Hunter 2", byId["uid-zzz"])
    }

    @Test
    fun `registrations arriving after locations rebuild labels`() {
        val game = Game.mock.copy(id = "game-late-reg")
        coEvery { firestoreRepository.getConfig("game-late-reg") } returns game
        val vm = createViewModel(gameId = "game-late-reg")
        testDispatcher.scheduler.runCurrent()

        // First: hunter location lands with no registration.
        hunterLocationsFlow.tryEmit(
            listOf(
                HunterLocation(hunterId = "uid-1", location = GeoPoint(50.0, 4.0), timestamp = Timestamp(Date()))
            )
        )
        testDispatcher.scheduler.runCurrent()
        assertEquals("Hunter 1", vm.uiState.value.hunterAnnotations.first().displayName)

        // Registration arrives → label must flip to teamName.
        registrationsFlow.tryEmit(listOf(Registration(userId = "uid-1", teamName = "Apex Predators")))
        testDispatcher.scheduler.runCurrent()
        assertEquals("Apex Predators", vm.uiState.value.hunterAnnotations.first().displayName)
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
