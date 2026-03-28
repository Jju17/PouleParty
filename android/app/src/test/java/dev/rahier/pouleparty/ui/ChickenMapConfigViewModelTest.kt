package dev.rahier.pouleparty.ui

import android.content.Context
import android.location.Location
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.chickenconfig.ChickenMapConfigViewModel
import dev.rahier.pouleparty.ui.chickenconfig.MapConfigPinMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.*

@OptIn(ExperimentalCoroutinesApi::class)
class ChickenMapConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var locationRepository: LocationRepository
    private lateinit var context: Context

    // Brussels center
    private val brussels = Point.fromLngLat(4.3528, 50.8466)
    // ~500m away from Brussels
    private val nearby = Point.fromLngLat(4.3580, 50.8500)
    // ~2km away from Brussels
    private val farAway = Point.fromLngLat(4.3900, 50.8600)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        locationRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { locationRepository.hasFineLocationPermission() } returns false

        // Mock Location.distanceBetween using Haversine formula
        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val lat1 = Math.toRadians(arg<Double>(0))
            val lon1 = Math.toRadians(arg<Double>(1))
            val lat2 = Math.toRadians(arg<Double>(2))
            val lon2 = Math.toRadians(arg<Double>(3))
            val results = arg<FloatArray>(4)
            val dlat = lat2 - lat1
            val dlon = lon2 - lon1
            val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            results[0] = (6371000.0 * c).toFloat()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Location::class)
    }

    private fun createViewModel(): ChickenMapConfigViewModel {
        return ChickenMapConfigViewModel(locationRepository, context)
    }

    // ── Final zone validation: must be within start zone radius ──

    @Test
    fun `final zone tap inside start zone is accepted`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Place start zone at Brussels
        vm.onMapTapped(brussels)
        // Switch to final mode
        vm.setPinMode(MapConfigPinMode.FINAL)
        // Tap nearby (within 1500m)
        vm.onMapTapped(nearby)

        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `final zone tap outside start zone is rejected`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Place start zone at Brussels
        vm.onMapTapped(brussels)
        // Switch to final mode
        vm.setPinMode(MapConfigPinMode.FINAL)
        // Tap far away (>1500m)
        vm.onMapTapped(farAway)

        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── Final zone cleared when start zone moves away ──

    @Test
    fun `moving start zone clears final zone if now outside radius`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Place start + final close together
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)

        // Now move start zone far away — final should be cleared
        vm.setPinMode(MapConfigPinMode.START)
        vm.onMapTapped(farAway)
        // After moving start, final should stay only if it's within radius of new start
        // farAway is ~2km from nearby, and radius is 1500m, so final should be cleared
        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── Final zone cleared when radius shrinks ──

    @Test
    fun `shrinking radius clears final zone if now outside`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Place start at Brussels
        vm.onMapTapped(brussels)
        // Place final ~500m away
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)

        // Shrink radius to 200m — final at 500m should be cleared
        vm.updateRadius(200.0)
        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `shrinking radius keeps final zone if still inside`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Place start at Brussels
        vm.onMapTapped(brussels)
        // Place final ~500m away
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)

        // Shrink radius to 1000m — final at 500m should still be inside
        vm.updateRadius(1000.0)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── Start zone tap auto-validates existing final zone ──

    @Test
    fun `onMapTapped in start mode validates existing final zone`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Place start at Brussels, final nearby
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)

        // Move start far from final — should clear final
        vm.setPinMode(MapConfigPinMode.START)
        // Place start far from the existing final
        val veryFar = Point.fromLngLat(5.0, 51.0) // ~50km away
        vm.onMapTapped(veryFar)
        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── Edge case: radius at exact boundary ──

    @Test
    fun `final zone at exact radius boundary is accepted`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        // Use the same point for start — final at distance 0 should always be accepted
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(brussels) // distance = 0, always within radius
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── Initialize with existing final marker ──

    @Test
    fun `initialize preserves existing final marker`() {
        val vm = createViewModel()
        vm.initialize(1500.0, nearby)

        assertEquals(nearby, vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `initialize without final marker has null final`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)

        assertNull(vm.uiState.value.finalMarkerPosition)
    }
}
