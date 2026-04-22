package dev.rahier.pouleparty.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves that the `onCleared` override added to `BaseMapViewModel` actually
 * tears down the Firestore/location stream jobs when Android's ViewModelStore
 * releases the VM. Without this guard, screens survive background + GC holds
 * and keep burning Firestore quota for every hunter/chicken stream.
 *
 * We drive the teardown through a real `ViewModelStore.clear()` (same
 * mechanism Android uses) and watch the observable side-effects: subscriber
 * count on a hot flow the VM subscribes to, and the `isActive` state of the
 * stream jobs we reach via reflection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseMapViewModelTeardownTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var analyticsRepository: AnalyticsRepository
    private lateinit var auth: FirebaseAuth

    // Hot flows so we can measure `subscriptionCount` before/after clear().
    private val gameConfig = MutableSharedFlow<Game?>(replay = 1)
    private val powerUps = MutableSharedFlow<List<dev.rahier.pouleparty.powerups.model.PowerUp>>(replay = 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        analyticsRepository = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        val user: FirebaseUser = mockk()
        every { user.uid } returns "chicken-uid"
        every { auth.currentUser } returns user

        // A realistic game so loadGame() doesn't short-circuit.
        coEvery { firestoreRepository.getConfig(any()) } returns Game.mock
        every { firestoreRepository.gameConfigFlow(any()) } returns gameConfig
        every { firestoreRepository.powerUpsFlow(any()) } returns powerUps
        every { firestoreRepository.hunterLocationsFlow(any()) } returns emptyFlow()
        every { firestoreRepository.chickenLocationFlow(any()) } returns emptyFlow()
        every { firestoreRepository.challengesStream() } returns emptyFlow()
        every { locationRepository.locationFlow() } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createChickenVM(): ChickenMapViewModel = ChickenMapViewModel(
        firestoreRepository = firestoreRepository,
        locationRepository = locationRepository,
        analyticsRepository = analyticsRepository,
        auth = auth,
        savedStateHandle = SavedStateHandle(mapOf("gameId" to "test-game")),
    )

    @Suppress("UNCHECKED_CAST")
    private fun streamJobsOf(vm: Any): List<Job> {
        // Reflect into the protected `streamJobs` list on BaseMapViewModel.
        val field = Class.forName("dev.rahier.pouleparty.ui.map.BaseMapViewModel")
            .getDeclaredField("streamJobs")
        field.isAccessible = true
        return field.get(vm) as List<Job>
    }

    @Test
    fun `ViewModelStore clear cancels active stream jobs`() {
        val store = ViewModelStore()
        val vm = createChickenVM()
        // Register into a ViewModelStore so we can drive onCleared() the same
        // way Android does when the nav destination is popped.
        store.put("chicken", vm)
        // Run the initial sync work of every launched coroutine up to its
        // first real suspension (flow collection, delay). We can't use
        // advanceUntilIdle here because the VM spins up infinite `while` +
        // `delay(1_000)` timer/heartbeat loops that would never settle.
        testDispatcher.scheduler.runCurrent()

        val jobs = streamJobsOf(vm)
        assertTrue("expected at least one stream job after loadGame", jobs.isNotEmpty())
        // Not `all`, some of the stubbed flows are `emptyFlow()` and their
        // collectors complete immediately. What matters is that the long-
        // running ones (timer, gameConfig, powerUps) are alive before clear.
        val aliveBefore = jobs.filter { it.isActive }
        assertTrue("at least one long-running stream job should be active", aliveBefore.isNotEmpty())

        store.clear()
        testDispatcher.scheduler.runCurrent()

        // After clear(), `onCleared` should have been invoked, which calls
        // `cancelStreams()`, every previously-alive job is now cancelled.
        assertFalse("all stream jobs should be cancelled after clear", aliveBefore.any { it.isActive })
    }

    @Test
    fun `ViewModelStore clear drops subscribers on hot flows`() {
        val store = ViewModelStore()
        val vm = createChickenVM()
        store.put("chicken", vm)
        // Run the initial sync work of every launched coroutine up to its
        // first real suspension (flow collection, delay). We can't use
        // advanceUntilIdle here because the VM spins up infinite `while` +
        // `delay(1_000)` timer/heartbeat loops that would never settle.
        testDispatcher.scheduler.runCurrent()

        // The VM subscribes to both `gameConfigFlow` and `powerUpsFlow` from
        // loadGame, quota cost of leaving them around after the screen dies.
        assertEquals(1, gameConfig.subscriptionCount.value)
        assertEquals(1, powerUps.subscriptionCount.value)

        store.clear()
        // Run the initial sync work of every launched coroutine up to its
        // first real suspension (flow collection, delay). We can't use
        // advanceUntilIdle here because the VM spins up infinite `while` +
        // `delay(1_000)` timer/heartbeat loops that would never settle.
        testDispatcher.scheduler.runCurrent()

        assertEquals(0, gameConfig.subscriptionCount.value)
        assertEquals(0, powerUps.subscriptionCount.value)
    }

    @Test
    fun `clear called twice is idempotent`() {
        val store = ViewModelStore()
        val vm = createChickenVM()
        store.put("chicken", vm)
        // Run the initial sync work of every launched coroutine up to its
        // first real suspension (flow collection, delay). We can't use
        // advanceUntilIdle here because the VM spins up infinite `while` +
        // `delay(1_000)` timer/heartbeat loops that would never settle.
        testDispatcher.scheduler.runCurrent()

        store.clear()
        // Run the initial sync work of every launched coroutine up to its
        // first real suspension (flow collection, delay). We can't use
        // advanceUntilIdle here because the VM spins up infinite `while` +
        // `delay(1_000)` timer/heartbeat loops that would never settle.
        testDispatcher.scheduler.runCurrent()
        // No second `put`, but re-clearing should not blow up, exercises
        // the `streamJobs.clear()` + null-guard path in cancelStreams().
        store.clear()
        // Run the initial sync work of every launched coroutine up to its
        // first real suspension (flow collection, delay). We can't use
        // advanceUntilIdle here because the VM spins up infinite `while` +
        // `delay(1_000)` timer/heartbeat loops that would never settle.
        testDispatcher.scheduler.runCurrent()

        val jobs = streamJobsOf(vm)
        assertFalse("all cancelled", jobs.any { it.isActive })
    }
}
