package dev.rahier.pouleparty.ui

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.model.ActiveEffects
import dev.rahier.pouleparty.model.GamePowerUps
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.model.ZoneCircle
import java.util.Date
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.PresenceRepository
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.data.ChallengeSubmissionRepository
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
    private lateinit var gameRepository: GameRepository
    private lateinit var presenceRepository: PresenceRepository
    private lateinit var gameFunctions: GameFunctions
    private lateinit var challengeSubmissions: ChallengeSubmissionRepository
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gameRepository = mockk(relaxed = true)
        presenceRepository = mockk(relaxed = true)
        gameFunctions = mockk(relaxed = true)
        challengeSubmissions = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        // Make `loadGame()` exit early so init coroutines settle without
        // needing real game data from the relaxed mock.
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns null
        io.mockk.every { gameRepository.gameConfigFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { gameRepository.powerUpsFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { presenceRepository.hunterLocationsFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { presenceRepository.chickenLocationFlow(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        io.mockk.every { locationRepository.locationFlow() } returns kotlinx.coroutines.flow.emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(gameId: String = "test-id"): ChickenMapViewModel {
        return ChickenMapViewModel(
            gameRepository = gameRepository,
            presenceRepository = presenceRepository,
            gameFunctions = gameFunctions,
            locationRepository = locationRepository,
            analyticsRepository = mockk<AnalyticsRepository>(relaxed = true),
            auth = mockk<FirebaseAuth>(relaxed = true),
            prefs = mockk<android.content.SharedPreferences>(relaxed = true),
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
        // Default game mock is followTheChicken + chickenCanSeeHunters = true
        assertEquals(R.string.subtitle_chicken_sees, dev.rahier.pouleparty.ui.gamelogic.chickenSubtitleRes(vm.uiState.value.game))
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

    @Test
    fun `radarPingBroadcastLoop writes chicken location while ping is active in stayInTheZone`() {
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test-id",
            gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 60_000)),
                end = Timestamp(Date(now + 3_600_000))
            ),
            powerUps = GamePowerUps(
                enabled = true,
                activeEffects = ActiveEffects(
                    radarPing = Timestamp(Date(now + 30_000))
                )
            )
        )
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns
            Point.fromLngLat(4.3928, 50.8266)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent() // let loadGame launch children, all block on first delay

        testDispatcher.scheduler.advanceTimeBy(AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(atLeast = 1) {
            presenceRepository.setChickenLocation(eq("test-id"), any())
        }
    }

    private fun startedGame(): Game {
        val now = System.currentTimeMillis()
        return Game(
            id = "test-id",
            gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 60_000)),
                end = Timestamp(Date(now + 3_600_000)),
            ),
        )
    }

    @Test
    fun `a failing heartbeat is retried on the next tick instead of crashing`() {
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns startedGame()
        io.mockk.coEvery { presenceRepository.updateHeartbeat(any()) } throws IllegalStateException("rtdb down")

        createViewModel()
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceTimeBy(AppConstants.HEARTBEAT_INTERVAL_MS * 2 + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(exactly = 3) { presenceRepository.updateHeartbeat("test-id") }
    }

    @Test
    fun `a moving chicken still writes once per throttle window`() {
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns startedGame()
        io.mockk.every { locationRepository.locationFlow() } returns kotlinx.coroutines.flow.flow {
            repeat(20) { step ->
                emit(Point.fromLngLat(4.39 + step * 0.001, 50.82))
                kotlinx.coroutines.delay(1_000)
            }
        }

        createViewModel()
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceTimeBy(AppConstants.LOCATION_THROTTLE_MS * 3 + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.verify(exactly = 4) { presenceRepository.setChickenLocation(eq("test-id"), any(), any()) }
    }

    /**
     * The chicken stationary rebroadcaster runs continuously regardless of
     * radar-ping state, gating it on ping landed us with a stale write
     * window when the 3 s ping fired right after a long stationary period.
     * The hunter-side UI is now what decides when to render the marker
     * (`game.isRadarPingActive`). This test asserts the loop fires writes
     * even when ping is inactive, because the next ping must hit a fresh
     * point. See `broadcastChickenLocation` in `ChickenMapViewModel`.
     */
    @Test
    fun `the chicken position is rebroadcast regardless of ping state`() {
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test-id",
            gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 60_000)),
                end = Timestamp(Date(now + 3_600_000))
            ),
            powerUps = GamePowerUps(
                enabled = true,
                activeEffects = ActiveEffects(radarPing = null)
            )
        )
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns
            Point.fromLngLat(4.3928, 50.8266)

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(3 * AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(atLeast = 1) {
            presenceRepository.setChickenLocation(eq("test-id"), any())
        }
    }

    /** Scenario 1 (chicken): timeout, `nowDate >= endDate` flips
     *  `isGameOver`. No auto-transition to Victory; map stays mounted. */
    @Test
    fun `pp19 timeout flips isGameOver without transition`() {
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test-id",
            gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 3_600_000)),
                end = Timestamp(Date(now - 1_000))
            )
        )
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns game

        val vm = createViewModel()
        // Let loadGame land + the first timer delay(1000) elapse so the
        // checkGameOverByTime branch fires.
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()

        assertTrue("isGameOver must flip true on timeout", vm.uiState.value.isGameOver)
        // The map is still mounted, no NavigateToVictory effect was
        // dispatched (effect emission is not asserted unit-level; the
        // post-condition is `isGameOver = true`, screen stays put).
    }

    /** PP-zone-stored: the zone no longer "collapses" to 0 / ends the
     *  game. With stored circles the schedule stops at the 50m final
     *  circle and stays there; the game ends only by time / all-found /
     *  cancel. So even with every shrink elapsed, isGameOver stays false
     *  (until endDate) and the radius settles on the last stored circle. */
    @Test
    fun `pp-zone-stored zone shrinks to final circle without game over`() {
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test-id",
            gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 3_600_000)),
                end = Timestamp(Date(now + 3_600_000))
            ),
            zone = Zone(
                center = GeoPoint(50.8466, 4.3528),
                radius = 100.0,
                shrinkIntervalMinutes = 1.0,
                shrinkMetersPerUpdate = 100.0
            )
        )
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns game
        // Stored schedule: initial 100m circle, final 50m circle.
        io.mockk.coEvery { gameRepository.fetchZoneSchedule(any()) } returns listOf(
            ZoneCircle(order = 0, radiusMeters = 100.0, lat = 50.8466, lng = 4.3528),
            ZoneCircle(order = 1, radiusMeters = 50.0, lat = 50.8466, lng = 4.3528),
        )

        val vm = createViewModel()
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()

        assertFalse("game must NOT end on zone shrink (ends by time now)", vm.uiState.value.isGameOver)
        assertTrue("radius settles on the final stored circle (50m)", vm.uiState.value.radius == 50)
    }

    /** Scenario 5 (chicken): once `isGameOver` is set, no further
     *  `setChickenLocation` calls fire. The streams are cancelled the
     *  moment `cancelStreams` runs alongside the `isGameOver` flip. */
    @Test
    fun `pp19 GPS writes stop after isGameOver flips on timeout`() {
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test-id",
            gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 3_600_000)),
                end = Timestamp(Date(now - 1_000))
            )
        )
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns game
        // No cached fix → no pre-timer initial write. The first write
        // would only come from the location flow, which we emit
        // post-gameOver to prove the collector has been cancelled.
        io.mockk.coEvery { locationRepository.getLastLocation() } returns null

        val locationFlow = MutableSharedFlow<Point>(replay = 0)
        io.mockk.every { locationRepository.locationFlow() } returns locationFlow

        val vm = createViewModel()
        // Let the timer tick once → isGameOver flips → cancelStreams cancels
        // the trackLocation coroutine.
        testDispatcher.scheduler.advanceTimeBy(1_500)
        testDispatcher.scheduler.runCurrent()
        assertTrue("Precondition: isGameOver true", vm.uiState.value.isGameOver)

        // Now try to push a fresh coord through the cancelled flow,
        // the collector is gone, so setChickenLocation must NOT fire.
        kotlinx.coroutines.runBlocking {
            locationFlow.emit(Point.fromLngLat(4.3600, 50.8500))
        }
        testDispatcher.scheduler.advanceTimeBy(100)
        testDispatcher.scheduler.runCurrent()

        io.mockk.coVerify(exactly = 0) {
            presenceRepository.setChickenLocation(any(), any())
        }
    }

    /**
     * In followTheChicken mode, the dedicated radar-ping loop must not be scheduled,
     * writes are already driven by the main locationFlow. We assert that by using a
     * game that has radarPing active in followTheChicken mode with an EMPTY
     * locationFlow: no writes should happen, proving the radar-ping loop never kicked
     * in for this mode.
     */
    @Test
    fun `radarPingBroadcastLoop is not scheduled in followTheChicken mode`() {
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test-id",
            gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            status = GameStatus.IN_PROGRESS.firestoreValue,
            timing = Timing(
                start = Timestamp(Date(now - 60_000)),
                end = Timestamp(Date(now + 3_600_000))
            ),
            powerUps = GamePowerUps(
                enabled = true,
                activeEffects = ActiveEffects(
                    radarPing = Timestamp(Date(now + 30_000))
                )
            )
        )
        io.mockk.coEvery { gameRepository.getConfig(any()) } returns game
        io.mockk.coEvery { locationRepository.getLastLocation() } returns null
        // Empty location flow so the primary trackLocation path has nothing to write.
        io.mockk.every { locationRepository.locationFlow() } returns kotlinx.coroutines.flow.emptyFlow()

        val vm = createViewModel()
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(3 * AppConstants.LOCATION_THROTTLE_MS + 100)
        testDispatcher.scheduler.runCurrent()

        // In followTheChicken, `locationRepository.getLastLocation()` would be called
        // once by the primary track path, but it returns null, and the radar-ping
        // loop (which *would* use it) isn't scheduled, so no writes fire.
        io.mockk.coVerify(exactly = 0) {
            presenceRepository.setChickenLocation(any(), any())
        }
    }
}
