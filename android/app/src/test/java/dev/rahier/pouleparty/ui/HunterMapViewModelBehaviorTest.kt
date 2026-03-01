package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.huntermap.HunterMapViewModel
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
            auth = auth,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId, "hunterName" to hunterName))
        )
    }

    // MARK: - Found code entry

    @Test
    fun `onFoundButtonTapped shows code entry`() {
        val vm = createViewModel()
        vm.onFoundButtonTapped()
        assertTrue(vm.uiState.value.isEnteringFoundCode)
    }

    @Test
    fun `dismissFoundCodeEntry clears code and hides entry`() {
        val vm = createViewModel()
        vm.onFoundButtonTapped()
        vm.onEnteredCodeChanged("123")
        vm.dismissFoundCodeEntry()
        assertFalse(vm.uiState.value.isEnteringFoundCode)
        assertEquals("", vm.uiState.value.enteredCode)
    }

    @Test
    fun `onEnteredCodeChanged truncates to max digits`() {
        val vm = createViewModel()
        vm.onEnteredCodeChanged("123456789")
        assertEquals(AppConstants.FOUND_CODE_DIGITS, vm.uiState.value.enteredCode.length)
    }

    @Test
    fun `onEnteredCodeChanged keeps short codes as-is`() {
        val vm = createViewModel()
        vm.onEnteredCodeChanged("12")
        assertEquals("12", vm.uiState.value.enteredCode)
    }

    // MARK: - Submit found code

    @Test
    fun `submitFoundCode wrong code shows alert`() {
        val vm = createViewModel()
        vm.onEnteredCodeChanged("9999")
        vm.submitFoundCode()
        assertTrue(vm.uiState.value.showWrongCodeAlert)
        assertEquals("", vm.uiState.value.enteredCode)
        assertFalse(vm.uiState.value.isEnteringFoundCode)
    }

    @Test
    fun `submitFoundCode wrong code increments attempts`() {
        val vm = createViewModel()
        vm.onEnteredCodeChanged("9999")
        vm.submitFoundCode()
        assertEquals(1, vm.uiState.value.wrongCodeAttempts)
    }

    @Test
    fun `submitFoundCode triggers cooldown after max attempts`() {
        val vm = createViewModel()
        repeat(AppConstants.CODE_MAX_WRONG_ATTEMPTS) {
            vm.dismissWrongCodeAlert()
            vm.onEnteredCodeChanged("9999")
            vm.submitFoundCode()
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
            vm.dismissWrongCodeAlert()
            vm.onEnteredCodeChanged("9999")
            vm.submitFoundCode()
        }
        // Now on cooldown, try submitting again
        vm.onEnteredCodeChanged("1234")
        vm.submitFoundCode()
        // Code should still be "1234" (submitFoundCode returned early, didn't clear it)
        assertEquals("1234", vm.uiState.value.enteredCode)
    }

    @Test
    fun `submitFoundCode correct code navigates to victory`() {
        val vm = createViewModel()
        // Game.mock has foundCode = "1234"
        vm.onEnteredCodeChanged("1234")
        vm.submitFoundCode()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.shouldNavigateToVictory)
    }

    // MARK: - Wrong code alert

    @Test
    fun `dismissWrongCodeAlert clears alert`() {
        val vm = createViewModel()
        vm.onEnteredCodeChanged("9999")
        vm.submitFoundCode()
        assertTrue(vm.uiState.value.showWrongCodeAlert)
        vm.dismissWrongCodeAlert()
        assertFalse(vm.uiState.value.showWrongCodeAlert)
    }

    // MARK: - Leave game

    @Test
    fun `onLeaveGameTapped shows leave alert`() {
        val vm = createViewModel()
        vm.onLeaveGameTapped()
        assertTrue(vm.uiState.value.showLeaveAlert)
    }

    @Test
    fun `dismissLeaveAlert hides leave alert`() {
        val vm = createViewModel()
        vm.onLeaveGameTapped()
        vm.dismissLeaveAlert()
        assertFalse(vm.uiState.value.showLeaveAlert)
    }

    @Test
    fun `confirmLeaveGame clears alert and calls callback`() {
        val vm = createViewModel()
        vm.onLeaveGameTapped()
        var menuCalled = false
        vm.confirmLeaveGame { menuCalled = true }
        assertFalse(vm.uiState.value.showLeaveAlert)
        assertTrue(menuCalled)
    }

    // MARK: - Game over

    @Test
    fun `confirmGameOver clears alert and calls callback`() {
        val vm = createViewModel()
        var menuCalled = false
        vm.confirmGameOver { menuCalled = true }
        assertFalse(vm.uiState.value.showGameOverAlert)
        assertTrue(menuCalled)
    }

    // MARK: - Game info

    @Test
    fun `onInfoTapped shows game info`() {
        val vm = createViewModel()
        vm.onInfoTapped()
        assertTrue(vm.uiState.value.showGameInfo)
    }

    @Test
    fun `dismissGameInfo hides game info`() {
        val vm = createViewModel()
        vm.onInfoTapped()
        vm.dismissGameInfo()
        assertFalse(vm.uiState.value.showGameInfo)
    }

    // MARK: - Hunter properties

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
}
