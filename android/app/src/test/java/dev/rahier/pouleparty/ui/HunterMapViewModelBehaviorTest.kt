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
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        // CRIT-2/CRIT-3 (audit 2026-05-17): the foundCode check is now
        // server-side via `submitFoundCode`. Default the mock to accept
        // only the Game.mock canonical code "1234" — every other code
        // gets a `InvalidCode` rejection, mirroring the real CF. Tests
        // that need throw-based failure modes override per-test.
        io.mockk.coEvery {
            firestoreRepository.submitFoundCode(any(), any(), any())
        } coAnswers {
            if (secondArg<String>() == "1234") {
                FirestoreRepository.SubmitFoundCodeResult.Success
            } else {
                FirestoreRepository.SubmitFoundCodeResult.Failure(
                    FirestoreRepository.SubmitFoundCodeReason.InvalidCode
                )
            }
        }
        // CRIT-2: chicken-side foundCode fetch (harmless for hunter VM
        // tests since it's only invoked from ChickenMapViewModel; the
        // relaxed mock returns "" by default which is fine).
        io.mockk.coEvery { firestoreRepository.getFoundCode(any()) } returns "1234"
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
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId, "hunterName" to hunterName)),
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
        // CRIT-2 (audit 2026-05-17): wrong-code check is now server-side;
        // advance the dispatcher so the CF mock resolves before asserting.
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.showWrongCodeAlert)
        assertEquals("", vm.uiState.value.enteredCode)
        assertFalse(vm.uiState.value.isEnteringFoundCode)
    }

    @Test
    fun `submitFoundCode wrong code increments attempts`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.wrongCodeAttempts)
    }

    @Test
    fun `submitFoundCode triggers cooldown after max attempts`() {
        val vm = createViewModel()
        repeat(AppConstants.CODE_MAX_WRONG_ATTEMPTS) {
            vm.onIntent(HunterMapIntent.DismissWrongCodeAlert)
            vm.onIntent(HunterMapIntent.EnteredCodeChanged("9999"))
            vm.onIntent(HunterMapIntent.SubmitFoundCode)
            testDispatcher.scheduler.advanceUntilIdle()
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
            testDispatcher.scheduler.advanceUntilIdle()
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
        // Game.mock has foundCode = "1234"; the default mock accepts it.
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
        testDispatcher.scheduler.advanceUntilIdle()
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
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns
            kotlinx.coroutines.flow.flowOf(emptyList())
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges becomes true when stream emits non-empty list`() {
        val challenge = dev.rahier.pouleparty.model.Challenge(
            id = "c1",
            points = 10,
            titleByLocale = mapOf("fr" to "Sing"),
            bodyByLocale = mapOf("fr" to "Sing loudly"),
        )
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns
            kotlinx.coroutines.flow.flowOf(listOf(challenge))
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges handles empty then non-empty transition`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow<List<dev.rahier.pouleparty.model.Challenge>>(emptyList())
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns flow
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)

        flow.value = listOf(dev.rahier.pouleparty.model.Challenge(id = "c1", points = 5, titleByLocale = mapOf("fr" to "T")))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges handles non-empty then empty transition`() {
        val flow = kotlinx.coroutines.flow.MutableStateFlow<List<dev.rahier.pouleparty.model.Challenge>>(
            listOf(dev.rahier.pouleparty.model.Challenge(id = "c1", points = 5, titleByLocale = mapOf("fr" to "T")))
        )
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns flow
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.hasChallenges)

        flow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    @Test
    fun `hasChallenges reflects latest emission across multiple updates`() {
        val c1 = dev.rahier.pouleparty.model.Challenge(id = "c1", points = 5, titleByLocale = mapOf("fr" to "T1"))
        val c2 = dev.rahier.pouleparty.model.Challenge(id = "c2", points = 10, titleByLocale = mapOf("fr" to "T2"))
        val flow = kotlinx.coroutines.flow.MutableStateFlow(listOf(c1))
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns flow
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
        io.mockk.every { firestoreRepository.challengesStream(any()) } returns
            kotlinx.coroutines.flow.flow { throw RuntimeException("boom") }
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.hasChallenges)
    }

    // MARK:, submitFoundCode retry flow (CRIT-3 audit 2026-05-17)

    @Test
    fun `submitFoundCode with correct code clears pendingFoundCode on success`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.shouldNavigateToVictory)
        // On success the VM must NOT keep the pending code around, leaking
        // it would let a later retry double-call the CF (idempotent server-
        // side but wastes a round-trip).
        assertNull(vm.uiState.value.pendingFoundCode)
        assertFalse(vm.uiState.value.winnerRegistrationFailed)
    }

    @Test
    fun `submitFoundCode with correct code surfaces retry alert when CF throws`() {
        io.mockk.coEvery {
            firestoreRepository.submitFoundCode(any(), any(), any())
        } coAnswers { throw RuntimeException("offline") }
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()

        // Hunter is NOT navigated to victory because the server has no record.
        assertFalse(vm.uiState.value.shouldNavigateToVictory)
        assertTrue(vm.uiState.value.winnerRegistrationFailed)
        // pendingFoundCode must be kept so Retry can reuse the same code.
        assertEquals("1234", vm.uiState.value.pendingFoundCode)
    }

    @Test
    fun `retryWinnerRegistration after transient failure eventually navigates on success`() {
        var callCount = 0
        io.mockk.coEvery {
            firestoreRepository.submitFoundCode(any(), any(), any())
        } coAnswers {
            callCount++
            if (callCount == 1) throw RuntimeException("transient")
            else FirestoreRepository.SubmitFoundCodeResult.Success
        }
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.winnerRegistrationFailed)
        assertFalse(vm.uiState.value.shouldNavigateToVictory)
        assertEquals("1234", vm.uiState.value.pendingFoundCode)

        vm.onIntent(HunterMapIntent.RetryWinnerRegistration)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.shouldNavigateToVictory)
        assertFalse(vm.uiState.value.winnerRegistrationFailed)
        assertNull(vm.uiState.value.pendingFoundCode)
        assertEquals(2, callCount)
    }

    @Test
    fun `retryWinnerRegistration re-sends same code and name across retries`() {
        // CRIT-3: the user-typed code must be the SAME on each retry. The
        // server stamps the winner's timestamp at success, so we don't
        // preserve a Winner across retries anymore; but if the retry sent
        // a different code, the hunter could in theory submit a wrong code
        // after the typed one was forgotten.
        val codesCaptured = mutableListOf<String>()
        val namesCaptured = mutableListOf<String>()
        io.mockk.coEvery {
            firestoreRepository.submitFoundCode(any(), any(), any())
        } answers {
            codesCaptured.add(secondArg<String>())
            namesCaptured.add(thirdArg<String>())
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

        assertEquals(3, codesCaptured.size)
        assertEquals(codesCaptured[0], codesCaptured[1])
        assertEquals(codesCaptured[0], codesCaptured[2])
        assertEquals(namesCaptured[0], namesCaptured[1])
        assertEquals(namesCaptured[0], namesCaptured[2])
    }

    @Test
    fun `dismissWinnerRegistrationError hides alert but keeps pendingFoundCode`() {
        io.mockk.coEvery {
            firestoreRepository.submitFoundCode(any(), any(), any())
        } coAnswers { throw RuntimeException("offline") }
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.winnerRegistrationFailed)
        assertEquals("1234", vm.uiState.value.pendingFoundCode)

        vm.onIntent(HunterMapIntent.DismissWinnerRegistrationError)
        assertFalse(vm.uiState.value.winnerRegistrationFailed)
        // Pending code is preserved → the hunter can still retry later.
        assertEquals("1234", vm.uiState.value.pendingFoundCode)
    }

    @Test
    fun `retryWinnerRegistration without pendingFoundCode is a no-op`() {
        val vm = createViewModel()

        vm.onIntent(HunterMapIntent.RetryWinnerRegistration)
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) {
            firestoreRepository.submitFoundCode(any(), any(), any())
        }
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
        val strayChickenLoc = dev.rahier.pouleparty.model.ChickenLocation(
            location = com.google.firebase.firestore.GeoPoint(strayChicken.latitude(), strayChicken.longitude()),
            timestamp = com.google.firebase.Timestamp.now(),
        )
        io.mockk.every { firestoreRepository.chickenLocationFlow(any()) } returns
            kotlinx.coroutines.flow.flowOf(strayChickenLoc)

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
        val chickenLoc = dev.rahier.pouleparty.model.ChickenLocation(
            location = com.google.firebase.firestore.GeoPoint(chickenPos.latitude(), chickenPos.longitude()),
            timestamp = com.google.firebase.Timestamp.now(),
        )
        io.mockk.every { firestoreRepository.chickenLocationFlow(any()) } returns
            kotlinx.coroutines.flow.flowOf(chickenLoc)

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
    // MARK: - PP-19 end-game stays on map
    //
    // Hunter mirror of `ChickenMapViewModelBehaviorTest` PP-19 block.
    // The map stays mounted at gameOver; `isGameOver` flips to true;
    // GPS writes stop. Only `winnerRegistered` (scenario 4) keeps the
    // Victory transition. The found code stays active after gameOver
    // (scenario 6, per PP-2).

    /** Scenario 1 (hunter): timeout — `nowDate >= endDate` flips
     *  `isGameOver` and shows the alert. No auto-transition. */
    @Test
    fun `pp19 timeout flips isGameOver and shows alert without transition`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 3_600_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now - 1_000))
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game

        val vm = createViewModel()
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()

        assertTrue("isGameOver must flip true on timeout", vm.uiState.value.isGameOver)
        assertTrue("gameOver alert must show", vm.uiState.value.showGameOverAlert)
        assertFalse("must NOT auto-transition to Victory", vm.uiState.value.shouldNavigateToVictory)
    }

    /** Scenario 2 (hunter): zone collapse flips `isGameOver`. No
     *  auto-transition; `gameOverMessage` reflects the collapse. */
    @Test
    fun `pp19 zone collapse flips isGameOver and stops streams`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 3_600_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            zone = dev.rahier.pouleparty.model.Zone(
                center = com.google.firebase.firestore.GeoPoint(50.8466, 4.3528),
                radius = 100.0,
                shrinkIntervalMinutes = 0.0,
                shrinkMetersPerUpdate = 100.0
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game

        val vm = createViewModel()
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()

        assertTrue("isGameOver must flip true on zone collapse", vm.uiState.value.isGameOver)
        assertTrue("gameOver alert must show", vm.uiState.value.showGameOverAlert)
        assertEquals("The zone has collapsed!", vm.uiState.value.gameOverMessage)
        assertFalse("must NOT auto-transition to Victory", vm.uiState.value.shouldNavigateToVictory)
    }

    /** Scenario 3 (hunter side): all hunters found → `isGameOver`
     *  flips locally (chicken is authoritative for the DONE write,
     *  but hunter still recognises the end-state). */
    @Test
    fun `pp19 all hunters found flips isGameOver on hunter side`() {
        val now = System.currentTimeMillis()
        // Two hunters, both already in winners → all-hunters-found path.
        val winners = listOf(
            dev.rahier.pouleparty.model.Winner(
                hunterId = "hunter-123",
                hunterName = "Me",
                timestamp = com.google.firebase.Timestamp.now()
            ),
            dev.rahier.pouleparty.model.Winner(
                hunterId = "other-hunter",
                hunterName = "Other",
                timestamp = com.google.firebase.Timestamp.now()
            )
        )
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            hunterIds = listOf("hunter-123", "other-hunter"),
            winners = winners,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 3_600_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        // Push the same game through gameConfigFlow so the all-hunters-found
        // branch in streamGameConfig fires.
        io.mockk.every { firestoreRepository.gameConfigFlow(any()) } returns
            kotlinx.coroutines.flow.flowOf(game)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()

        assertTrue("isGameOver must flip when winners ≥ hunterIds", vm.uiState.value.isGameOver)
        assertFalse("must NOT auto-transition to Victory", vm.uiState.value.shouldNavigateToVictory)
    }

    /** Scenario 4 (PP-16 exception): an individual hunter entering the
     *  correct found code triggers the transition to Victory — the
     *  personal-win path is preserved. */
    @Test
    fun `pp19 winnerRegistered keeps the Victory transition`() {
        val vm = createViewModel()
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Personal win still navigates to Victory", vm.uiState.value.shouldNavigateToVictory)
    }

    /** Scenario 6 (PP-2): the FOUND code stays active for the hunter
     *  even after `isGameOver` is set, so a straggler can still close
     *  the loop. */
    @Test
    fun `pp19 found code still works after isGameOver flips`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            foundCode = "1234",
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 3_600_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now - 1_000))
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game

        val vm = createViewModel()
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()
        assertTrue("Precondition: isGameOver true", vm.uiState.value.isGameOver)

        // Late entry of the found code still records the winner.
        vm.onIntent(HunterMapIntent.EnteredCodeChanged("1234"))
        vm.onIntent(HunterMapIntent.SubmitFoundCode)
        testDispatcher.scheduler.advanceUntilIdle()

        // The submit path is NOT gated on isGameOver — submitFoundCode is
        // dispatched and the hunter heads to Victory (their personal win
        // path stays open per PP-2).
        assertTrue(
            "Found code must stay active after gameOver",
            vm.uiState.value.shouldNavigateToVictory
        )
        io.mockk.coVerify(atLeast = 1) {
            firestoreRepository.submitFoundCode(eq("test-id"), any(), any())
        }
    }

    /** Scenario 5 (hunter): once `isGameOver` is set, no further
     *  `setHunterLocation` calls fire. The streams are cancelled the
     *  moment `cancelStreams` runs alongside the `isGameOver` flip. */
    @Test
    fun `pp19 GPS writes stop after isGameOver flips on timeout`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            chickenCanSeeHunters = true,
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 3_600_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now - 1_000))
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns null

        // Hot location flow that emits AFTER gameOver fires.
        val locationFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.mapbox.geojson.Point>(replay = 0)
        io.mockk.every { locationRepository.locationFlow() } returns locationFlow

        val vm = createViewModel()
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()
        assertTrue("Precondition: isGameOver true", vm.uiState.value.isGameOver)

        kotlinx.coroutines.runBlocking {
            locationFlow.emit(com.mapbox.geojson.Point.fromLngLat(4.3600, 50.8500))
        }
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(exactly = 0) {
            firestoreRepository.setHunterLocation(any(), any(), any())
        }
    }

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
