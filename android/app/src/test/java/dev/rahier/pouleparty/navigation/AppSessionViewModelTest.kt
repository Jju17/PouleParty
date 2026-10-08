package dev.rahier.pouleparty.navigation

import android.content.SharedPreferences
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.PushRegistrar
import dev.rahier.pouleparty.data.UserProfileRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true) { every { edit() } returns editor }
    private val profiles = mockk<UserProfileRepository>(relaxed = true)
    private val pushRegistrar = mockk<PushRegistrar>(relaxed = true)
    private val user = mockk<FirebaseUser> { every { uid } returns "uid-1" }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a new anonymous user is sent back to onboarding`() = runTest(dispatcher) {
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns null
        val result = mockk<AuthResult> { every { additionalUserInfo?.isNewUser } returns true }
        every { auth.signInAnonymously() } returns Tasks.forResult(result)

        val vm = AppSessionViewModel(auth, prefs, profiles, pushRegistrar)
        assertFalse(vm.state.value.isReady)
        advanceUntilIdle()

        assertTrue(vm.state.value.isReady)
        verify { editor.putBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, false) }
        coVerify { pushRegistrar.syncAfterSignIn(any()) }
    }

    @Test
    fun `a failed sign-in still lets the app start`() = runTest(dispatcher) {
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns null
        every { auth.signInAnonymously() } returns Tasks.forException(IllegalStateException("offline"))

        val vm = AppSessionViewModel(auth, prefs, profiles, pushRegistrar)
        advanceUntilIdle()
        assertTrue(vm.state.value.isReady)
    }

    @Test
    fun `finishing onboarding stores the nickname locally and remotely`() = runTest(dispatcher) {
        val auth = mockk<FirebaseAuth> { every { currentUser } returns user }
        val vm = AppSessionViewModel(auth, prefs, profiles, pushRegistrar)
        vm.onOnboardingCompleted("Les Renards")
        advanceUntilIdle()
        assertTrue(vm.state.value.hasCompletedOnboarding)
        verify { editor.putString(AppConstants.PREF_USER_NICKNAME, "Les Renards") }
        coVerify { profiles.saveNickname("uid-1", "Les Renards") }
    }
}
