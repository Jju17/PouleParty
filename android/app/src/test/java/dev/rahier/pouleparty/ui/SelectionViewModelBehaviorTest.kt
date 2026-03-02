package dev.rahier.pouleparty.ui

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.selection.SelectionViewModel
import io.mockk.coEvery
import io.mockk.every
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

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionViewModelBehaviorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firestoreRepository = mockk(relaxed = true)
        locationRepository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        auth = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockAuthUser(uid: String?) {
        if (uid != null) {
            val mockUser = mockk<FirebaseUser>()
            every { mockUser.uid } returns uid
            every { auth.currentUser } returns mockUser
        } else {
            every { auth.currentUser } returns null
        }
    }

    private fun createViewModel(): SelectionViewModel {
        return SelectionViewModel(
            firestoreRepository = firestoreRepository,
            locationRepository = locationRepository,
            prefs = prefs,
            auth = auth
        )
    }

    // ── checkForActiveGame (called in init) ──

    @Test
    fun `checkForActiveGame with no user does not update state`() {
        mockAuthUser(null)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.activeGame)
        assertNull(vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds hunter game`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(Game.mock, PlayerRole.HUNTER)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Game.mock, vm.uiState.value.activeGame)
        assertEquals(PlayerRole.HUNTER, vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds chicken game`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(Game.mock, PlayerRole.CHICKEN)
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Game.mock, vm.uiState.value.activeGame)
        assertEquals(PlayerRole.CHICKEN, vm.uiState.value.activeGameRole)
    }

    @Test
    fun `checkForActiveGame finds no game`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns null
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.activeGame)
        assertNull(vm.uiState.value.activeGameRole)
    }

    // ── rejoinGame ──

    @Test
    fun `rejoinGame as hunter calls onRejoinAsHunter with game id and nickname`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(Game.mock, PlayerRole.HUNTER)
        every { prefs.getString(AppConstants.PREF_USER_NICKNAME, "") } returns "Julien"

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var hunterGameId: String? = null
        var hunterName: String? = null
        vm.rejoinGame(
            onRejoinAsChicken = { fail("Should not call chicken callback") },
            onRejoinAsHunter = { id, name -> hunterGameId = id; hunterName = name }
        )

        assertEquals(Game.mock.id, hunterGameId)
        assertEquals("Julien", hunterName)
    }

    @Test
    fun `rejoinGame as hunter uses default name when nickname is empty`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(Game.mock, PlayerRole.HUNTER)
        every { prefs.getString(AppConstants.PREF_USER_NICKNAME, "") } returns ""

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var hunterName: String? = null
        vm.rejoinGame(
            onRejoinAsChicken = { fail("Should not call chicken callback") },
            onRejoinAsHunter = { _, name -> hunterName = name }
        )

        assertEquals("Hunter", hunterName)
    }

    @Test
    fun `rejoinGame as chicken calls onRejoinAsChicken with game id`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(Game.mock, PlayerRole.CHICKEN)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var chickenGameId: String? = null
        vm.rejoinGame(
            onRejoinAsChicken = { id -> chickenGameId = id },
            onRejoinAsHunter = { _, _ -> fail("Should not call hunter callback") }
        )

        assertEquals(Game.mock.id, chickenGameId)
    }

    @Test
    fun `rejoinGame clears active game from state`() {
        mockAuthUser("user-123")
        coEvery { firestoreRepository.findActiveGame("user-123") } returns Pair(Game.mock, PlayerRole.CHICKEN)

        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.activeGame)

        vm.rejoinGame(
            onRejoinAsChicken = { },
            onRejoinAsHunter = { _, _ -> }
        )

        assertNull(vm.uiState.value.activeGame)
        assertNull(vm.uiState.value.activeGameRole)
    }

    @Test
    fun `rejoinGame with no active game does nothing`() {
        mockAuthUser(null) // No user → no active game loaded
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var called = false
        vm.rejoinGame(
            onRejoinAsChicken = { called = true },
            onRejoinAsHunter = { _, _ -> called = true }
        )

        assertFalse(called)
    }
}
