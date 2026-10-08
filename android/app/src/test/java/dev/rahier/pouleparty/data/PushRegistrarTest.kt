package dev.rahier.pouleparty.data

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dev.rahier.pouleparty.AppConstants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PushRegistrarTest {

    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val auth = mockk<FirebaseAuth>()
    private val profiles = mockk<UserProfileRepository>(relaxed = true)

    private fun signedIn(uid: String?) {
        every { auth.currentUser } returns uid?.let { id -> mockk<FirebaseUser> { every { this@mockk.uid } returns id } }
    }

    @Test
    fun `nothing is written before sign-in`() = runTest {
        signedIn(null)
        every { prefs.getString(AppConstants.PREF_PUSH_REGISTRATION_ID, null) } returns "fid-1"
        PushRegistrar(prefs, auth, profiles).saveForCurrentUser()
        coVerify(exactly = 0) { profiles.savePushRegistration(any(), any(), any()) }
    }

    @Test
    fun `the kept registration is saved once the user exists`() = runTest {
        signedIn("uid-1")
        every { prefs.getString(AppConstants.PREF_PUSH_REGISTRATION_ID, null) } returns "fid-1"
        every { prefs.getString(AppConstants.PREF_USER_NICKNAME, "") } returns " Julie "
        var requested = false
        PushRegistrar(prefs, auth, profiles).syncAfterSignIn { requested = true }
        coVerify { profiles.savePushRegistration("uid-1", "fid-1", "Julie") }
        assertEquals(true, requested)
    }

    @Test
    fun `a failed save or registration request does not throw`() = runTest {
        signedIn("uid-1")
        every { prefs.getString(AppConstants.PREF_PUSH_REGISTRATION_ID, null) } returns "fid-1"
        every { prefs.getString(AppConstants.PREF_USER_NICKNAME, "") } returns ""
        coEvery { profiles.savePushRegistration(any(), any(), any()) } throws IllegalStateException("offline")
        PushRegistrar(prefs, auth, profiles).syncAfterSignIn { error("no play services") }
    }

    @Test
    fun `registration fields carry the id under token and skip a blank nickname`() {
        val fields = pushRegistrationFields("fid-9", "  ")
        assertEquals("fid-9", fields["token"])
        assertEquals("android", fields["platform"])
        assertFalse(fields.containsKey("nickname"))
        assertEquals("Max", pushRegistrationFields("fid-9", "Max")["nickname"])
    }
}
