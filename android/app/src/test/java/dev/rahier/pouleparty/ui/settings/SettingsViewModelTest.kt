package dev.rahier.pouleparty.ui.settings

import android.content.SharedPreferences
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.UserProfileRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.MyGame
import dev.rahier.pouleparty.model.MyGameRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true) {
        every { edit() } returns editor
        every { getString(any(), any()) } returns ""
    }
    private val user = mockk<FirebaseUser>(relaxed = true) { every { uid } returns "uid-1" }
    private val auth = mockk<FirebaseAuth>(relaxed = true) { every { currentUser } returns user }
    private val gameRepository = mockk<GameRepository>(relaxed = true)
    private val profiles = mockk<UserProfileRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { editor.putString(any(), any()) } returns editor
        every { editor.clear() } returns editor
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SettingsViewModel(auth, prefs, gameRepository, profiles)

    @Test
    fun `a failed games load is an error with retry, not an empty list`() {
        coEvery { gameRepository.fetchMyGames("uid-1") } throws IOException("offline")
        val vm = createViewModel()
        dispatcher.scheduler.runCurrent()
        assertEquals(R.string.api_error_network, vm.uiState.value.myGamesErrorRes)
        assertFalse(vm.uiState.value.isLoadingGames)

        coEvery { gameRepository.fetchMyGames("uid-1") } returns listOf(MyGame(Game(id = "g1"), MyGameRole.HUNTER))
        vm.onIntent(SettingsIntent.RetryMyGames)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.uiState.value.myGamesErrorRes)
        assertEquals(listOf("g1"), vm.uiState.value.myGames.map { it.game.id })
    }

    @Test
    fun `a clean nickname is stored locally and on the profile`() {
        val vm = createViewModel()
        vm.onIntent(SettingsIntent.NicknameChanged("  Poulet Rôti  "))
        vm.onIntent(SettingsIntent.SaveNickname)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isShowingNicknameSaved)
        verify { editor.putString(any(), "Poulet Rôti") }
        coVerify { profiles.saveNickname("uid-1", "Poulet Rôti") }
    }

    @Test
    fun `account deletion stops when the profile cannot be removed`() {
        coEvery { profiles.deleteProfile("uid-1") } throws IOException("offline")
        val vm = createViewModel()
        vm.onIntent(SettingsIntent.ConfirmDelete)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isShowingDeleteError)
        verify(exactly = 0) { user.delete() }
    }

    @Test
    fun `account deletion removes the profile, then the user, then signs in fresh`() {
        every { user.delete() } returns Tasks.forResult(null)
        every { auth.signInAnonymously() } returns Tasks.forResult(mockk(relaxed = true))
        val vm = createViewModel()
        vm.onIntent(SettingsIntent.ConfirmDelete)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isShowingDeleteSuccess)
        coVerify { profiles.deleteProfile("uid-1") }
        verify { editor.clear() }
    }
}
