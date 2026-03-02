package dev.rahier.pouleparty.ui.chickenconfig

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ChickenConfigUiState(
    val game: Game = Game.mock,
    val codeCopied: Boolean = false,
    val showAlert: Boolean = false,
    val alertMessage: String = "",
    val showMapConfig: Boolean = false,
    val showTimePicker: Boolean = false
)

@HiltViewModel
class ChickenConfigViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""

    private val _uiState = MutableStateFlow(
        ChickenConfigUiState(
            game = Game(
                id = gameId,
                name = "",
                numberOfPlayers = 10,
                radiusIntervalUpdate = 5.0,
                initialRadius = 1500.0,
                radiusDeclinePerUpdate = 100.0,
                chickenHeadStartMinutes = 5.0,
                gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
                foundCode = Game.generateFoundCode(),
                creatorId = auth.currentUser?.uid ?: ""
            )
        )
    )
    val uiState: StateFlow<ChickenConfigUiState> = _uiState.asStateFlow()

    init {
        resolveInitialLocation()
    }

    private fun resolveInitialLocation() {
        if (locationRepository.hasFineLocationPermission()) {
            viewModelScope.launch {
                try {
                    val location = locationRepository.locationFlow().first()
                    _uiState.update { it.copy(game = it.game.withInitialLocation(location)) }
                } catch (_: Exception) {
                    // Fallback: keep Brussels default
                }
            }
        }
    }

    fun onStartTimeTapped() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun dismissTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun updateStartDate(hour: Int, minute: Int) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
        }
        // Ensure at least 2 min from now
        val minDate = Date(System.currentTimeMillis() + 120_000)
        if (cal.time.before(minDate)) {
            cal.time = minDate
        }
        _uiState.update { it.copy(game = it.game.withStartDate(cal.time), showTimePicker = false) }
    }

    fun updateGameMod(mod: GameMod) {
        _uiState.update { it.copy(game = it.game.copy(gameMod = mod.firestoreValue)) }
    }

    fun updateRadiusIntervalUpdate(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(radiusIntervalUpdate = value)) }
    }

    fun updateRadiusDecline(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(radiusDeclinePerUpdate = value)) }
    }

    fun updateChickenHeadStart(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(chickenHeadStartMinutes = value)) }
    }

    fun updateInitialRadius(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(initialRadius = value)) }
    }

    fun updateGame(game: Game) {
        _uiState.update { it.copy(game = game) }
    }

    fun onCodeCopied() {
        _uiState.update { it.copy(codeCopied = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _uiState.update { it.copy(codeCopied = false) }
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(showAlert = false) }
    }

    fun onMapSetupTapped() {
        _uiState.update { it.copy(showMapConfig = true) }
    }

    fun dismissMapConfig() {
        _uiState.update { it.copy(showMapConfig = false) }
    }

    fun onLocationSelected(point: com.mapbox.geojson.Point, radius: Double) {
        _uiState.update {
            it.copy(game = it.game.withInitialLocation(point).copy(initialRadius = radius))
        }
    }

    fun startGame(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Auto-calculate endDate from radius parameters
                val game = _uiState.value.game
                val shrinks = ceil(game.initialRadius / game.radiusDeclinePerUpdate)
                val durationMs = (shrinks * game.radiusIntervalUpdate * 60 * 1000).toLong()
                val endDate = Date(game.hunterStartDate.time + durationMs)
                val finalGame = game.withEndDate(endDate)
                firestoreRepository.setConfig(finalGame)
                onSuccess(finalGame.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showAlert = true,
                        alertMessage = "Could not create the game. Please check your connection and try again."
                    )
                }
            }
        }
    }
}
