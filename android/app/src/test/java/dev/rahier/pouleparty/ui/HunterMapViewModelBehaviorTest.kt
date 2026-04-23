package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.huntermap.HunterMapIntent
import dev.rahier.pouleparty.ui.huntermap.HunterMapViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HunterMapViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        // Make `loadGame()` exit early so init coroutines settle without
        // needing real game data from the relaxed mock.
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns null
        // Streams default to empty so background flows don't surface NPEs in tests.
        io.mockk.every { firestoreRepository.gameConfigFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { firestoreRepository.powerUpsFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { firestoreRepository.chickenLocationFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { firestoreRepository.challengesStream() } returns kotlinx.coroutines.flow.emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        gameId: String = "test-id",
        hunterName: String = "TestHunter",
        hunterId: String? = "hunter-123"
    ): HunterMapViewModel {
        if (hunterId != null) {
            val mockUser = mockk<FirebaseUser>()
            every { mockUser.uid } returns hunterId
            every { auth.currentUser } returns mockUser
        }
        return HunterMapViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = mockk<dev.rahier.pouleparty.data.AnalyticsRepository>(relaxed = true),
            auth = auth,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId, "hunterName" to hunterName))
        )
    }

    // MARK:, Found code entry

    @Test
    fun `onFoundButtonTapped shows code entry`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.FoundButtonTapped)
        assertTrue(vm.uiState.value.isEnteringFoundCode)
    }

    @Test
    fun `dismissFoundCodeEntry clears code and hides entry`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.FoundButtonTapped)
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("123"))
        vm.onIntent(HunterMapIntent.DismissFoundCodeEntry)
        assertFalse(vm.uiState.value.isEnteringFoundCode)
        assertEquals("", vm.uiState.value.enteredCode)
    }

    @Test
    fun `onEnteredCodeChanged truncates to max digits`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("123456789"))
        assertEquals(AppConstants.FOUND_CODE_DIGITS, vm.uiState.value.enteredCode.length)
    }

    @Test
    fun `onEnteredCodeChanged keeps short codes as-is`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("12"))
        assertEquals("12", vm.uiState.value.enteredCode)
    }

    // MARK:, Submit found code

    @Test
    fun `submitFoundCode wrong code shows alert`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        assertTrue(vm.uiState.value.showWrongCodeAlert)
        assertEquals("", vm.uiState.value.enteredCode)
        assertFalse(vm.uiState.value.isEnteringFoundCode)
    }

    @Test
    fun `submitFoundCode wrong code increments attempts`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        assertEquals(1, vm.uiState.value.wrongCodeAttempts)
    }

    @Test
    fun `submitFoundCode triggers cooldown after max attempts`() {
        val vm = createViewModel()
        repeat(AppConstants.CODE_MAX_WRONG_ATTEMPTS) {
            vm.onIntent(HunterMapIntent.DismissWrongCodeAlert)
            vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
            vm.onIntent(HunterMapIntent.SubmitFoundCode)
        }
        // After max attempts, wrongCodeAttempts resets to 0 and cooldown is set
        assertEquals(0, vm.uiState.value.wrongCodeAttempts)
        assertTrue(vm.uiState.value.codeCooldownUntil > 0)
    }

    @Test
    fun `submitFoundCode on cooldown does nothing`() {
        val vm = createViewModel()
        // Trigger cooldown
        repeat(AppConstants.CODE_MAX_WRONG_ATTEMPTS) {
            vm.onIntent(HunterMapIntent.DismissWrongCodeAlert)
            vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
            vm.onIntent(HunterMapIntent.SubmitFoundCode)
        }
        // Now on cooldown, try submitting again
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        // Code should still be "1234" (submitFoundCode returned early, didn't clear it)
        assertEquals("1234", vm.uiState.value.enteredCode)
    }

    @Test
    fun `submitFoundCode correct code navigates to victory`() {
        val vm = createViewModel()
        // Game.mock has foundCode = "1234"
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.shouldNavigateToVictory)
    }

    // MARK:, Wrong code alert

    @Test
    fun `dismissWrongCodeAlert clears alert`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        assertTrue(vm.uiState.value.showWrongCodeAlert)
        vm.onIntent(HunterMapIntent.DismissWrongCodeAlert)
        assertFalse(vm.uiState.value.showWrongCodeAlert)
    }

    // MARK:, Leave game

    @Test
    fun `onLeaveGameTapped shows leave alert`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.LeaveGameTapped)
        assertTrue(vm.uiState.value.showLeaveAlert)
    }

    @Test
    fun `dismissLeaveAlert hides leave alert`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.LeaveGameTapped)
        vm.onIntent(HunterMapIntent.DismissLeaveAlert)
        assertFalse(vm.uiState.value.showLeaveAlert)
    }

    @Test
    fun `confirmLeaveGame clears alert (NavigateToMenu effect emitted)`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.LeaveGameTapped)
        vm.onIntent(HunterMapIntent.ConfirmLeaveGame)
        assertFalse(vm.uiState.value.showLeaveAlert)
        // Effect emission is covered by the screen integration test;
        // unit-level we only verify the state transition.
    }

    // MARK:, Game over

    @Test
    fun `confirmGameOver clears alert (NavigateToVictory effect emitted)`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.ConfirmGameOver)
        assertFalse(vm.uiState.value.showGameOverAlert)
    }

    // MARK:, Game info

    @Test
    fun `onInfoTapped shows game info`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.InfoTapped)
        assertTrue(vm.uiState.value.showGameInfo)
    }

    @Test
    fun `dismissGameInfo hides game info`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.InfoTapped)
        vm.onIntent(HunterMapIntent.DismissGameInfo)
        assertFalse(vm.uiState.value.showGameInfo)
    }

    // MARK:, Hunter properties

    @Test
    fun `hunterId comes from auth`() {
        val vm = createViewModel(hunterId = "hunter-123")
        assertEquals("hunter-123", vm.hunterId)
    }

    @Test
    fun `hunterName comes from saved state`() {
        val vm = createViewModel(hunterName = "Alice")
        assertEquals("Alice", vm.hunterName)
    }

    @Test
    fun `hunterSubtitle for followTheChicken`() {
        val vm = createViewModel()
        // Default Game.mock is followTheChicken
        assertEquals("Catch the \uD83D\uDC14 !", vm.hunterSubtitle)
    }

    // ── Edge cases ─────────────────────────────────────────

    @Test
    fun `EnteredCodeChanged truncates beyond FOUND_CODE_DIGITS`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("123456789"))
        assertEquals(AppConstants.FOUND_CODE_DIGITS, vm.uiState.value.enteredCode.length)
    }

    @Test
    fun `EnteredCodeChanged with empty string is allowed`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("12"))
        vm.onIntent(HunterMapIntent.EnteredCodeChanged(""))
        assertEquals("", vm.uiState.value.enteredCode)
    }

    @Test
    fun `DismissFoundCodeEntry clears entered code`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.FoundButtonTapped)
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("99"))
        vm.onIntent(HunterMapIntent.DismissFoundCodeEntry)
        assertFalse(vm.uiState.value.isEnteringFoundCode)
        assertEquals("", vm.uiState.value.enteredCode)
    }

    @Test
    fun `LeaveGameTapped twice keeps alert showing (idempotent)`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.LeaveGameTapped)
        vm.onIntent(HunterMapIntent.LeaveGameTapped)
        assertTrue(vm.uiState.value.showLeaveAlert)
    }

    @Test
    fun `InfoTapped + DismissGameInfo cycles cleanly`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.InfoTapped)
        assertTrue(vm.uiState.value.showGameInfo)
        vm.onIntent(HunterMapIntent.DismissGameInfo)
        assertFalse(vm.uiState.value.showGameInfo)
        vm.onIntent(HunterMapIntent.InfoTapped)
        assertTrue(vm.uiState.value.showGameInfo)
    }

    @Test
    fun `PowerUpInventoryTapped opens then DismissPowerUpInventory closes`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.PowerUpInventoryTapped)
        assertTrue(vm.uiState.value.showPowerUpInventory)
        vm.onIntent(HunterMapIntent.DismissPowerUpInventory)
        assertFalse(vm.uiState.value.showPowerUpInventory)
    }

    @Test
    fun `VictoryNavigated resets shouldNavigateToVictory flag`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.shouldNavigateToVictory)
        vm.onIntent(HunterMapIntent.VictoryNavigated)
        assertFalse(vm.uiState.value.shouldNavigateToVictory)
    }

    @Test
    fun `DismissRegistrationRequiredAlert clears the flag`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.DismissRegistrationRequiredAlert)
        assertFalse(vm.uiState.value.showRegistrationRequiredAlert)
    }

    @Test
    fun `CodeCopied flips codeCopied to true then back to false after delay`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.CodeCopied)
        assertTrue(vm.uiState.value.codeCopied)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.codeCopied)
    }

    // MARK:, hasChallenges gate

    @Test
    fun `hasChallenges is false before any stream emission`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges is false when stream emits empty list`() {
        io.mockk.every { firestoreRepository.challengesStream() } returns
            kotlinx.coroutines.flow.flowOf(emptyList())
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges becomes true when stream emits non-empty list`() {
        val challenge = dev.rahier.pouleparty.model.Challenge(
            id = "c1", title = "Sing", body = "Sing loudly", points = 10
        )
        io.mockk.every { firestoreRepository.challengesStream() } returns
            kotlinx.coroutines.flow.flowOf(listOf(challenge))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges handles empty then non-empty transition`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow<List<dev.rahier.pouleparty.model.Challenge>>(emptyList())
        io.mockk.every { firestoreRepository.challengesStream() } returns flow
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)

        flow.value = listOf(dev.rahier.pouleparty.model.Challenge(id = "c1", title = "T", body = "B", points = 5))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges handles non-empty then empty transition`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow<List<dev.rahier.pouleparty.model.Challenge>>(
            listOf(dev.rahier.pouleparty.model.Challenge(id = "c1", title = "T", body = "B", points = 5))
        )
        io.mockk.every { firestoreRepository.challengesStream() } returns flow
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)

        flow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges reflects latest emission across multiple updates`() {
        val c1 = dev.rahier.pouleparty.model.Challenge(id = "c1", title = "T1", body = "B", points = 5)
        val c2 = dev.rahier.pouleparty.model.Challenge(id = "c2", title = "T2", body = "B", points = 10)
        val flow = kotlinx.coroutines.flow.MutableStateFlow(listOf(c1))
        io.mockk.every { firestoreRepository.challengesStream() } returns flow
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)

        flow.value = listOf(c1, c2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)

        flow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)

        flow.value = listOf(c2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges stays false when stream errors without emitting`() {
        io.mockk.every { firestoreRepository.challengesStream() } returns
            kotlinx.coroutines.flow.flow { throw RuntimeException("boom") }
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    // MARK:, addWinner retry flow

    @Test
    fun `submitFoundCode with correct code clears pendingWinner on success`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.shouldNavigateToVictory)
        // On success the VM must NOT keep the winner around, leaking it
        // would let a later retry double-write the same victory.
        assertNull(vm.uiState.value.pendingWinner)
        assertFalse(vm.uiState.value.winnerRegistrationFailed)
    }

    @Test
    fun `submitFoundCode with correct code surfaces retry alert when write throws`() {
        io.mockk.coEvery { firestoreRepository.addWinner(any(), any()) } coAnswers { throw RuntimeException("offline") }
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Hunter is NOT navigated to victory because the server has no record.
        assertFalse(vm.uiState.value.shouldNavigateToVictory)
        assertTrue(vm.uiState.value.winnerRegistrationFailed)
        // pendingWinner must be kept so Retry can reuse the original Winner
        // (same timestamp, same attempt count).
        val pending = vm.uiState.value.pendingWinner
        assertNotNull(pending)
        assertEquals("hunter-123", pending!!.hunterId)
    }

    @Test
    fun `retryWinnerRegistration after transient failure eventually navigates on success`() {
        // First write throws, second succeeds, simulates a flaky Firestore.
        var callCount = 0
        io.mockk.coEvery { firestoreRepository.addWinner(any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) throw RuntimeException("transient") else Unit
        }
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.winnerRegistrationFailed)
        assertFalse(vm.uiState.value.shouldNavigateToVictory)
        val originalWinner = vm.uiState.value.pendingWinner
        assertNotNull(originalWinner)

        vm.onIntent(HunterMapIntent.RetryWinnerRegistration)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.shouldNavigateToVictory)
        assertFalse(vm.uiState.value.winnerRegistrationFailed)
        assertNull(vm.uiState.value.pendingWinner)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryWinnerRegistration preserves original Winner across retries`() {
        val winnersCaptured = mutableListOf<dev.rahier.pouleparty.model.Winner>()
        io.mockk.coEvery { firestoreRepository.addWinner(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            winnersCaptured.add(secondArg<dev.rahier.pouleparty.model.Winner>())
            throw RuntimeException("offline")
        }

        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HunterMapIntent.RetryWinnerRegistration)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onIntent(HunterMapIntent.RetryWinnerRegistration)
        testDispatcher.scheduler.advanceUntilIdle()

        // Same Winner (same timestamp, same hunter) must be submitted on
        // every retry, otherwise we'd log "found at time X" the first time
        // and "found at time X+30s" on retry, corrupting analytics.
        assertEquals(3, winnersCaptured.size)
        val first = winnersCaptured[0]
        assertEquals(first.hunterId, winnersCaptured[1].hunterId)
        assertEquals(first.timestamp, winnersCaptured[1].timestamp)
        assertEquals(first.hunterId, winnersCaptured[2].hunterId)
        assertEquals(first.timestamp, winnersCaptured[2].timestamp)
    }

    @Test
    fun `dismissWinnerRegistrationError hides alert but keeps pendingWinner`() {
        io.mockk.coEvery { firestoreRepository.addWinner(any(), any()) } coAnswers { throw RuntimeException("offline") }
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.winnerRegistrationFailed)
        assertNotNull(vm.uiState.value.pendingWinner)

        vm.onIntent(HunterMapIntent.DismissWinnerRegistrationError)
        assertFalse(vm.uiState.value.winnerRegistrationFailed)
        // Pending winner is preserved → the hunter can still retry later.
        assertNotNull(vm.uiState.value.pendingWinner)
    }

    @Test
    fun `retryWinnerRegistration without pendingWinner is a no-op`() {
        // Defensive: no winner in state (e.g. retry tapped after alert already
        // dismissed and something else cleared it). Must not crash or fire
        // addWinner.
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.RetryWinnerRegistration)
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) { firestoreRepository.addWinner(any(), any()) }
        assertFalse(vm.uiState.value.shouldNavigateToVictory)
    }

    // MARK:, Zone-center overwrite regression (bug 2)
    //
    // In `stayInTheZone`, a stray chicken broadcast (radar ping, or a stale
    // `chickenLocations/latest` doc replayed by Firestore on listener
    // connect) used to overwrite `circleCenter` with the chicken's
    // position. The zone check then fired against the chicken's position
    // rather than the real drifted center and flagged the hunter as
    // "outside" even standing inside the visible circle. Fix lives in
    // `HunterMapViewModel.streamChickenLocation`.

    @Test
    fun `chicken broadcast in stayInTheZone does NOT move circleCenter`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.STAY_IN_THE_ZONE.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 60_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            zone = dev.rahier.pouleparty.model.Zone(
                center = com.google.firebase.firestore.GeoPoint(50.8500, 4.3500),
                finalCenter = com.google.firebase.firestore.GeoPoint(50.8501, 4.3501),
                radius = 1500.0
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        // Emit the game via gameConfigFlow so streamGameConfig seeds the
        // drifted initial circleCenter — otherwise circleCenter stays
        // null forever in stayInTheZone and the assertion is meaningless.
        io.mockk.every { firestoreRepository.gameConfigFlow(any()) } returns
            kotlinx.coroutines.flow.flowOf(game)

        // Emit a chicken position 2 km away — this MUST NOT become the
        // zone center in stayInTheZone.
        val strayChicken = com.mapbox.geojson.Point.fromLngLat(4.3700, 50.8700)
        io.mockk.every { firestoreRepository.chickenLocationFlow(any()) } returns
            kotlinx.coroutines.flow.flowOf(strayChicken)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent() // let init launch children
        // The VM's 1 Hz game-timer loop means `advanceUntilIdle` would run
        // forever; only advance enough virtual time for the single chicken
        // emit + the gameConfig initial pass to land in state.
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()

        val centerNow = vm.uiState.value.circleCenter
        assertNotNull("circleCenter should be initialised by gameConfigFlow", centerNow)
        assertFalse(
            "circleCenter was overwritten by a stray chicken broadcast in stayInTheZone",
            centerNow?.latitude() == strayChicken.latitude() &&
                centerNow?.longitude() == strayChicken.longitude()
        )
    }

    @Test
    fun `chicken broadcast in followTheChicken DOES move circleCenter`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 60_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            zone = dev.rahier.pouleparty.model.Zone(
                center = com.google.firebase.firestore.GeoPoint(50.8500, 4.3500),
                radius = 1500.0
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game

        val chickenPos = com.mapbox.geojson.Point.fromLngLat(4.3700, 50.8700)
        io.mockk.every { firestoreRepository.chickenLocationFlow(any()) } returns
            kotlinx.coroutines.flow.flowOf(chickenPos)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()

        val centerNow = vm.uiState.value.circleCenter
        assertEquals(chickenPos.latitude(), centerNow?.latitude())
        assertEquals(chickenPos.longitude(), centerNow?.longitude())
    }

    // MARK:, First-write throttle regression (bug 3)
    //
    // The initial `setHunterLocation` used to be gated behind
    // `locationRepository.getLastLocation()` returning a cached fix. If
    // it returned null, the throttle was already armed at `Date.now` and
    // the FIRST coord from `locationFlow` was blocked for 5 s. Result:
    // in a small 2-device test, the hunter marker stayed invisible on
    // the chicken's map for the first several seconds even though the
    // hunter was moving. Fix: keep `lastWrite` at epoch until the first
    // successful write, so the first `locationFlow` emit triggers an
    // unthrottled write.
    @Test
    fun `first locationFlow emit writes immediately when getLastLocation is null`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            chickenCanSeeHunters = true,
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 60_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            zone = dev.rahier.pouleparty.model.Zone(
                center = com.google.firebase.firestore.GeoPoint(50.8500, 4.3500),
                radius = 1500.0
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns null

        val firstPoint = com.mapbox.geojson.Point.fromLngLat(4.3500, 50.8500)
        io.mockk.every { locationRepository.locationFlow() } returns
            kotlinx.coroutines.flow.flowOf(firstPoint)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent()
        // Crucially DON'T advance past the 5 s throttle window — the
        // whole point of the fix is that the first emit is unthrottled.
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(atLeast = 1) {
            firestoreRepository.setHunterLocation(eq("test-id"), any(), eq(firstPoint))
        }
    }
}
