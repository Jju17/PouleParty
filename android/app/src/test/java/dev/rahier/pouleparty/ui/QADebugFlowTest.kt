package dev.rahier.pouleparty.ui

import androidx.lifecycle.SavedStateHandle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapIntent
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapViewModel
import dev.rahier.pouleparty.ui.gamecreation.GameCreationIntent
import dev.rahier.pouleparty.ui.gamemastermap.GameMasterMapIntent
import dev.rahier.pouleparty.ui.gamecreation.GameCreationViewModel
import dev.rahier.pouleparty.ui.gamemastermap.GameMasterMapViewModel
import dev.rahier.pouleparty.ui.home.AdminCodeResult
import dev.rahier.pouleparty.ui.home.HomeIntent
import dev.rahier.pouleparty.ui.home.HomeViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration coverage for the QA debug feature on Android: the debug-code
 * recognition edge cases, the on-map debug actions (chicken + GameMaster)
 * routing through `debugAdvanceGame`, and the compressed timing applied when
 * a debug game is created. Mirrors the iOS `QADebugFlowTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QADebugFlowTest {

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
        val mockUser = mockk<FirebaseUser>()
        every { mockUser.uid } returns "user-abc"
        every { auth.currentUser } returns mockUser
        coEvery { firestoreRepository.getConfig(any()) } returns null
        every { firestoreRepository.gameConfigFlow(any()) } returns emptyFlow()
        every { firestoreRepository.powerUpsFlow(any()) } returns emptyFlow()
        every { firestoreRepository.hunterLocationsFlow(any()) } returns emptyFlow()
        every { firestoreRepository.chickenLocationFlow(any()) } returns emptyFlow()
        every { firestoreRepository.registrationsFlow(any()) } returns emptyFlow()
        every { firestoreRepository.challengesStream(any()) } returns emptyFlow()
        every { locationRepository.locationFlow() } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ────────────────────────────────────────────

    private fun chickenVm(gameId: String = "test-id") = ChickenMapViewModel(
        firestoreRepository = firestoreRepository,
        locationRepository = locationRepository,
        analyticsRepository = analyticsRepository,
        auth = auth,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId)),
    )

    private fun gameMasterVm(gameId: String = "test-id") = GameMasterMapViewModel(
        firestoreRepository = firestoreRepository,
        auth = auth,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to gameId)),
    )

    private fun gameCreationVm(
        gameId: String = "test-id",
        isDebugGame: Boolean = false,
    ): GameCreationViewModel = GameCreationViewModel(
        firestoreRepository = firestoreRepository,
        locationRepository = locationRepository,
        analyticsRepository = analyticsRepository,
        auth = auth,
        remoteConfig = mockk<dev.rahier.pouleparty.config.RemoteConfigProvider>(relaxed = true) {
            every { defaultInitialRadius } returns 1500.0
        },
        savedStateHandle = SavedStateHandle(
            mapOf(
                "gameId" to gameId,
                "isAdminCreation" to true,
                "isDebugGame" to isDebugGame,
            )
        ),
    )

    private fun homeVm(): HomeViewModel = HomeViewModel(
        firestoreRepository = firestoreRepository,
        locationRepository = locationRepository,
        analyticsRepository = analyticsRepository,
        auth = auth,
        prefs = mockk(relaxed = true),
        appContext = mockk(relaxed = true),
        remoteConfig = mockk<dev.rahier.pouleparty.config.RemoteConfigProvider>(relaxed = true).also {
            every { it.adminCode } returns "jujurahier"
            every { it.qaDebugCode } returns "qadebug"
        },
    )

    // ── Debug panel actions (chicken) ──────────────────────

    @Test
    fun `chicken onDebugEndNow calls callable with endNow`() {
        val vm = chickenVm()
        vm.onIntent(ChickenMapIntent.DebugEndNowTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { firestoreRepository.debugAdvanceGame("test-id", "endNow") }
    }

    @Test
    fun `chicken onDebugSpawnPowerUps calls callable with spawnPowerUp`() {
        val vm = chickenVm()
        vm.onIntent(ChickenMapIntent.DebugSpawnPowerUpsTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { firestoreRepository.debugAdvanceGame("test-id", "spawnPowerUp") }
    }

    @Test
    fun `chicken debug action swallows callable error`() {
        coEvery { firestoreRepository.debugAdvanceGame(any(), any()) } throws RuntimeException("boom")
        val vm = chickenVm()
        // Must not crash: the VM wraps the call in try/catch.
        vm.onIntent(ChickenMapIntent.DebugEndNowTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isGameOver)
    }

    // ── Debug panel actions (GameMaster) ───────────────────

    @Test
    fun `gameMaster onDebugEndNow calls callable with endNow`() {
        val vm = gameMasterVm()
        vm.onIntent(GameMasterMapIntent.DebugEndNowTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { firestoreRepository.debugAdvanceGame("test-id", "endNow") }
    }

    @Test
    fun `gameMaster onDebugSpawnPowerUps calls callable with spawnPowerUp`() {
        val vm = gameMasterVm()
        vm.onIntent(GameMasterMapIntent.DebugSpawnPowerUpsTapped)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { firestoreRepository.debugAdvanceGame("test-id", "spawnPowerUp") }
    }

    // ── Debug game creation compresses the timing ──────────

    @Test
    fun `debug game creation compresses the timing`() {
        val captured = slot<Game>()
        coEvery { firestoreRepository.setConfig(capture(captured)) } returns Unit
        val vm = gameCreationVm(isDebugGame = true)
        vm.onIntent(GameCreationIntent.StartGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()

        val g = captured.captured
        assertTrue("isDebugGame", g.isDebugGame)
        assertTrue("manualStartEnabled", g.manualStartEnabled)
        assertEquals("headStart", 0.0, g.timing.headStartMinutes, 0.0001)
        assertEquals("shrink interval", 1.0, g.zone.shrinkIntervalMinutes, 0.0001)
        val durationMs = g.timing.end.toDate().time - g.timing.start.toDate().time
        assertEquals("5-minute duration", 300_000L, durationMs)
    }

    @Test
    fun `standard game creation keeps standard timing`() {
        val captured = slot<Game>()
        coEvery { firestoreRepository.setConfig(capture(captured)) } returns Unit
        val vm = gameCreationVm(isDebugGame = false)
        vm.onIntent(GameCreationIntent.StartGameTapped)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(captured.captured.isDebugGame)
    }

    // ── validateAdminCode edge cases ───────────────────────

    @Test
    fun `validateAdminCode returns ADMIN for admin code`() {
        val vm = homeVm()
        vm.onIntent(HomeIntent.AdminCodeChanged("jujurahier"))
        assertEquals(AdminCodeResult.ADMIN, vm.validateAdminCode())
    }

    @Test
    fun `validateAdminCode returns DEBUG for qa debug code`() {
        val vm = homeVm()
        vm.onIntent(HomeIntent.AdminCodeChanged("qadebug"))
        assertEquals(AdminCodeResult.DEBUG, vm.validateAdminCode())
    }

    @Test
    fun `validateAdminCode returns INVALID for wrong code`() {
        val vm = homeVm()
        vm.onIntent(HomeIntent.AdminCodeChanged("nope"))
        assertEquals(AdminCodeResult.INVALID, vm.validateAdminCode())
    }

    @Test
    fun `validateAdminCode does not match empty debug code`() {
        // Remote Config cleared the debug code → debug creation disabled.
        val vm = HomeViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = analyticsRepository,
            auth = auth,
            prefs = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
            remoteConfig = mockk<dev.rahier.pouleparty.config.RemoteConfigProvider>(relaxed = true).also {
                every { it.adminCode } returns "jujurahier"
                every { it.qaDebugCode } returns ""
            },
        )
        vm.onIntent(HomeIntent.AdminCodeChanged(""))
        assertEquals(AdminCodeResult.INVALID, vm.validateAdminCode())
    }

    @Test
    fun `validateAdminCode prefers ADMIN when both codes equal`() {
        val vm = HomeViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            analyticsRepository = analyticsRepository,
            auth = auth,
            prefs = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
            remoteConfig = mockk<dev.rahier.pouleparty.config.RemoteConfigProvider>(relaxed = true).also {
                every { it.adminCode } returns "samecode"
                every { it.qaDebugCode } returns "samecode"
            },
        )
        vm.onIntent(HomeIntent.AdminCodeChanged("samecode"))
        assertEquals(AdminCodeResult.ADMIN, vm.validateAdminCode())
    }
}
