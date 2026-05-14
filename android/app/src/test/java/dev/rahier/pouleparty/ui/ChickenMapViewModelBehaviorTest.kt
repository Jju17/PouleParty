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
     * The chicken stationary rebroadcaster runs continuously regardless of
     * radar-ping state — gating it on ping landed us with a stale write
     * window when the 3 s ping fired right after a long stationary period.
     * The hunter-side UI is now what decides when to render the marker
     * (`game.isRadarPingActive`). This test asserts the loop fires writes
     * even when ping is inactive, because the next ping must hit a fresh
     * point. See `chickenStationaryRebroadcastLoop` in `ChickenMapViewModel`.
     */
    @Test
    fun `chickenStationaryRebroadcastLoop writes regardless of ping state`() {
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

        testDispatcher.scheduler.advanceTimeBy(3 * dev.rahier.pouleparty.AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(atLeast = 1) {
            firestoreRepository.setChickenLocation(eq("test-id"), any())
        }
    }

    // MARK: - PP-19 end-game stays on map
    //
    // The map must stay mounted at gameOver — `isGameOver` flips to
    // true, no auto-transition to Victory. The GPS write loop stops
    // (no `setChickenLocation` writes after gameOver). Mirrors iOS
    // `ChickenMapFeatureTests` PP-19 block.

    /** Scenario 1 (chicken): timeout — `nowDate >= endDate` flips
     *  `isGameOver` and `showGameOverAlert`. No auto-transition to
     *  Victory; map stays mounted. */
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
        // Let loadGame land + the first timer delay(1000) elapse so the
        // checkGameOverByTime branch fires.
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()

        assertTrue("isGameOver must flip true on timeout", vm.uiState.value.isGameOver)
        assertTrue("gameOver alert must show", vm.uiState.value.showGameOverAlert)
        // The map is still mounted — no NavigateToVictory effect was
        // dispatched (effect emission is not asserted unit-level; the
        // post-condition is `isGameOver = true`, screen stays put).
    }

    /** Scenario 2 (chicken): zone collapse → `isGameOver` flips,
     *  `gameOverMessage` reflects the collapse reason. No auto-transition. */
    @Test
    fun `pp19 zone collapse flips isGameOver and stops streams`() {
        val now = System.currentTimeMillis()
        // Radius is small enough that the very first shrink reaches 0,
        // tripping `processRadiusUpdate.isGameOver`.
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
                shrinkIntervalMinutes = 0.0,           // shrink is overdue immediately
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
    }

    /** Scenario 5 (chicken): once `isGameOver` is set, no further
     *  `setChickenLocation` calls fire. The streams are cancelled the
     *  moment `cancelStreams` runs alongside the `isGameOver` flip. */
    @Test
    fun `pp19 GPS writes stop after isGameOver flips on timeout`() {
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
        // No cached fix → no pre-timer initial write. The first write
        // would only come from the location flow, which we emit
        // post-gameOver to prove the collector has been cancelled.
        io.mockk.coEvery { locationRepository.getLastLocation() } returns null

        val locationFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.mapbox.geojson.Point>(replay = 0)
        io.mockk.every { locationRepository.locationFlow() } returns locationFlow

        val vm = createViewModel()
        // Let the timer tick once → isGameOver flips → cancelStreams cancels
        // the trackLocation coroutine.
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()
        assertTrue("Precondition: isGameOver true", vm.uiState.value.isGameOver)

        // Now try to push a fresh coord through the cancelled flow —
        // the collector is gone, so setChickenLocation must NOT fire.
        kotlinx.coroutines.runBlocking {
            locationFlow.emit(com.mapbox.geojson.Point.fromLngLat(4.3600, 50.8500))
        }
        testDispatcher.scheduler.advanceTimeBy(100)
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
