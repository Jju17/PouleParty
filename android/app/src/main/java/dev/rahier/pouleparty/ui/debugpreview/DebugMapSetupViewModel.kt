package dev.rahier.pouleparty.ui.debugpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GamePowerUps
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.model.calculateNormalModeSettings
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state for [DebugMapSetupScreen]. Mirrors the pin/radius model
 * the GameCreation wizard holds in `GameCreationUiState` but keeps
 * nothing else — timing/seed are finalized at Launch time so the
 * countdown is always 60 s from the tap.
 */
data class DebugMapSetupUiState(
    val initialLocation: Point = Point.fromLngLat(
        AppConstants.DEFAULT_LONGITUDE,
        AppConstants.DEFAULT_LATITUDE
    ),
    val finalLocation: Point? = Point.fromLngLat(
        AppConstants.DEFAULT_LONGITUDE,
        AppConstants.DEFAULT_LATITUDE
    ),
    val radius: Double = 1500.0,
)

sealed interface DebugMapSetupEffect {
    data class NavigateToChickenMapDebug(val gameId: String) : DebugMapSetupEffect
}

@HiltViewModel
class DebugMapSetupViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugMapSetupUiState())
    val uiState: StateFlow<DebugMapSetupUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DebugMapSetupEffect>(Channel.BUFFERED)
    val effects: Flow<DebugMapSetupEffect> = _effects.receiveAsFlow()

    init {
        // Seed both pins with the last known location so the user lands
        // on something meaningful instead of an arbitrary Brussels pin.
        // Suspending fetch runs off the main thread; Brussels defaults
        // stay visible until the GPS resolves.
        viewModelScope.launch {
            locationRepository.getLastLocation()?.let { here ->
                _uiState.update { it.copy(initialLocation = here, finalLocation = here) }
            }
        }
    }

    fun onLocationSelected(point: Point) {
        _uiState.update { it.copy(initialLocation = point) }
    }

    fun onFinalLocationSelected(point: Point?) {
        _uiState.update { it.copy(finalLocation = point) }
    }

    fun onRadiusChanged(radius: Double) {
        _uiState.update { it.copy(radius = radius) }
    }

    /**
     * Builds the preset stayInTheZone game (1 min start, 1 h long, no
     * head start, no power-ups) using the currently selected pins and
     * radius, writes it to Firestore, then emits a navigation effect
     * so the host can route to the chicken map in debug preview mode.
     */
    fun onLaunchTapped() {
        val creatorId = auth.currentUser?.uid
        if (creatorId.isNullOrEmpty()) {
            android.util.Log.e("DebugMapSetupVM", "no current user id — can't launch")
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            val startMs = System.currentTimeMillis() + 60_000L
            val endMs = startMs + 60L * 60_000L
            val (interval, decline) = calculateNormalModeSettings(
                initialRadius = state.radius,
                gameDurationMinutes = 60.0
            )
            val driftSeed = (1..999_999).random()
            val gameId = java.util.UUID.randomUUID().toString()
            val finalCenter = state.finalLocation ?: state.initialLocation
            val game = Game(
                id = gameId,
                name = "DEBUG PREVIEW",
                maxPlayers = 1,
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                foundCode = Game.generateFoundCode(),
                creatorId = creatorId,
                timing = Timing(
                    start = Timestamp(Date(startMs)),
                    end = Timestamp(Date(endMs)),
                    headStartMinutes = 0.0
                ),
                zone = Zone(
                    center = GeoPoint(state.initialLocation.latitude(), state.initialLocation.longitude()),
                    finalCenter = GeoPoint(finalCenter.latitude(), finalCenter.longitude()),
                    radius = state.radius,
                    shrinkIntervalMinutes = interval,
                    shrinkMetersPerUpdate = decline,
                    driftSeed = driftSeed
                ),
                powerUps = GamePowerUps(enabled = false)
            )
            try {
                firestoreRepository.setConfig(game)
                _effects.send(DebugMapSetupEffect.NavigateToChickenMapDebug(gameId))
            } catch (e: Exception) {
                android.util.Log.e("DebugMapSetupVM", "setConfig failed", e)
            }
        }
    }
}
