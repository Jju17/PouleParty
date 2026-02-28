package dev.rahier.pouleparty.ui.chickenconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
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
    val cameraPosition: CameraPosition = CameraPosition.fromLatLngZoom(
        LatLng(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE), AppConstants.MAP_CAMERA_ZOOM
    ),
    val markerPosition: LatLng = LatLng(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
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
                            cameraPosition = CameraPosition.fromLatLngZoom(location, 14f),
                            markerPosition = location
                        )
                    }
                } catch (_: Exception) {
                    // Fallback: stay at Brussels
                }
            }
        }
    }

    fun onCameraMove(center: LatLng) {
        _uiState.update { it.copy(markerPosition = center) }
    }

    fun onRadiusChanged(radius: Double) {
        _uiState.update { it.copy(radius = radius) }
    }
}
