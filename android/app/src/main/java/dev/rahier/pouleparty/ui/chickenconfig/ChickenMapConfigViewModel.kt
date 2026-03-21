package dev.rahier.pouleparty.ui.chickenconfig

import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.ui.components.zoomForRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SearchResult(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)

enum class MapConfigPinMode { START, FINAL }

data class MapConfigUiState(
    val cameraCenter: Point = Point.fromLngLat(AppConstants.DEFAULT_LONGITUDE, AppConstants.DEFAULT_LATITUDE),
    val cameraZoom: Float = AppConstants.MAP_CAMERA_ZOOM,
    val markerPosition: Point = Point.fromLngLat(AppConstants.DEFAULT_LONGITUDE, AppConstants.DEFAULT_LATITUDE),
    val finalMarkerPosition: Point? = null,
    val radius: Double = AppConstants.DEFAULT_INITIAL_RADIUS,
    val pinMode: MapConfigPinMode = MapConfigPinMode.START,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList()
)

@HiltViewModel
class ChickenMapConfigViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapConfigUiState())
    val uiState: StateFlow<MapConfigUiState> = _uiState.asStateFlow()

    fun initialize(initialRadius: Double, finalMarker: Point?) {
        val zoom = zoomForRadius(initialRadius, AppConstants.DEFAULT_LATITUDE).toFloat()
        _uiState.update { it.copy(radius = initialRadius, cameraZoom = zoom, finalMarkerPosition = finalMarker) }

        if (locationRepository.hasFineLocationPermission()) {
            viewModelScope.launch {
                try {
                    val location = locationRepository.locationFlow().first()
                    val locationZoom = zoomForRadius(initialRadius, location.latitude()).toFloat()
                    _uiState.update {
                        it.copy(
                            cameraCenter = location,
                            cameraZoom = locationZoom,
                            markerPosition = location
                        )
                    }
                } catch (_: Exception) {
                    // Fallback: stay at Brussels
                }
            }
        }
    }

    fun setPinMode(mode: MapConfigPinMode) {
        _uiState.update { it.copy(pinMode = mode) }
    }

    fun onMapTapped(point: Point) {
        val state = _uiState.value
        if (state.pinMode == MapConfigPinMode.FINAL) {
            // Validate: final point must be within initial circle
            val results = FloatArray(1)
            Location.distanceBetween(
                state.markerPosition.latitude(), state.markerPosition.longitude(),
                point.latitude(), point.longitude(),
                results
            )
            if (results[0] > state.radius) return // Outside initial zone, ignore

            _uiState.update { it.copy(finalMarkerPosition = point) }
        } else {
            val zoom = zoomForRadius(state.radius, point.latitude()).toFloat()
            _uiState.update {
                it.copy(
                    markerPosition = point,
                    cameraCenter = point,
                    cameraZoom = zoom
                )
            }
        }
    }

    fun onCameraMove(center: Point) {
        _uiState.update { it.copy(markerPosition = center) }
    }

    fun onRadiusChanged(radius: Double) {
        _uiState.update { it.copy(radius = radius) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 3) {
            searchAddress(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    }

    fun onSearchResultSelected(result: SearchResult) {
        val point = Point.fromLngLat(result.longitude, result.latitude)
        val state = _uiState.value
        if (state.pinMode == MapConfigPinMode.FINAL) {
            val results = FloatArray(1)
            Location.distanceBetween(
                state.markerPosition.latitude(), state.markerPosition.longitude(),
                point.latitude(), point.longitude(),
                results
            )
            if (results[0] <= state.radius) {
                _uiState.update {
                    it.copy(
                        finalMarkerPosition = point,
                        searchQuery = result.title,
                        searchResults = emptyList()
                    )
                }
            }
        } else {
            val zoom = zoomForRadius(state.radius, result.latitude).toFloat()
            _uiState.update {
                it.copy(
                    markerPosition = point,
                    cameraCenter = point,
                    cameraZoom = zoom,
                    searchQuery = result.title,
                    searchResults = emptyList()
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun searchAddress(query: String) {
        viewModelScope.launch {
            try {
                val addresses = withContext(Dispatchers.IO) {
                    Geocoder(context).getFromLocationName(query, 5) ?: emptyList()
                }
                val results = addresses.map { address ->
                    SearchResult(
                        title = address.getAddressLine(0) ?: "",
                        subtitle = listOfNotNull(address.locality, address.countryName).joinToString(", "),
                        latitude = address.latitude,
                        longitude = address.longitude
                    )
                }
                _uiState.update { it.copy(searchResults = results) }
            } catch (_: Exception) {
                _uiState.update { it.copy(searchResults = emptyList()) }
            }
        }
    }
}
