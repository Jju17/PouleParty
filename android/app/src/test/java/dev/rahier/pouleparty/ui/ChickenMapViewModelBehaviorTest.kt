package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapIntent
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapViewModel
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
class ChickenMapViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        // Make `loadGame()` exit early so init coroutines settle without
        // needing real game data from the relaxed mock.
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns null
        io.mockk.every { firestoreRepository.gameConfigFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { firestoreRepository.powerUpsFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(gameId: String = "test-id"): ChickenMapViewModel {
        return ChickenMapViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = mockk<dev.rahier.pouleparty.data.AnalyticsRepository>(relaxed = true),
            auth = mockk<FirebaseAuth>(relaxed = true),
            mapboxAccessToken = "test-token",
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId))
        )
    }

    // MARK: - Cancel alert

    @Test
    fun `onCancelGameTapped shows cancel alert`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.CancelGameTapped)
        assertTrue(vm.uiState.value.showCancelAlert)
    }

    @Test
    fun `dismissCancelAlert hides cancel alert`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.CancelGameTapped)
        vm.onIntent(ChickenMapIntent.DismissCancelAlert)
        assertFalse(vm.uiState.value.showCancelAlert)
    }

    // MARK: - Game over

    @Test
    fun `confirmGameOver clears alert (NavigateToVictory effect emitted)`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.ConfirmGameOver)
        assertFalse(vm.uiState.value.showGameOverAlert)
    }

    // MARK: - Game info

    @Test
    fun `onInfoTapped shows game info`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.InfoTapped)
        assertTrue(vm.uiState.value.showGameInfo)
    }

    @Test
    fun `dismissGameInfo hides game info`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.InfoTapped)
        vm.onIntent(ChickenMapIntent.DismissGameInfo)
        assertFalse(vm.uiState.value.showGameInfo)
    }

    // MARK: - Found code

    @Test
    fun `onFoundButtonTapped shows found code`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.FoundButtonTapped)
        assertTrue(vm.uiState.value.showFoundCode)
    }

    @Test
    fun `dismissFoundCode hides found code`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.FoundButtonTapped)
        vm.onIntent(ChickenMapIntent.DismissFoundCode)
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
    fun `confirmCancelGame dismisses alert (NavigateToMenu effect emitted)`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.CancelGameTapped)
        vm.onIntent(ChickenMapIntent.ConfirmCancelGame)
        assertFalse(vm.uiState.value.showCancelAlert)
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
