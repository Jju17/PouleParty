package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapViewModel
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
class ChickenMapViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(gameId: String = "test-id"): ChickenMapViewModel {
        return ChickenMapViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId))
        )
    }

    // MARK: - Cancel alert

    @Test
    fun `onCancelGameTapped shows cancel alert`() {
        val vm = createViewModel()
        vm.onCancelGameTapped()
        assertTrue(vm.uiState.value.showCancelAlert)
    }

    @Test
    fun `dismissCancelAlert hides cancel alert`() {
        val vm = createViewModel()
        vm.onCancelGameTapped()
        vm.dismissCancelAlert()
        assertFalse(vm.uiState.value.showCancelAlert)
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

    // MARK: - Found code

    @Test
    fun `onFoundButtonTapped shows found code`() {
        val vm = createViewModel()
        vm.onFoundButtonTapped()
        assertTrue(vm.uiState.value.showFoundCode)
    }

    @Test
    fun `dismissFoundCode hides found code`() {
        val vm = createViewModel()
        vm.onFoundButtonTapped()
        vm.dismissFoundCode()
        assertFalse(vm.uiState.value.showFoundCode)
    }

    // MARK: - Chicken subtitle

    @Test
    fun `chickenSubtitle for followTheChicken`() {
        val vm = createViewModel()
        // Default game mock is followTheChicken
        assertEquals("Don't be seen !", vm.chickenSubtitle)
    }

    // MARK: - Confirm cancel game

    @Test
    fun `confirmCancelGame dismisses alert and calls callback`() {
        val vm = createViewModel()
        vm.onCancelGameTapped()
        var menuCalled = false
        vm.confirmCancelGame { menuCalled = true }
        assertFalse(vm.uiState.value.showCancelAlert)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(menuCalled)
    }
}
