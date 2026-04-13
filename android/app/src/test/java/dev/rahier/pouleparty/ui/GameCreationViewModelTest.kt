package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.gamecreation.GameCreationStep
import dev.rahier.pouleparty.ui.gamecreation.GameCreationViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        pricingModel: String = "free",
        numberOfPlayers: Int = 5,
        pricePerPlayerCents: Int = 0,
        depositAmountCents: Int = 0
    ): GameCreationViewModel {
        return GameCreationViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = analyticsRepository,
            auth = auth,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "gameId" to gameId,
                    "pricingModel" to pricingModel,
                    "numberOfPlayers" to numberOfPlayers,
                    "pricePerPlayerCents" to pricePerPlayerCents,
                    "depositAmountCents" to depositAmountCents
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
        assertFalse(state.isExpertMode)
        assertFalse(state.showAlert)
        assertTrue(state.goingForward)
    }

    @Test
    fun `initial game has values from savedStateHandle`() {
        val vm = createViewModel(
            gameId = "custom-id",
            pricingModel = "flat",
            numberOfPlayers = 20,
            pricePerPlayerCents = 500
        )
        val game = vm.uiState.value.game
        assertEquals("custom-id", game.id)
        assertEquals(20, game.maxPlayers)
        assertEquals("flat", game.pricing.model)
        assertEquals(500, game.pricing.pricePerPlayer)
    }

    @Test
    fun `deposit pricing requires registration by default`() {
        val vm = createViewModel(pricingModel = "deposit")
        assertTrue(vm.uiState.value.game.registration.required)
    }

    @Test
    fun `free pricing does not require registration by default`() {
        val vm = createViewModel(pricingModel = "free")
        assertFalse(vm.uiState.value.game.registration.required)
    }

    // ── Steps flow ──

    @Test
    fun `steps are in correct order when participating`() {
        val vm = createViewModel()
        val steps = vm.uiState.value.steps
        assertEquals(GameCreationStep.PARTICIPATION, steps[0])
        assertEquals(GameCreationStep.GAME_MODE, steps[1])
        assertEquals(GameCreationStep.ZONE_SETUP, steps[2])
        assertEquals(GameCreationStep.REGISTRATION, steps[3])
        assertEquals(GameCreationStep.START_TIME, steps[4])
        assertEquals(GameCreationStep.DURATION, steps[5])
        assertEquals(GameCreationStep.HEAD_START, steps[6])
        assertEquals(GameCreationStep.POWER_UPS, steps[7])
        assertEquals(GameCreationStep.CHICKEN_SEES_HUNTERS, steps[8])
        assertEquals(GameCreationStep.RECAP, steps[9])
    }

    @Test
    fun `steps include chickenSelection when not participating`() {
        val vm = createViewModel()
        vm.setParticipating(false)
        val steps = vm.uiState.value.steps
        assertEquals(GameCreationStep.PARTICIPATION, steps[0])
        assertEquals(GameCreationStep.CHICKEN_SELECTION, steps[1])
        assertEquals(GameCreationStep.GAME_MODE, steps[2])
        assertEquals(11, steps.size) // 10 + chickenSelection
    }

    @Test
    fun `registration comes before start time in steps`() {
        val vm = createViewModel()
        val steps = vm.uiState.value.steps
        val regIndex = steps.indexOf(GameCreationStep.REGISTRATION)
        val startIndex = steps.indexOf(GameCreationStep.START_TIME)
        assertTrue("REGISTRATION must come before START_TIME", regIndex < startIndex)
    }

    // ── Navigation ──

    @Test
    fun `next increments step index`() {
        val vm = createViewModel()
        vm.next()
        assertEquals(1, vm.uiState.value.currentStepIndex)
        assertTrue(vm.uiState.value.goingForward)
    }

    @Test
    fun `next does not go past last step`() {
        val vm = createViewModel()
        val maxSteps = vm.uiState.value.steps.size
        repeat(maxSteps + 5) { vm.next() }
        assertEquals(maxSteps - 1, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `back decrements step index`() {
        val vm = createViewModel()
        vm.next()
        vm.next()
        vm.back()
        assertEquals(1, vm.uiState.value.currentStepIndex)
        assertFalse(vm.uiState.value.goingForward)
    }

    @Test
    fun `back does not go below zero`() {
        val vm = createViewModel()
        repeat(5) { vm.back() }
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
        vm.next()
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
        repeat(maxSteps) { vm.next() }
        assertEquals(1f, vm.uiState.value.progress, 0.001f)
    }

    // ── Game mode ──

    @Test
    fun `updateGameMod to followTheChicken clears finalCenter`() {
        val vm = createViewModel()
        vm.onFinalLocationSelected(Point.fromLngLat(5.0, 51.0))
        assertNotNull(vm.uiState.value.game.finalLocation)
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        assertNull(vm.uiState.value.game.finalLocation)
    }

    @Test
    fun `updateGameMod to stayInTheZone does not clear finalCenter`() {
        val vm = createViewModel()
        vm.onFinalLocationSelected(Point.fromLngLat(5.0, 51.0))
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
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
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        vm.onLocationSelected(Point.fromLngLat(4.4, 50.9))
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured true for stayInTheZone with both locations`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        vm.onLocationSelected(Point.fromLngLat(4.4, 50.9))
        vm.onFinalLocationSelected(Point.fromLngLat(4.5, 51.0))
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured true for followTheChicken with only initial location`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        vm.onLocationSelected(Point.fromLngLat(4.4, 50.9))
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    // ── Registration ──

    @Test
    fun `toggleRequiresRegistration enables and sets default deadline`() {
        val vm = createViewModel(pricingModel = "free")
        vm.toggleRequiresRegistration(true)
        assertTrue(vm.uiState.value.game.registration.required)
        assertEquals(15, vm.uiState.value.game.registration.closesMinutesBefore)
    }

    @Test
    fun `toggleRequiresRegistration disables and clears deadline`() {
        val vm = createViewModel(pricingModel = "free")
        vm.toggleRequiresRegistration(true)
        vm.toggleRequiresRegistration(false)
        assertFalse(vm.uiState.value.game.registration.required)
        assertNull(vm.uiState.value.game.registration.closesMinutesBefore)
    }

    @Test
    fun `toggleRequiresRegistration cannot disable for deposit pricing`() {
        val vm = createViewModel(pricingModel = "deposit")
        vm.toggleRequiresRegistration(false)
        assertTrue("Deposit games must require registration", vm.uiState.value.game.registration.required)
    }

    @Test
    fun `setRegistrationClosesBeforeStart updates deadline`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(60)
        assertEquals(60, vm.uiState.value.game.registration.closesMinutesBefore)
    }

    // ── Minimum start date ──

    @Test
    fun `minimumStartDate open join is 1 minute from now`() {
        val vm = createViewModel()
        val min = vm.uiState.value.minimumStartDate
        val expected = System.currentTimeMillis() + 60_000L
        assertTrue("Open join minimum should be ~1 min", Math.abs(min.time - expected) < 1000)
    }

    @Test
    fun `minimumStartDate with 15 min deadline is 20 minutes`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(15)
        val min = vm.uiState.value.minimumStartDate
        val expected = System.currentTimeMillis() + (15 + 5) * 60 * 1000L
        assertTrue("15 min deadline should produce ~20 min minimum", Math.abs(min.time - expected) < 1000)
    }

    @Test
    fun `minimumStartDate with 1 day deadline is 1445 minutes`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(1440)
        val min = vm.uiState.value.minimumStartDate
        val expected = System.currentTimeMillis() + (1440 + 5) * 60 * 1000L
        assertTrue("1 day deadline should produce ~1445 min minimum", Math.abs(min.time - expected) < 1000)
    }

    @Test
    fun `toggling registration clamps start date forward`() {
        val vm = createViewModel()
        // Start date initial → roughly in 90 min
        // Activate registration with 120 min deadline → minimum becomes now + 125 min
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(120)
        val startDate = vm.uiState.value.game.startDate
        val minExpected = System.currentTimeMillis() + 125 * 60 * 1000L
        assertTrue("Start date should be clamped to at least ~125 min", startDate.time >= minExpected - 2000)
    }

    @Test
    fun `disabling registration does not push start date backward`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(120)
        val beforeDisable = vm.uiState.value.game.startDate.time
        vm.toggleRequiresRegistration(false)
        val afterDisable = vm.uiState.value.game.startDate.time
        assertEquals("Start date should not move when registration is disabled", beforeDisable, afterDisable)
    }

    // ── Duration ──

    @Test
    fun `updateDuration updates state`() {
        val vm = createViewModel()
        vm.updateDuration(120.0)
        assertEquals(120.0, vm.uiState.value.gameDurationMinutes, 0.0)
    }

    @Test
    fun `updateDuration triggers normal mode recalc`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(60.0)
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
        vm.updateHeadStart(10.0)
        assertEquals(10.0, vm.uiState.value.game.timing.headStartMinutes, 0.0)
    }

    @Test
    fun `updateHeadStart recalculates normal mode with effective duration`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(90.0)
        vm.updateHeadStart(15.0)
        advanceUntilIdle()
        // Effective duration = 90 - 15 = 75 min → 15 shrinks → (1500-100)/15 ≈ 93.33
        val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
        assertEquals(93.33, decline, 0.1)
    }

    // ── Power-ups ──

    @Test
    fun `togglePowerUps enables power-ups`() {
        val vm = createViewModel()
        vm.togglePowerUps(true)
        assertTrue(vm.uiState.value.game.powerUps.enabled)
    }

    @Test
    fun `togglePowerUps disables power-ups`() {
        val vm = createViewModel()
        vm.togglePowerUps(true)
        vm.togglePowerUps(false)
        assertFalse(vm.uiState.value.game.powerUps.enabled)
    }

    @Test
    fun `togglePowerUpType removes existing type`() {
        val vm = createViewModel()
        val initialTypes = vm.uiState.value.game.powerUps.enabledTypes.toList()
        assertTrue("ZONE_PREVIEW should be enabled by default", initialTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue))
        vm.togglePowerUpType(PowerUpType.ZONE_PREVIEW)
        assertFalse(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue))
    }

    @Test
    fun `togglePowerUpType re-adds removed type`() {
        val vm = createViewModel()
        vm.togglePowerUpType(PowerUpType.ZONE_PREVIEW)
        vm.togglePowerUpType(PowerUpType.ZONE_PREVIEW)
        assertTrue(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue))
    }

    // ── Chicken can see hunters ──

    @Test
    fun `toggleChickenCanSeeHunters true`() {
        val vm = createViewModel()
        vm.toggleChickenCanSeeHunters(true)
        assertTrue(vm.uiState.value.game.chickenCanSeeHunters)
    }

    @Test
    fun `toggleChickenCanSeeHunters false`() {
        val vm = createViewModel()
        vm.toggleChickenCanSeeHunters(true)
        vm.toggleChickenCanSeeHunters(false)
        assertFalse(vm.uiState.value.game.chickenCanSeeHunters)
    }

    // ── Start game ──

    @Test
    fun `startGame calls setConfig and logs analytics on success`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        var successCalled = false
        var successId: String? = null
        vm.startGame { id ->
            successCalled = true
            successId = id
        }
        advanceUntilIdle()
        assertTrue("onSuccess should be called", successCalled)
        assertEquals("test-game-id", successId)
        coVerify { firestoreRepository.setConfig(any()) }
        coVerify { analyticsRepository.gameCreated(any(), any(), any(), any()) }
    }

    @Test
    fun `startGame shows alert on failure`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("Network error")
        var successCalled = false
        vm.startGame { successCalled = true }
        advanceUntilIdle()
        assertFalse("onSuccess should not be called on failure", successCalled)
        assertTrue("Alert should be shown", vm.uiState.value.showAlert)
    }

    @Test
    fun `startGame clamps start date to minimum before saving`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(60) // 65 min minimum
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.startGame { }
        advanceUntilIdle()
        val startDate = vm.uiState.value.game.startDate
        val minExpected = System.currentTimeMillis() + 65 * 60 * 1000L
        assertTrue("Start date should be clamped to at least ~65 min", startDate.time >= minExpected - 2000)
    }

    @Test
    fun `dismissAlert hides alert`() {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("err")
        vm.startGame { }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.showAlert)
        vm.dismissAlert()
        assertFalse(vm.uiState.value.showAlert)
    }

    // ── Code copy feedback ──

    @Test
    fun `onCodeCopied sets codeCopied true`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onCodeCopied()
        assertTrue(vm.uiState.value.codeCopied)
    }

    // ── Date picker flow ──

    @Test
    fun `onStartTimeTapped shows date picker`() {
        val vm = createViewModel()
        vm.onStartTimeTapped()
        assertTrue(vm.uiState.value.showDatePicker)
    }

    @Test
    fun `dismissDatePicker hides date picker`() {
        val vm = createViewModel()
        vm.onStartTimeTapped()
        vm.dismissDatePicker()
        assertFalse(vm.uiState.value.showDatePicker)
    }

    @Test
    fun `updateStartTime clamps to minimum when in the past`() {
        val vm = createViewModel()
        // Set hour to 00:00 (midnight) — certainly in the past for the current date
        vm.updateStartTime(0, 0)
        val startDate = vm.uiState.value.game.startDate
        val minExpected = System.currentTimeMillis() + 60_000L - 2000 // 1 min minimum - tolerance
        assertTrue("Start date should not be in the past", startDate.time >= minExpected)
    }

    // ── Power-up selection overlay ──

    @Test
    fun `onPowerUpSelectionTapped shows overlay`() {
        val vm = createViewModel()
        vm.onPowerUpSelectionTapped()
        assertTrue(vm.uiState.value.showPowerUpSelection)
    }

    @Test
    fun `dismissPowerUpSelection hides overlay`() {
        val vm = createViewModel()
        vm.onPowerUpSelectionTapped()
        vm.dismissPowerUpSelection()
        assertFalse(vm.uiState.value.showPowerUpSelection)
    }

    // ═══════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════

    // ── Participation toggle edge cases ──

    @Test
    fun `toggling participation multiple times keeps step list in sync`() {
        val vm = createViewModel()
        assertEquals(10, vm.uiState.value.steps.size)
        vm.setParticipating(false)
        assertEquals(11, vm.uiState.value.steps.size)
        vm.setParticipating(true)
        assertEquals(10, vm.uiState.value.steps.size)
        vm.setParticipating(false)
        vm.setParticipating(false) // no-op
        assertEquals(11, vm.uiState.value.steps.size)
    }

    @Test
    fun `setParticipating does not reset currentStepIndex`() {
        val vm = createViewModel()
        vm.next()
        vm.next()
        vm.next() // index 3
        vm.setParticipating(false)
        assertEquals(3, vm.uiState.value.currentStepIndex)
    }

    // ── Zone config edge cases (exact boundary) ──

    @Test
    fun `isZoneConfigured false when location exactly at default Brussels`() {
        val vm = createViewModel()
        vm.onLocationSelected(
            Point.fromLngLat(
                dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE,
                dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE
            )
        )
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured false when location within default tolerance`() {
        val vm = createViewModel()
        vm.onLocationSelected(
            Point.fromLngLat(
                dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE + 0.0005,
                dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE + 0.0005
            )
        )
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        assertFalse("Location within 0.001 of default should count as default", vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured true just beyond default tolerance`() {
        val vm = createViewModel()
        vm.onLocationSelected(
            Point.fromLngLat(
                dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE + 0.002,
                dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE + 0.002
            )
        )
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        assertTrue(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `clearing final location sets back to not configured in stayInTheZone`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        vm.onLocationSelected(Point.fromLngLat(4.4, 50.9))
        vm.onFinalLocationSelected(Point.fromLngLat(4.5, 51.0))
        assertTrue(vm.uiState.value.isZoneConfigured)
        vm.onFinalLocationSelected(null)
        assertFalse(vm.uiState.value.isZoneConfigured)
    }

    @Test
    fun `switching modes multiple times preserves initial location`() {
        val vm = createViewModel()
        vm.onLocationSelected(Point.fromLngLat(4.4, 50.9))
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        val loc = vm.uiState.value.game.initialLocation
        assertEquals(4.4, loc.longitude(), 0.0001)
        assertEquals(50.9, loc.latitude(), 0.0001)
    }

    // ── Power-up toggle constraints ──

    @Test
    fun `togglePowerUpType cannot remove last available type in followTheChicken`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        // Start with all types enabled, remove all but one
        val allTypes = PowerUpType.entries
        for (type in allTypes.dropLast(1)) {
            vm.togglePowerUpType(type)
        }
        val remaining = vm.uiState.value.game.powerUps.enabledTypes
        assertEquals("Should have exactly 1 type left", 1, remaining.size)
        // Now try to remove the last one
        val lastType = allTypes.last()
        vm.togglePowerUpType(lastType)
        assertEquals("Should not be able to remove last available type", 1, vm.uiState.value.game.powerUps.enabledTypes.size)
    }

    @Test
    fun `togglePowerUpType can remove unavailable type in stayInTheZone`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        // INVISIBILITY is unavailable in stayInTheZone — should be removable even if "last"
        val before = vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.INVISIBILITY.firestoreValue)
        assertTrue("INVISIBILITY should be enabled by default", before)
        vm.togglePowerUpType(PowerUpType.INVISIBILITY)
        assertFalse(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.INVISIBILITY.firestoreValue))
    }

    @Test
    fun `togglePowerUpType re-adds unavailable type`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        vm.togglePowerUpType(PowerUpType.INVISIBILITY)
        vm.togglePowerUpType(PowerUpType.INVISIBILITY)
        assertTrue(vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.INVISIBILITY.firestoreValue))
    }

    @Test
    fun `togglePowerUpType last available type in stayInTheZone counts only available ones`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        // Available in stayInTheZone: ZONE_PREVIEW, RADAR_PING, ZONE_FREEZE (3 available, 3 unavailable)
        // Remove 2 available to leave only 1 available enabled
        vm.togglePowerUpType(PowerUpType.ZONE_PREVIEW)
        vm.togglePowerUpType(PowerUpType.RADAR_PING)
        // ZONE_FREEZE is the last available enabled → cannot be removed
        val before = vm.uiState.value.game.powerUps.enabledTypes.size
        vm.togglePowerUpType(PowerUpType.ZONE_FREEZE)
        val after = vm.uiState.value.game.powerUps.enabledTypes.size
        assertEquals("Cannot remove last available type", before, after)
    }

    // ── Registration deadline edge cases ──

    @Test
    fun `setRegistrationClosesBeforeStart while registration not required still updates field`() {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.game.registration.required)
        vm.setRegistrationClosesBeforeStart(30)
        // Field is updated even if not required
        assertEquals(30, vm.uiState.value.game.registration.closesMinutesBefore)
    }

    @Test
    fun `toggle registration off then on restores default 15 min`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(60)
        vm.toggleRequiresRegistration(false) // clears to null
        vm.toggleRequiresRegistration(true) // should restore default 15
        assertEquals(15, vm.uiState.value.game.registration.closesMinutesBefore)
    }

    @Test
    fun `setRegistrationClosesBeforeStart with null clears deadline`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(null)
        assertNull(vm.uiState.value.game.registration.closesMinutesBefore)
    }

    @Test
    fun `changing deadline multiple times clamps progressively`() {
        val vm = createViewModel()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(15) // min = now + 20 min
        val t1 = vm.uiState.value.game.startDate.time
        vm.setRegistrationClosesBeforeStart(60) // min = now + 65 min
        val t2 = vm.uiState.value.game.startDate.time
        vm.setRegistrationClosesBeforeStart(120) // min = now + 125 min
        val t3 = vm.uiState.value.game.startDate.time
        assertTrue("Start date should increase with deadline", t1 <= t2)
        assertTrue("Start date should increase with deadline", t2 <= t3)
    }

    // ── startDate clamping on navigation ──

    @Test
    fun `next clamps start date to minimum`() = runTest(testDispatcher) {
        val vm = createViewModel()
        // Manually set an outdated start date via updateStartTime bypass not directly possible;
        // instead, enable registration with aggressive deadline which pushes minimum
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(60) // pushes min to now + 65 min
        val dateBeforeNav = vm.uiState.value.game.startDate.time
        vm.next() // clamp runs again
        val dateAfterNav = vm.uiState.value.game.startDate.time
        assertTrue("Start date should not regress after next", dateAfterNav >= dateBeforeNav - 2000)
    }

    @Test
    fun `back clamps start date to minimum`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.next()
        vm.next()
        vm.toggleRequiresRegistration(true)
        vm.setRegistrationClosesBeforeStart(30) // min = now + 35 min
        vm.back()
        val startDate = vm.uiState.value.game.startDate
        val minExpected = System.currentTimeMillis() + 35 * 60 * 1000L
        assertTrue("Start date should be >= minimum after back", startDate.time >= minExpected - 2000)
    }

    // ── Duration edge cases ──

    @Test
    fun `duration zero with head start 0 produces large decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(0.0)
        vm.updateHeadStart(0.0)
        advanceUntilIdle()
        // Effective = max(0 - 0, 1) = 1 → numberOfShrinks = 0.2 → decline = (1500-100)/0.2 = 7000
        assertEquals(7000.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `duration equal to head start produces effective duration of 1`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(15.0)
        vm.updateHeadStart(15.0) // effective = 15 - 15 = 0 → clamped to 1
        advanceUntilIdle()
        // Effective = 1 min → numberOfShrinks = 0.2 → decline = (1500-100)/0.2 = 7000
        val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
        assertTrue("Decline should be very large for 1 min effective duration", decline > 1000)
    }

    @Test
    fun `head start greater than duration clamps effective to 1`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(10.0)
        vm.updateHeadStart(20.0) // effective = 10 - 20 = -10 → clamped to 1
        advanceUntilIdle()
        val decline = vm.uiState.value.game.zone.shrinkMetersPerUpdate
        // Effective = 1 min → numberOfShrinks = 0.2 → large decline
        assertTrue("Decline should be positive", decline > 0)
    }

    @Test
    fun `very long duration produces small decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(600.0) // 10 hours
        advanceUntilIdle()
        // 600/5 = 120 shrinks → (1500-100)/120 ≈ 11.67
        assertEquals(11.67, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.1)
    }

    // ── Initial radius edge cases ──

    @Test
    fun `updateInitialRadius updates game and triggers recalc`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateInitialRadius(500.0)
        advanceUntilIdle()
        assertEquals(500.0, vm.uiState.value.game.zone.radius, 0.0)
        // 90 min / 5 = 18 shrinks → (500-100)/18 ≈ 22.22
        assertEquals(22.22, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.1)
    }

    @Test
    fun `updateInitialRadius with radius at minimum produces zero decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateInitialRadius(100.0) // exactly NORMAL_MODE_MINIMUM_RADIUS
        advanceUntilIdle()
        assertEquals(0.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `updateInitialRadius with radius below minimum produces zero decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateInitialRadius(50.0)
        advanceUntilIdle()
        // Decline = (50-100)/18 = negative → clamped to 0
        assertEquals(0.0, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `updateInitialRadius with very large value produces proportionally large decline`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateInitialRadius(50000.0)
        advanceUntilIdle()
        // 90/5 = 18 shrinks → (50000-100)/18 ≈ 2772.22
        assertEquals(2772.22, vm.uiState.value.game.zone.shrinkMetersPerUpdate, 0.1)
    }

    // ── Start game edge cases ──

    @Test
    fun `startGame sets endDate based on duration and passes to firestore`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateDuration(60.0)
        var capturedGame: dev.rahier.pouleparty.model.Game? = null
        coEvery { firestoreRepository.setConfig(any()) } answers {
            capturedGame = firstArg()
            Unit
        }
        vm.startGame { }
        advanceUntilIdle()
        assertNotNull("setConfig should be called", capturedGame)
        val expectedDuration = 60 * 60 * 1000L
        val actualDuration = capturedGame!!.endDate.time - capturedGame!!.startDate.time
        assertEquals(expectedDuration, actualDuration)
    }

    @Test
    fun `startGame analytics event contains correct game mode`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.STAY_IN_THE_ZONE)
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.startGame { }
        advanceUntilIdle()
        coVerify {
            analyticsRepository.gameCreated(
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                maxPlayers = any(),
                pricingModel = any(),
                powerUpsEnabled = any()
            )
        }
    }

    @Test
    fun `startGame analytics event contains correct powerUps enabled state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.togglePowerUps(true)
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.startGame { }
        advanceUntilIdle()
        coVerify {
            analyticsRepository.gameCreated(
                gameMode = any(),
                maxPlayers = any(),
                pricingModel = any(),
                powerUpsEnabled = true
            )
        }
    }

    @Test
    fun `startGame analytics uses maxPlayers from pricing params`() = runTest(testDispatcher) {
        val vm = createViewModel(numberOfPlayers = 25)
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.startGame { }
        advanceUntilIdle()
        coVerify {
            analyticsRepository.gameCreated(
                gameMode = any(),
                maxPlayers = 25,
                pricingModel = any(),
                powerUpsEnabled = any()
            )
        }
    }

    // ── Alert and state management ──

    @Test
    fun `consecutive startGame failures show alert each time`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } throws RuntimeException("net err")
        vm.startGame { }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showAlert)
        vm.dismissAlert()
        assertFalse(vm.uiState.value.showAlert)
        vm.startGame { }
        advanceUntilIdle()
        assertTrue("Alert should be shown again on second failure", vm.uiState.value.showAlert)
    }

    // ── Date picker interaction ──

    @Test
    fun `updateStartDateOnly preserves existing hour and minute`() {
        val vm = createViewModel()
        // Initial date is now + 90 min, set hour/minute to something specific first
        vm.updateStartTime(15, 30)
        val cal = java.util.Calendar.getInstance().apply { time = vm.uiState.value.game.startDate }
        val origHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val origMinute = cal.get(java.util.Calendar.MINUTE)
        // updateStartDateOnly should keep the time
        val nextYear = cal.get(java.util.Calendar.YEAR) + 1
        vm.updateStartDateOnly(nextYear, 0, 1) // January 1 next year
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
        vm.updateStartDateOnly(futureCal.get(java.util.Calendar.YEAR), futureCal.get(java.util.Calendar.MONTH), futureCal.get(java.util.Calendar.DAY_OF_MONTH))
        vm.updateStartTime(15, 45)
        val cal = java.util.Calendar.getInstance().apply { time = vm.uiState.value.game.startDate }
        assertEquals(0, cal.get(java.util.Calendar.SECOND))
    }

    @Test
    fun `updateStartTime hides time picker`() {
        val vm = createViewModel()
        vm.updateStartTime(15, 30)
        assertFalse(vm.uiState.value.showTimePicker)
    }

    // ── Deposit game invariants ──

    @Test
    fun `deposit game registration stays required after start`() = runTest(testDispatcher) {
        val vm = createViewModel(pricingModel = "deposit")
        // Try all kinds of tricks to disable it
        vm.toggleRequiresRegistration(false)
        vm.toggleRequiresRegistration(false)
        vm.setRegistrationClosesBeforeStart(null)
        assertTrue("Deposit must keep registration required", vm.uiState.value.game.registration.required)
    }

    @Test
    fun `deposit game startGame preserves registration required`() = runTest(testDispatcher) {
        val vm = createViewModel(pricingModel = "deposit")
        vm.toggleRequiresRegistration(false) // ignored
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.startGame { }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.game.registration.required)
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
        repeat(maxSteps * 2) { vm.next() }
        assertEquals(maxSteps - 1, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `rapid back calls do not go below zero`() {
        val vm = createViewModel()
        vm.next()
        vm.next()
        repeat(100) { vm.back() }
        assertEquals(0, vm.uiState.value.currentStepIndex)
    }

    @Test
    fun `rapid togglePowerUpType alternates correctly`() {
        val vm = createViewModel()
        vm.updateGameMod(GameMod.FOLLOW_THE_CHICKEN)
        val initial = vm.uiState.value.game.powerUps.enabledTypes.contains(PowerUpType.ZONE_PREVIEW.firestoreValue)
        repeat(4) { vm.togglePowerUpType(PowerUpType.ZONE_PREVIEW) }
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
                    vm.updateInitialRadius(radius)
                    vm.updateDuration(duration)
                    vm.updateHeadStart(headStart)
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
        vm.startGame { }
        advanceUntilIdle()
        coVerify(exactly = 0) {
            analyticsRepository.gameCreated(any(), any(), any(), any())
        }
    }

    @Test
    fun `startGame success logs analytics exactly once`() = runTest(testDispatcher) {
        val vm = createViewModel()
        coEvery { firestoreRepository.setConfig(any()) } returns Unit
        vm.startGame { }
        advanceUntilIdle()
        coVerify(exactly = 1) {
            analyticsRepository.gameCreated(any(), any(), any(), any())
        }
    }
}
