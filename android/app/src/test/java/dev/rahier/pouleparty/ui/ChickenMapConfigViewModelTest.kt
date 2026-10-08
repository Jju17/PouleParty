package dev.rahier.pouleparty.ui

import android.content.Context
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.chickenmapconfig.ChickenMapConfigViewModel
import dev.rahier.pouleparty.ui.chickenmapconfig.MapConfigPinMode
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

    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChickenMapConfigViewModel {
        return ChickenMapConfigViewModel(locationRepository, context)
    }

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
        vm.onMapTapped(farAway)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

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
        vm.setPinMode(MapConfigPinMode.START)
        vm.onMapTapped(farAway)
        assertNotNull(vm.uiState.value.finalMarkerPosition)
    }

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
