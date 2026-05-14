package dev.rahier.pouleparty.ui

import android.content.Context
import android.location.Location
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.chickenmapconfig.ChickenMapConfigViewModel
import dev.rahier.pouleparty.ui.chickenmapconfig.MapConfigPinMode
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

    // ── PP-11 / PP-12: final-pin placement is unconstrained by the
    //    slider-controlled start radius. The recap step (PP-13) picks
    //    the radius from the pin distance, so PP-12 only enforces the
    //    ≥ 100 m minimum (via `GameCreationUiState.isFinalZoneConfigured`,
    //    not at tap time). Tap inside or outside the legacy circle:
    //    both stick. ──

    @Test
    fun `final zone tap inside start zone is accepted`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `final zone tap outside start zone is still accepted (PP-12 no radius constraint)`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        // Pre-PP-11 this would be rejected (> 1500 m from start); PP-12
        // removed the radius constraint, so the pin sticks.
        vm.onMapTapped(farAway)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── PP-12: the only auto-clear path is when the user moves the
    //    START pin to within 100 m of the existing FINAL pin — at that
    //    distance the final wouldn't satisfy `isFinalZoneConfigured`
    //    anyway, so the VM clears it to force a re-placement. Any other
    //    start move leaves the final untouched. ──

    @Test
    fun `moving start zone within 100 m of final clears final`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
        // Move start to within 100 m of the existing final → cleared.
        vm.setPinMode(MapConfigPinMode.START)
        val withinHundredMeters = Point.fromLngLat(nearby.longitude() + 0.0005, nearby.latitude())
        vm.onMapTapped(withinHundredMeters)
        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `moving start zone far away keeps final (PP-12 no radius gate)`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
        // Move start very far. Pre-PP-12 the final would be cleared
        // (outside the slider radius); now the recap recomputes the
        // radius from the new pin pair, so the final persists.
        vm.setPinMode(MapConfigPinMode.START)
        vm.onMapTapped(farAway)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── PP-13 radius slider only fires in followTheChicken now; in
    //    stayInTheZone the radius is recomputed at the recap step from
    //    the two pins. The VM helper `updateRadius` still validates the
    //    final against the new radius for the followTheChicken case
    //    (legacy code path) so we keep these tests as regression
    //    coverage for that branch. ──

    @Test
    fun `shrinking radius clears final zone if now outside`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
        vm.updateRadius(200.0)
        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `shrinking radius keeps final zone if still inside`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
        vm.updateRadius(1000.0)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

    // ── Edge case: placing start exactly on the final pin (distance = 0)
    //    triggers the < 100 m guard and clears the final. ──

    @Test
    fun `onMapTapped in start mode on top of final clears final`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        vm.onMapTapped(nearby)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
        vm.setPinMode(MapConfigPinMode.START)
        // Start on top of final → distance 0 → < 100 m guard → clears.
        vm.onMapTapped(nearby)
        assertNull(vm.uiState.value.finalMarkerPosition)
    }

    @Test
    fun `final zone at exact start pin is accepted`() {
        val vm = createViewModel()
        vm.initialize(1500.0, null)
        vm.onMapTapped(brussels)
        vm.setPinMode(MapConfigPinMode.FINAL)
        // PP-12 no longer enforces a minimum at tap time — only at the
        // Next button via `isFinalZoneConfigured`. So a coincident tap
        // still leaves the marker on the map.
        vm.onMapTapped(brussels)
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
