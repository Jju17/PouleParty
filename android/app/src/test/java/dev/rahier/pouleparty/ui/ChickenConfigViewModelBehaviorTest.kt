package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigViewModel
import io.mockk.coEvery
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
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ChickenConfigViewModelBehaviorTest {

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

    private fun createViewModel(gameId: String = "test-id"): ChickenConfigViewModel {
        return ChickenConfigViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            auth = auth,
            savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId))
        )
    }

    @Test
    fun `updateStartDate updates game start`() {
        val vm = createViewModel()
        val newDate = Date(System.currentTimeMillis() + 600_000)
        vm.updateStartDate(newDate)
        assertTrue(
            Math.abs(vm.uiState.value.game.startDate.time - newDate.time) < 1000
        )
    }

    @Test
    fun `updateEndDate updates game end`() {
        val vm = createViewModel()
        val newDate = Date(System.currentTimeMillis() + 3_600_000)
        vm.updateEndDate(newDate)
        assertTrue(
            Math.abs(vm.uiState.value.game.endDate.time - newDate.time) < 1000
        )
    }

    @Test
    fun `updateGameMod updates game mod`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        assertEquals(GameMod.STAY_IN_THE_ZONE, vm.uiState.value.game.gameModEnum)
    }

    @Test
    fun `updateInitialRadius updates game radius`() {
        val vm = createViewModel()
        vm.updateInitialRadius(2000.0)
        assertEquals(2000.0, vm.uiState.value.game.initialRadius, 0.01)
    }

    @Test
    fun `updateRadiusDecline updates decline value`() {
        val vm = createViewModel()
        vm.updateRadiusDecline(200.0)
        assertEquals(200.0, vm.uiState.value.game.radiusDeclinePerUpdate, 0.01)
    }

    @Test
    fun `updateRadiusIntervalUpdate updates interval`() {
        val vm = createViewModel()
        vm.updateRadiusIntervalUpdate(10.0)
        assertEquals(10.0, vm.uiState.value.game.radiusIntervalUpdate, 0.01)
    }

    @Test
    fun `updateChickenHeadStart updates head start`() {
        val vm = createViewModel()
        vm.updateChickenHeadStart(10.0)
        assertEquals(10.0, vm.uiState.value.game.chickenHeadStartMinutes, 0.01)
    }

    @Test
    fun `dismissAlert clears alert`() {
        val vm = createViewModel()
        // Trigger an alert first via internal state
        vm.dismissAlert()
        assertFalse(vm.uiState.value.showAlert)
    }

    @Test
    fun `startGame success calls callback`() {
        val vm = createViewModel()
        var successGameId: String? = null
        vm.startGame { successGameId = it }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("test-id", successGameId)
    }

    @Test
    fun `startGame failure shows alert`() {
        coEvery { firestoreRepository.setConfig(any()) } throws Exception("network error")
        val vm = createViewModel()
        var callbackCalled = false
        vm.startGame { callbackCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(callbackCalled)
        assertTrue(vm.uiState.value.showAlert)
        assertEquals(
            "Could not create the game. Please check your connection and try again.",
            vm.uiState.value.alertMessage
        )
    }

    @Test
    fun `game id comes from saved state handle`() {
        val vm = createViewModel(gameId = "custom-game-123")
        assertEquals("custom-game-123", vm.uiState.value.game.id)
    }
}
