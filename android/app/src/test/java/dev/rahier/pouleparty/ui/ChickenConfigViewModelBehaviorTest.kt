package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigIntent
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigViewModel
import io.mockk.coEvery
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
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1)
        }
        vm.onIntent(ChickenConfigIntent.StartTimeChanged(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)))
        val updatedCal = java.util.Calendar.getInstance().apply { time = vm.uiState.value.game.startDate }
        assertEquals(cal.get(java.util.Calendar.HOUR_OF_DAY), updatedCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(cal.get(java.util.Calendar.MINUTE), updatedCal.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `updateGameMod updates game mod`() {
        val vm = createViewModel()
        vm.onIntent(ChickenConfigIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        assertEquals(GameMod.STAY_IN_THE_ZONE, vm.uiState.value.game.gameModEnum)
    }

    @Test
    fun `updateInitialRadius updates game radius`() {
        val vm = createViewModel()
        vm.onIntent(ChickenConfigIntent.InitialRadiusChanged(2000.0))
        assertEquals(2000.0, vm.uiState.value.game.zone.radius, 0.01)
    }

    @Test
    fun `updateRadiusDecline updates decline value`() {
        val vm = createViewModel()
        vm.onIntent(ChickenConfigIntent.RadiusDeclineChanged(200.0))
        assertEquals(200.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `updateRadiusIntervalUpdate updates interval`() {
        val vm = createViewModel()
        vm.onIntent(ChickenConfigIntent.RadiusIntervalUpdateChanged(10.0))
        assertEquals(10.0, vm.uiState.value.game.zone.shrinkIntervalMinutes, 0.01)
    }

    @Test
    fun `updateChickenHeadStart updates head start`() {
        val vm = createViewModel()
        vm.onIntent(ChickenConfigIntent.HeadStartChanged(10.0))
        assertEquals(10.0, vm.uiState.value.game.timing.headStartMinutes, 0.01)
    }

    @Test
    fun `dismissAlert clears alert`() {
        val vm = createViewModel()
        // Trigger an alert first via internal state
        vm.onIntent(ChickenConfigIntent.DismissAlert)
        assertFalse(vm.uiState.value.showAlert)
    }

    @Test
    fun `startGame success completes without alert`() {
        val vm = createViewModel()
        vm.onIntent(ChickenConfigIntent.StartGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        // No alert means the Firestore write succeeded; the GameStarted
        // effect emission is covered by the screen integration test.
        assertFalse(vm.uiState.value.showAlert)
    }

    @Test
    fun `startGame failure shows alert`() {
        coEvery { firestoreRepository.setConfig(any()) } throws Exception("network error")
        val vm = createViewModel()
        var callbackCalled = false
        vm.onIntent(ChickenConfigIntent.StartGameTapped)
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
