package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.ui.gamecreation.GameCreationStep
import dev.rahier.pouleparty.ui.gamecreation.GameCreationViewModel
import dev.rahier.pouleparty.ui.gamecreation.GameCreationIntent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class GameCreationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        analyticsRepository = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        every { locationRepository.hasFineLocationPermission() } returns false
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "user-abc"
        every { auth.currentUser } returns mockUser
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        gameId: String = "test-game-id",
        isAdminCreation: Boolean = false
    ): GameCreationViewModel {
        return GameCreationViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = analyticsRepository,
            auth = auth,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "gameId" to gameId,
                    "isAdminCreation" to isAdminCreation
                )
            )
        )
    }

    // ── Initial state ──

    @Test
    fun `initial state has default values`() {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertEquals(0, state.currentStepIndex)
        assertTrue(state.isParticipating)
        assertEquals(90.0, state.gameDurationMinutes, 0.0)
        assertFalse(state.showAlert)
        assertTrue(state.goingForward)
    }

    @Test
    fun `initial game has gameId from savedStateHandle`() {
        val vm = createViewModel(gameId = "custom-id")
        val game = vm.uiState.value.game
        assertEquals("custom-id", game.id)
        assertEquals(5, game.maxPlayers)
    }

    // ── Steps flow ──

    @Test
    fun `steps are in correct order when participating in stayInTheZone`() {
        val vm = createViewModel()
        val steps = vm.uiState.value.steps
        assertEquals(GameCreationStep.PARTICIPATION, steps[0])
        assertEquals(GameCreationStep.MAX_PLAYERS, steps[1])
        // Wizard order: When → How long → Mode → Where → Rules.
        // GAME_MODE sits right before the zone block (it decides
        // whether FINAL_ZONE_SETUP exists), and the timing trio
        // comes earlier so PP-13's recap has a valid duration
        // window for the shrink schedule.
        assertEquals(GameCreationStep.START_TIME, steps[2])
        assertEquals(GameCreationStep.DURATION, steps[3])
        assertEquals(GameCreationStep.HEAD_START, steps[4])
        assertEquals(GameCreationStep.GAME_MODE, steps[5])
        // PP-11 / PP-12 / PP-13: zone setup as three consecutive
        // sub-steps in stayInTheZone (default mode).
        assertEquals(GameCreationStep.START_ZONE_SETUP, steps[6])
        assertEquals(GameCreationStep.FINAL_ZONE_SETUP, steps[7])
        assertEquals(GameCreationStep.ZONES_RECAP, steps[8])
        // PP-70 / PP-88: GameMaster password sits with the other
        // modifier toggles at the tail of the wizard.
        assertEquals(GameCreationStep.GAME_MASTER_PASSWORD, steps[9])
        assertEquals(GameCreationStep.POWER_UPS, steps[10])
        assertEquals(GameCreationStep.CHICKEN_SEES_HUNTERS, steps[11])
        assertEquals(GameCreationStep.RECAP, steps[12])
        assertEquals(13, steps.size)
    }

    @Test
    fun `steps skip FINAL_ZONE_SETUP in followTheChicken`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(dev.rahier.pouleparty.model.GameMod.FOLLOW_THE_CHICKEN))
        val steps = vm.uiState.value.steps
        // PP-12: no `finalCenter` in followTheChicken — the zone
        // tracks the chicken's live position, so FINAL_ZONE_SETUP is
        // dropped from the sequence (forward + back). The recap step
        // (PP-13) stays — it shows a single circle.
        assertTrue(GameCreationStep.START_ZONE_SETUP in steps)
        assertFalse(GameCreationStep.FINAL_ZONE_SETUP in steps)
        assertTrue(GameCreationStep.ZONES_RECAP in steps)
        assertEquals(12, steps.size)
    }

    @Test
    fun `steps include chickenSelection when not participating`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.ParticipatingChanged(false))
        val steps = vm.uiState.value.steps
        assertEquals(GameCreationStep.PARTICIPATION, steps[0])
        assertEquals(GameCreationStep.CHICKEN_SELECTION, steps[1])
        assertEquals(GameCreationStep.MAX_PLAYERS, steps[2])
        assertEquals(GameCreationStep.GAME_MODE, steps[3])
        // PP-11 / PP-12 / PP-13 split ZONE_SETUP into 3 sub-steps in
        // stayInTheZone (default), so total grows to 14 with
        // chickenSelection.
        assertEquals(14, steps.size)
    }

    @Test
    fun `max players step follows participation`() {
        val vm = createViewModel()
        val steps = vm.uiState.value.steps
        val participationIndex = steps.indexOf(GameCreationStep.PARTICIPATION)
        val maxPlayersIndex = steps.indexOf(GameCreationStep.MAX_PLAYERS)
        assertEquals(participationIndex + 1, maxPlayersIndex)
    }

    // ── Max players (PP-42) ──

    @Test
    fun `default max players range is 2 to 5`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.isAdminCreation)
        assertEquals(2..5, vm.uiState.value.maxPlayersRange)
    }

    @Test
    fun `admin creation max players range is 2 to 500`() {
        val vm = createViewModel(isAdminCreation = true)
        assertTrue(vm.uiState.value.isAdminCreation)
        assertEquals(2..500, vm.uiState.value.maxPlayersRange)
    }

    @Test
    fun `max players changed within range updates game`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(3))
        assertEquals(3, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players changed clamps to upper bound for standard creation`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(50))
        assertEquals(5, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players changed clamps to lower bound`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(0))
        assertEquals(2, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players changed accepts large value when admin creation`() {
        val vm = createViewModel(isAdminCreation = true)
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(250))
        assertEquals(250, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players boundary values pass through for standard creation`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(2))
        assertEquals(2, vm.uiState.value.game.maxPlayers)
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(5))
        assertEquals(5, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players boundary values pass through for admin creation`() {
        val vm = createViewModel(isAdminCreation = true)
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(2))
        assertEquals(2, vm.uiState.value.game.maxPlayers)
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(500))
        assertEquals(500, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players changed clamps admin upper overflow to 500`() {
        val vm = createViewModel(isAdminCreation = true)
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(9999))
        assertEquals(500, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players changed clamps negative to lower bound`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(-10))
        assertEquals(2, vm.uiState.value.game.maxPlayers)
    }

    @Test
    fun `max players changed just above standard cap clamps to five`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(6))
        assertEquals(5, vm.uiState.value.game.maxPlayers)
    }

    // ── Navigation ──

    @Test
    fun `next increments step index`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.Next)
        assertEquals(1, vm.uiState.value.currentStepIndex)
        assertTrue(vm.uiState.value.goingForward)
    }

    @Test
    fun `next does not go past last step`() {
        val vm = createViewModel()
        val maxSteps = vm.uiState.value.steps.size
        repeat(maxSteps + 5) { vm.onIntent(GameCreationIntent.Next) }
        assertEquals(maxSteps - 1, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `back decrements step index`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.Next)
        vm.onIntent(GameCreationIntent.Next)
        vm.onIntent(GameCreationIntent.Back)
        assertEquals(1, vm.uiState.value.currentStepIndex)
        assertFalse(vm.uiState.value.goingForward)
    }

    @Test
    fun `back does not go below zero`() {
        val vm = createViewModel()
        repeat(5) { vm.onIntent(GameCreationIntent.Back) }
        assertEquals(0, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `canGoBack false on first step`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.canGoBack)
    }

    @Test
    fun `canGoBack true on second step`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.Next)
        assertTrue(vm.uiState.value.canGoBack)
    }

    // ── Progress ──

    @Test
    fun `progress is 1 over total on first step`() {
        val vm = createViewModel()
        val expected = 1f / vm.uiState.value.steps.size
        assertEquals(expected, vm.uiState.value.progress, 0.001f)
    }

    @Test
    fun `progress is 1 on last step`() {
        val vm = createViewModel()
        val maxSteps = vm.uiState.value.steps.size
        repeat(maxSteps) { vm.onIntent(GameCreationIntent.Next) }
        assertEquals(1f, vm.uiState.value.progress, 0.001f)
    }

    // ── Game mode ──

    @Test
    fun `updateGameMod to followTheChicken clears finalCenter`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.FinalLocationSelected(Point.fromLngLat(5.0, 51.0)))
        assertNotNull(vm.uiState.value.game.finalLocation)
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        assertNull(vm.uiState.value.game.finalLocation)
    }

    @Test
    fun `updateGameMod to stayInTheZone does not clear finalCenter`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.FinalLocationSelected(Point.fromLngLat(5.0, 51.0)))
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        assertNotNull(vm.uiState.value.game.finalLocation)
    }

    // ── Zone configuration ──

    @Test
    fun `isZoneConfigured false with default location`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured false for stayInTheZone without finalCenter`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured true for stayInTheZone with both locations`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        vm.onIntent(GameCreationIntent.FinalLocationSelected(Point.fromLngLat(4.5, 51.0)))
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured true for followTheChicken with only initial location`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isFinalZoneConfigured false when final is closer than 100 m to start`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        // ~10 m offset: roughly 0.00009° in latitude near 51°N.
        vm.onIntent(GameCreationIntent.FinalLocationSelected(Point.fromLngLat(4.4, 50.90009)))
        assertFalse("Final < 100 m from start should not satisfy PP-12", vm.uiState.value.isFinalZoneConfigured)
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isFinalZoneConfigured true when final is far enough`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        // ~150 m offset: a hair over PP-12's threshold.
        vm.onIntent(GameCreationIntent.FinalLocationSelected(Point.fromLngLat(4.4, 50.9015)))
        assertTrue(vm.uiState.value.isFinalZoneConfigured)
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isStartZoneConfigured tracks start pin only`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.isStartZoneConfigured)
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        assertTrue(vm.uiState.value.isStartZoneConfigured)
    }

    // ── Minimum start date ──

    @Test
    fun `minimumStartDate is 1 minute from now`() {
        val vm = createViewModel()
        val min = vm.uiState.value.minimumStartDate
        val expected = System.currentTimeMillis() + 60_000L
        assertTrue("Minimum should be ~1 min", Math.abs(min.time - expected) < 1000)
    }

    // ── Duration ──

    @Test
    fun `updateDuration updates state`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(120.0))
        assertEquals(120.0, vm.uiState.value.gameDurationMinutes, 0.0)
    }

    @Test
    fun `updateDuration triggers normal mode recalc`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(60.0))
        advanceUntilIdle()
        val zone = vm.uiState.value.game.zone
        assertEquals(5.0, zone.shrinkIntervalMinutes, 0.0)
        // 60 min / 5 = 12 shrinks, (1500 - 100) / 12 = 116.67
        assertEquals(116.67, zone.shrinkMetersPerUpdate, 0.1)
    }

    // ── Head start ──

    @Test
    fun `updateHeadStart updates state`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.HeadStartChanged(10.0))
        assertEquals(10.0, vm.uiState.value.game.timing.headStartMinutes, 0.0)
    }

    @Test
    fun `updateHeadStart recalculates normal mode with effective duration`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(90.0))
        vm.onIntent(GameCreationIntent.HeadStartChanged(15.0))
        advanceUntilIdle()
        // Effective duration = 90 - 15 = 75 min → 15 shrinks → (1500-100)/15 ≈ 93.33
        val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
        assertEquals(93.33, decline, 0.1)
    }

    // ── Power-ups ──

    @Test
    fun `togglePowerUps enables power-ups`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.PowerUpsToggled(true))
        assertTrue(vm.uiState.value.game.powerUps.enabled)
    }

    @Test
    fun `togglePowerUps disables power-ups`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.PowerUpsToggled(true))
        vm.onIntent(GameCreationIntent.PowerUpsToggled(false))
        assertFalse(vm.uiState.value.game.powerUps.enabled)
    }

    @Test
    fun `togglePowerUpType removes existing type`() {
        val vm = createViewModel()
        val initialTypes = vm.uiState.value.game.powerUps.enabledTypes.toList()
        assertTrue("ZONE_PREVIEW should be enabled by default", initialTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue))
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.ZONE_PREVIEW))
        assertFalse(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue))
    }

    @Test
    fun `togglePowerUpType re-adds removed type`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.ZONE_PREVIEW))
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.ZONE_PREVIEW))
        assertTrue(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue))
    }

    // ── Chicken can see hunters ──

    @Test
    fun `toggleChickenCanSeeHunters true`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.ChickenCanSeeHuntersToggled(true))
        assertTrue(vm.uiState.value.game.chickenCanSeeHunters)
    }

    @Test
    fun `toggleChickenCanSeeHunters false`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.ChickenCanSeeHuntersToggled(true))
        vm.onIntent(GameCreationIntent.ChickenCanSeeHuntersToggled(false))
        assertFalse(vm.uiState.value.game.chickenCanSeeHunters)
    }

    // ── Start game ──

    @Test
    fun `startGame calls setConfig and logs analytics on success`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        val effect = vm.effects.first() as dev.rahier.pouleparty.ui.gamecreation.GameCreationEffect.GameStarted
        assertEquals("test-game-id", effect.gameId)
        coVerify { firestoreRepository.setConfig(any()) }
        coVerify { analyticsRepository.gameCreated(any(), any(), any()) }
    }

    @Test
    fun `startGame shows alert on failure`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("Network error")
        var successCalled = false
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        assertFalse("onSuccess should not be called on failure", successCalled)
        assertTrue("Alert should be shown", vm.uiState.value.showAlert)
    }

    @Test
    fun `dismissAlert hides alert`() {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("err")
        vm.onIntent(GameCreationIntent.StartGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.showAlert)
        vm.onIntent(GameCreationIntent.DismissAlert)
        assertFalse(vm.uiState.value.showAlert)
    }

    // ── Code copy feedback ──

    @Test
    fun `onCodeCopied sets codeCopied true`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.CodeCopied)
        assertTrue(vm.uiState.value.codeCopied)
    }

    // ── Date picker flow ──

    @Test
    fun `onStartTimeTapped shows date picker`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.StartTimeTapped)
        assertTrue(vm.uiState.value.showDatePicker)
    }

    @Test
    fun `dismissDatePicker hides date picker`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.StartTimeTapped)
        vm.onIntent(GameCreationIntent.DismissDatePicker)
        assertFalse(vm.uiState.value.showDatePicker)
    }

    @Test
    fun `updateStartTime clamps to minimum when in the past`() {
        val vm = createViewModel()
        // Set hour to 00:00 (midnight) — certainly in the past for the current date
        vm.onIntent(GameCreationIntent.StartTimeChanged(0, 0))
        val startDate = vm.uiState.value.game.startDate
        val minExpected = System.currentTimeMillis() + 60_000L - 2000 // 1 min minimum - tolerance
        assertTrue("Start date should not be in the past", startDate.time >= minExpected)
    }

    // ── Power-up selection overlay ──

    @Test
    fun `onPowerUpSelectionTapped shows overlay`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.PowerUpSelectionTapped)
        assertTrue(vm.uiState.value.showPowerUpSelection)
    }

    @Test
    fun `dismissPowerUpSelection hides overlay`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.PowerUpSelectionTapped)
        vm.onIntent(GameCreationIntent.DismissPowerUpSelection)
        assertFalse(vm.uiState.value.showPowerUpSelection)
    }

    // ═══════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════

    // ── Participation toggle edge cases ──

    @Test
    fun `toggling participation multiple times keeps step list in sync`() {
        // PP-88 added GAME_MASTER_PASSWORD → base 12, +1 for chickenSelection.
        val vm = createViewModel()
        assertEquals(12, vm.uiState.value.steps.size)
        vm.onIntent(GameCreationIntent.ParticipatingChanged(false))
        assertEquals(13, vm.uiState.value.steps.size)
        vm.onIntent(GameCreationIntent.ParticipatingChanged(true))
        assertEquals(12, vm.uiState.value.steps.size)
        vm.onIntent(GameCreationIntent.ParticipatingChanged(false))
        vm.onIntent(GameCreationIntent.ParticipatingChanged(false)) // no-op
        assertEquals(13, vm.uiState.value.steps.size)
    }

    @Test
    fun `setParticipating does not reset currentStepIndex`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.Next)
        vm.onIntent(GameCreationIntent.Next)
        vm.onIntent(GameCreationIntent.Next) // index 3
        vm.onIntent(GameCreationIntent.ParticipatingChanged(false))
        assertEquals(3, vm.uiState.value.currentStepIndex)
    }

    // ── Zone config edge cases (exact boundary) ──

    @Test
    fun `isZoneConfigured false when location exactly at default Brussels`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.LocationSelected(
            Point.fromLngLat(
                dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE,
                dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE
            ))
        )
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured false when location within default tolerance`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.LocationSelected(
            Point.fromLngLat(
                dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE + 0.0005,
                dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE + 0.0005
            ))
        )
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        assertFalse("Location within 0.001 of default should count as default", vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured true just beyond default tolerance`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.LocationSelected(
            Point.fromLngLat(
                dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE + 0.002,
                dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE + 0.002
            ))
        )
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `clearing final location sets back to not configured in stayInTheZone`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        vm.onIntent(GameCreationIntent.FinalLocationSelected(Point.fromLngLat(4.5, 51.0)))
        assertTrue(vm.uiState.value.isZoneConfigured)
        vm.onIntent(GameCreationIntent.FinalLocationSelected(null))
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `switching modes multiple times preserves initial location`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.LocationSelected(Point.fromLngLat(4.4, 50.9)))
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        val loc = vm.uiState.value.game.initialLocation
        assertEquals(4.4, loc.longitude(), 0.0001)
        assertEquals(50.9, loc.latitude(), 0.0001)
    }

    // ── Power-up toggle constraints ──

    @Test
    fun `togglePowerUpType cannot remove last available type in followTheChicken`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        // Start with all types enabled, remove all but one
        val allTypes = PowerUpType.entries
        for (type in allTypes.dropLast(1)) {
            vm.onIntent(GameCreationIntent.PowerUpTypeToggled(type))
        }
        val remaining = vm.uiState.value.game.powerUps.enabledTypes
        assertEquals("Should have exactly 1 type left", 1, remaining.size)
        // Now try to remove the last one
        val lastType = allTypes.last()
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(lastType))
        assertEquals("Should not be able to remove last available type", 1, vm.uiState.value.game.powerUps.enabledTypes.size)
    }

    @Test
    fun `togglePowerUpType can remove unavailable type in stayInTheZone`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        // INVISIBILITY is unavailable in stayInTheZone — should be removable even if "last"
        val before = vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.INVISIBILITY.firestoreValue)
        assertTrue("INVISIBILITY should be enabled by default", before)
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.INVISIBILITY))
        assertFalse(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.INVISIBILITY.firestoreValue))
    }

    @Test
    fun `togglePowerUpType re-adds unavailable type`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.INVISIBILITY))
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.INVISIBILITY))
        assertTrue(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.INVISIBILITY.firestoreValue))
    }

    @Test
    fun `togglePowerUpType last available type in stayInTheZone counts only available ones`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        // Available in stayInTheZone: ZONE_PREVIEW, RADAR_PING, ZONE_FREEZE (3 available, 3 unavailable)
        // Remove 2 available to leave only 1 available enabled
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.ZONE_PREVIEW))
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.RADAR_PING))
        // ZONE_FREEZE is the last available enabled → cannot be removed
        val before = vm.uiState.value.game.powerUps.enabledTypes.size
        vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.ZONE_FREEZE))
        val after = vm.uiState.value.game.powerUps.enabledTypes.size
        assertEquals("Cannot remove last available type", before, after)
    }

    // ── Duration edge cases ──

    @Test
    fun `duration zero with head start 0 produces large decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(0.0))
        vm.onIntent(GameCreationIntent.HeadStartChanged(0.0))
        advanceUntilIdle()
        // Effective = max(0 - 0, 1) = 1 → numberOfShrinks = 0.2 → decline = (1500-100)/0.2 = 7000
        assertEquals(7000.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `duration equal to head start produces effective duration of 1`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(15.0))
        vm.onIntent(GameCreationIntent.HeadStartChanged(15.0)) // effective = 15 - 15 = 0 → clamped to 1
        advanceUntilIdle()
        // Effective = 1 min → numberOfShrinks = 0.2 → decline = (1500-100)/0.2 = 7000
        val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
        assertTrue("Decline should be very large for 1 min effective duration", decline > 1000)
    }

    @Test
    fun `head start greater than duration clamps effective to 1`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(10.0))
        vm.onIntent(GameCreationIntent.HeadStartChanged(20.0)) // effective = 10 - 20 = -10 → clamped to 1
        advanceUntilIdle()
        val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
        // Effective = 1 min → numberOfShrinks = 0.2 → large decline
        assertTrue("Decline should be positive", decline > 0)
    }

    @Test
    fun `very long duration produces small decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(600.0)) // 10 hours
        advanceUntilIdle()
        // 600/5 = 120 shrinks → (1500-100)/120 ≈ 11.67
        assertEquals(11.67, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.1)
    }

    // ── Initial radius edge cases ──

    @Test
    fun `updateInitialRadius updates game and triggers recalc`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.InitialRadiusChanged(500.0))
        advanceUntilIdle()
        assertEquals(500.0, vm.uiState.value.game.zone.radius, 0.0)
        // 90 min / 5 = 18 shrinks → (500-100)/18 ≈ 22.22
        assertEquals(22.22, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.1)
    }

    @Test
    fun `updateInitialRadius with radius at minimum produces zero decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.InitialRadiusChanged(100.0)) // exactly NORMAL_MODE_MINIMUM_RADIUS
        advanceUntilIdle()
        assertEquals(0.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `updateInitialRadius with radius below minimum produces zero decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.InitialRadiusChanged(50.0))
        advanceUntilIdle()
        // Decline = (50-100)/18 = negative → clamped to 0
        assertEquals(0.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `updateInitialRadius with very large value produces proportionally large decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.InitialRadiusChanged(50000.0))
        advanceUntilIdle()
        // 90/5 = 18 shrinks → (50000-100)/18 ≈ 2772.22
        assertEquals(2772.22, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.1)
    }

    // ── Start game edge cases ──

    @Test
    fun `startGame sets endDate based on duration and passes to firestore`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.DurationChanged(60.0))
        var capturedGame: dev.rahier.pouleparty.model.Game? = null
        coEvery { firestoreRepository.setConfig(any()) } answers {
            capturedGame = firstArg()
            Unit
        }
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        assertNotNull("setConfig should be called", capturedGame)
        val expectedDuration = 60 * 60 * 1000L
        val actualDuration = capturedGame!!.endDate.time - capturedGame!!.startDate.time
        assertEquals(expectedDuration, actualDuration)
    }

    @Test
    fun `startGame analytics event contains correct game mode`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.STAY_IN_THE_ZONE))
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        coVerify {
            analyticsRepository.gameCreated(
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                maxPlayers = any(),
                powerUpsEnabled = any()
            )
        }
    }

    @Test
    fun `startGame analytics event contains correct powerUps enabled state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.PowerUpsToggled(true))
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        coVerify {
            analyticsRepository.gameCreated(
                gameMode = any(),
                maxPlayers = any(),
                powerUpsEnabled = true
            )
        }
    }

    @Test
    fun `startGame analytics carries maxPlayers from current state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.MaxPlayersChanged(4))
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        coVerify {
            analyticsRepository.gameCreated(
                gameMode = any(),
                maxPlayers = 4,
                powerUpsEnabled = any()
            )
        }
    }

    // ── Alert and state management ──

    @Test
    fun `consecutive startGame failures show alert each time`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("net err")
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showAlert)
        vm.onIntent(GameCreationIntent.DismissAlert)
        assertFalse(vm.uiState.value.showAlert)
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        assertTrue("Alert should be shown again on second failure", vm.uiState.value.showAlert)
    }

    // ── Date picker interaction ──

    @Test
    fun `updateStartDateOnly preserves existing hour and minute`() {
        val vm = createViewModel()
        // Initial date is now + 90 min, set hour/minute to something specific first
        vm.onIntent(GameCreationIntent.StartTimeChanged(15, 30))
        val cal = java.util.Calendar.getInstance().apply { time = vm.uiState.value.game.startDate }
        val origHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val origMinute = cal.get(java.util.Calendar.MINUTE)
        // updateStartDateOnly should keep the time
        val nextYear = cal.get(java.util.Calendar.YEAR) + 1
        vm.onIntent(GameCreationIntent.StartDateChanged(nextYear, 0, 1)) // January 1 next year
        val newCal = java.util.Calendar.getInstance().apply { time = vm.uiState.value.game.startDate }
        assertEquals(nextYear, newCal.get(java.util.Calendar.YEAR))
        assertEquals(0, newCal.get(java.util.Calendar.MONTH))
        assertEquals(1, newCal.get(java.util.Calendar.DAY_OF_MONTH))
        // Hour/minute should be preserved (not necessarily the clamped version)
        assertEquals(origHour, newCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(origMinute, newCal.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `updateStartTime strips seconds`() {
        val vm = createViewModel()
        // Use a date far in the future so minimumStartDate clamp doesn't override seconds
        val futureCal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        vm.onIntent(GameCreationIntent.StartDateChanged(futureCal.get(java.util.Calendar.YEAR), futureCal.get(java.util.Calendar.MONTH), futureCal.get(java.util.Calendar.DAY_OF_MONTH)))
        vm.onIntent(GameCreationIntent.StartTimeChanged(15, 45))
        val cal = java.util.Calendar.getInstance().apply { time = vm.uiState.value.game.startDate }
        assertEquals(0, cal.get(java.util.Calendar.SECOND))
    }

    @Test
    fun `updateStartTime hides time picker`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.StartTimeChanged(15, 30))
        assertFalse(vm.uiState.value.showTimePicker)
    }

    // ── Game code and ID ──

    @Test
    fun `game code is derived from id prefix uppercased`() {
        val vm = createViewModel(gameId = "abcdef-1234-5678")
        assertEquals("ABCDEF", vm.uiState.value.game.gameCode)
    }

    @Test
    fun `foundCode is 4 digit string`() {
        val vm = createViewModel()
        val foundCode = vm.uiState.value.game.foundCode
        assertEquals(4, foundCode.length)
        assertNotNull(foundCode.toIntOrNull())
    }

    // ── Concurrent navigation ──

    @Test
    fun `rapid next calls do not skip steps or go past max`() {
        val vm = createViewModel()
        val maxSteps = vm.uiState.value.steps.size
        repeat(maxSteps * 2) { vm.onIntent(GameCreationIntent.Next) }
        assertEquals(maxSteps - 1, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `rapid back calls do not go below zero`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.Next)
        vm.onIntent(GameCreationIntent.Next)
        repeat(100) { vm.onIntent(GameCreationIntent.Back) }
        assertEquals(0, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `rapid togglePowerUpType alternates correctly`() {
        val vm = createViewModel()
        vm.onIntent(GameCreationIntent.GameModeChanged(GameMod.FOLLOW_THE_CHICKEN))
        val initial = vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue)
        repeat(4) { vm.onIntent(GameCreationIntent.PowerUpTypeToggled(PowerUpType.ZONE_PREVIEW)) }
        val after = vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue)
        assertEquals("Even number of toggles → same state", initial, after)
    }

    // ── Normal mode recalc ensures zone never goes negative ──

    @Test
    fun `normal mode recalc always produces non-negative decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        // Test various combinations
        for (radius in listOf(100.0, 500.0, 1500.0, 50000.0)) {
            for (duration in listOf(5.0, 60.0, 90.0, 180.0)) {
                for (headStart in listOf(0.0, 5.0, 15.0)) {
                    vm.onIntent(GameCreationIntent.InitialRadiusChanged(radius))
                    vm.onIntent(GameCreationIntent.DurationChanged(duration))
                    vm.onIntent(GameCreationIntent.HeadStartChanged(headStart))
                    advanceUntilIdle()
                    val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
                    assertTrue(
                        "Decline must be >= 0 (r=$radius d=$duration hs=$headStart): $decline",
                        decline >= 0
                    )
                }
            }
        }
    }

    // ── Initial location fetching ──

    @Test
    fun `no location permission does not crash initialization`() {
        every { locationRepository.hasFineLocationPermission() } returns false
        val vm = createViewModel()
        assertNotNull(vm.uiState.value)
    }

    // ── Analytics not called on setConfig failure ──

    @Test
    fun `startGame failure does not log analytics`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("net err")
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        coVerify(exactly = 0) {
            analyticsRepository.gameCreated(any(), any(), any())
        }
    }

    @Test
    fun `startGame success logs analytics exactly once`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.onIntent(GameCreationIntent.StartGameTapped)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            analyticsRepository.gameCreated(any(), any(), any())
        }
    }
}
