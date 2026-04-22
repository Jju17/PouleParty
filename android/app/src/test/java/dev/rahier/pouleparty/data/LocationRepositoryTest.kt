package dev.rahier.pouleparty.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the permission-query helpers on [LocationRepository].
 *
 * Targets the pre-Android 10 fallback added when we tightened the onboarding
 * gate to require `Always`, on API < 29 the `ACCESS_BACKGROUND_LOCATION`
 * permission didn't exist as a separate runtime check, so `checkSelfPermission`
 * reports DENIED and the repo must short-circuit to `hasFineLocationPermission`.
 *
 * Unit tests run with `unitTests.isReturnDefaultValues = true`, which leaves
 * `Build.VERSION.SDK_INT` at 0 → we are always on the pre-Q branch here,
 * which is exactly what these tests exercise. The Q+ branch is covered by
 * on-device integration and the onboarding behavior tests.
 */
class LocationRepositoryTest {

    private lateinit var context: Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fusedLocationClient = mockk(relaxed = true)
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `hasBackgroundLocationPermission returns true pre-Q when fine is granted`() {
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns
            PackageManager.PERMISSION_GRANTED

        val repo = LocationRepository(context, fusedLocationClient)
        assertTrue(repo.hasBackgroundLocationPermission())
    }

    @Test
    fun `hasBackgroundLocationPermission returns false pre-Q when fine is denied`() {
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns
            PackageManager.PERMISSION_DENIED

        val repo = LocationRepository(context, fusedLocationClient)
        assertFalse(repo.hasBackgroundLocationPermission())
    }

    @Test
    fun `hasFineLocationPermission reflects ContextCompat result`() {
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns
            PackageManager.PERMISSION_GRANTED

        val repo = LocationRepository(context, fusedLocationClient)
        assertTrue(repo.hasFineLocationPermission())

        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns
            PackageManager.PERMISSION_DENIED
        assertFalse(repo.hasFineLocationPermission())
    }
}
