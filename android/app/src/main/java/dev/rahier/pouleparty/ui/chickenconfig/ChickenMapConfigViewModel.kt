package dev.rahier.pouleparty.ui.chickenconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapConfigUiState(
    val cameraCenter: Point = Point.fromLngLat(AppConstants.DEFAULT_LONGITUDE, AppConstants.DEFAULT_LATITUDE),
    val cameraZoom: Float = AppConstants.MAP_CAMERA_ZOOM,
    val markerPosition: Point = Point.fromLngLat(AppConstants.DEFAULT_LONGITUDE, AppConstants.DEFAULT_LATITUDE),
    val radius: Double = AppConstants.DEFAULT_INITIAL_RADIUS
)

@HiltViewModel
class ChickenMapConfigViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapConfigUiState())
    val uiState: StateFlow<MapConfigUiState> = _uiState.asStateFlow()

    fun initialize(initialRadius: Double) {
        _uiState.update { it.copy(radius = initialRadius) }

        if (locationRepository.hasFineLocationPermission()) {
            viewModelScope.launch {
                try {
                    val location = locationRepository.locationFlow().first()
                    _uiState.update {
                        it.copy(
                            cameraCenter = location,
                            cameraZoom = 14f,
                            markerPosition = location
                        )
                    }
                } catch (_: Exception) {
                    // Fallback: stay at Brussels
                }
            }
        }
    }

    fun onCameraMove(center: Point) {
        _uiState.update { it.copy(markerPosition = center) }
    }

    fun onRadiusChanged(radius: Double) {
        _uiState.update { it.copy(radius = radius) }
    }
}
