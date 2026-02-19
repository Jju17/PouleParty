package dev.rahier.pouleparty.ui.chickenconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapConfigUiState(
    val cameraPosition: CameraPosition = CameraPosition.fromLatLngZoom(
        LatLng(50.8503, 4.3517), 14f
    ),
    val markerPosition: LatLng = LatLng(50.8503, 4.3517),
    val radius: Double = 1500.0
)

@HiltViewModel
class ChickenMapConfigViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapConfigUiState())
    val uiState: StateFlow<MapConfigUiState> = _uiState.asStateFlow()

    fun initialize(initialRadius: Double) {
        _uiState.value = _uiState.value.copy(radius = initialRadius)

        if (locationRepository.hasFineLocationPermission()) {
            viewModelScope.launch {
                try {
                    val location = locationRepository.locationFlow().first()
                    _uiState.value = _uiState.value.copy(
                        cameraPosition = CameraPosition.fromLatLngZoom(location, 14f),
                        markerPosition = location
                    )
                } catch (_: Exception) {
                    // Fallback: stay at Brussels
                }
            }
        }
    }

    fun onCameraMove(center: LatLng) {
        _uiState.value = _uiState.value.copy(markerPosition = center)
    }

    fun onRadiusChanged(radius: Double) {
        _uiState.value = _uiState.value.copy(radius = radius)
    }
}
