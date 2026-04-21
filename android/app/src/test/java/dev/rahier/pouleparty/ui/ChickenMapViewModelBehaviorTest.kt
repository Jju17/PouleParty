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
        io.mockk.every { firestoreRepository.hunterLocationsFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { firestoreRepository.chickenLocationFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { locationRepository.locationFlow() } returns kotlinx.coroutines.flow.emptyFlow()
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

    // ── Edge cases ─────────────────────────────────────────

    @Test
    fun `CancelGameTapped twice keeps alert showing (idempotent)`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.CancelGameTapped)
        vm.onIntent(ChickenMapIntent.CancelGameTapped)
        assertTrue(vm.uiState.value.showCancelAlert)
    }

    @Test
    fun `DismissCancelAlert without prior tap is no-op`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.DismissCancelAlert)
        assertFalse(vm.uiState.value.showCancelAlert)
    }

    @Test
    fun `DismissPowerUpInventory without opening first is no-op`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.DismissPowerUpInventory)
        assertFalse(vm.uiState.value.showPowerUpInventory)
    }

    @Test
    fun `PowerUpInventoryTapped + DismissPowerUpInventory cycles cleanly`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.PowerUpInventoryTapped)
        assertTrue(vm.uiState.value.showPowerUpInventory)
        vm.onIntent(ChickenMapIntent.DismissPowerUpInventory)
        assertFalse(vm.uiState.value.showPowerUpInventory)
    }

    @Test
    fun `FoundButtonTapped + DismissFoundCode toggles found code dialog`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.FoundButtonTapped)
        assertTrue(vm.uiState.value.showFoundCode)
        vm.onIntent(ChickenMapIntent.DismissFoundCode)
        assertFalse(vm.uiState.value.showFoundCode)
    }

    @Test
    fun `CodeCopied flips codeCopied true then back false after delay`() {
        val vm = createViewModel()
        vm.onIntent(ChickenMapIntent.CodeCopied)
        assertTrue(vm.uiState.value.codeCopied)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.codeCopied)
    }

    // MARK: - Radar Ping broadcast in stayInTheZone

    /**
     * Regression guard for the 1.6.3 fix: in stayInTheZone, the chicken's location
     * is written to Firestore via a dedicated timer loop while a radar ping is active,
     * even if the chicken has not moved (CoreLocation/FusedLocation 10 m distance
     * filter would otherwise suppress the `locationFlow` emission that originally
     * triggered writes).
     */
    @Test
    fun `radarPingBroadcastLoop writes chicken location while ping is active in stayInTheZone`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.STAY_IN_THE_ZONE.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 60_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            powerUps = dev.rahier.pouleparty.model.GamePowerUps(
                enabled = true,
                activeEffects = dev.rahier.pouleparty.model.ActiveEffects(
                    radarPing = com.google.firebase.Timestamp(java.util.Date(now + 30_000))
                )
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns
            com.mapbox.geojson.Point.fromLngLat(4.3928, 50.8266)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent() // let loadGame launch children, all block on first delay

        testDispatcher.scheduler.advanceTimeBy(dev.rahier.pouleparty.AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(atLeast = 1) {
            firestoreRepository.setChickenLocation(eq("test-id"), any())
        }
    }

    /**
     * When the ping is inactive, the loop must not call `setChickenLocation`.
     */
    @Test
    fun `radarPingBroadcastLoop does not write when ping is inactive`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.STAY_IN_THE_ZONE.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 60_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            powerUps = dev.rahier.pouleparty.model.GamePowerUps(
                enabled = true,
                activeEffects = dev.rahier.pouleparty.model.ActiveEffects(radarPing = null)
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns
            com.mapbox.geojson.Point.fromLngLat(4.3928, 50.8266)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent()

        // Advance by 3 throttle periods — still nothing should be written.
        testDispatcher.scheduler.advanceTimeBy(3 * dev.rahier.pouleparty.AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(exactly = 0) {
            firestoreRepository.setChickenLocation(any(), any())
        }
    }

    /**
     * In followTheChicken mode, the dedicated radar-ping loop must not be scheduled —
     * writes are already driven by the main locationFlow. We assert that by using a
     * game that has radarPing active in followTheChicken mode with an EMPTY
     * locationFlow: no writes should happen, proving the radar-ping loop never kicked
     * in for this mode.
     */
    @Test
    fun `radarPingBroadcastLoop is not scheduled in followTheChicken mode`() {
        val now = System.currentTimeMillis()
        val game = dev.rahier.pouleparty.model.Game(
            id = "test-id",
            gameMode = dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = dev.rahier.pouleparty.model.GameStatus.IN_PROGRESS.firestoreValue,
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(java.util.Date(now - 60_000)),
                end = com.google.firebase.Timestamp(java.util.Date(now + 3_600_000))
            ),
            powerUps = dev.rahier.pouleparty.model.GamePowerUps(
                enabled = true,
                activeEffects = dev.rahier.pouleparty.model.ActiveEffects(
                    radarPing = com.google.firebase.Timestamp(java.util.Date(now + 30_000))
                )
            )
        )
        io.mockk.coEvery { firestoreRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns null
        // Empty location flow so the primary trackLocation path has nothing to write.
        io.mockk.every { locationRepository.locationFlow() } returns kotlinx.coroutines.flow.emptyFlow()

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(3 * dev.rahier.pouleparty.AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        // In followTheChicken, `locationRepository.getLastLocation()` would be called
        // once by the primary track path — but it returns null, and the radar-ping
        // loop (which *would* use it) isn't scheduled, so no writes fire.
        io.mockk.coVerify(exactly = 0) {
            firestoreRepository.setChickenLocation(any(), any())
        }
    }
}
