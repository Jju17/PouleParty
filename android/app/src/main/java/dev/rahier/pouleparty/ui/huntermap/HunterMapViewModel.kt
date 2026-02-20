package dev.rahier.pouleparty.ui.huntermap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class HunterMapUiState(
    val game: Game = Game.mock,
    val nextRadiusUpdate: Date? = null,
    val nowDate: Date = Date(),
    val radius: Int = 1500,
    val circleCenter: LatLng? = null,
    val showLeaveAlert: Boolean = false,
    val showGameOverAlert: Boolean = false,
    val gameOverMessage: String = ""
)

@HiltViewModel
class HunterMapViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val hunterId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(HunterMapUiState())
    val uiState: StateFlow<HunterMapUiState> = _uiState.asStateFlow()

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
            val (lastUpdate, lastRadius) = game.findLastUpdate()

            _uiState.value = _uiState.value.copy(
                game = game,
                radius = lastRadius,
                nextRadiusUpdate = lastUpdate,
                circleCenter = game.initialLocation
            )

            startTimer()
            startGameConfigStream(game)
            startChickenLocationStream(game)
            startHunterLocationSending(game)
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _uiState.value
                _uiState.value = state.copy(nowDate = Date())

                if (state.showGameOverAlert) continue

                if (Date().after(state.game.endDate)) {
                    _uiState.value = _uiState.value.copy(
                        showGameOverAlert = true,
                        gameOverMessage = "Time's up! The Chicken survived!"
                    )
                    continue
                }

                val nextUpdate = state.nextRadiusUpdate ?: continue
                if (Date().after(nextUpdate)) {
                    val game = state.game
                    val newRadius = state.radius - game.radiusDeclinePerUpdate.toInt()
                    if (newRadius > 0) {
                        val intervalMs = (game.radiusIntervalUpdate * 60 * 1000).toLong()
                        val newNextUpdate = Date(nextUpdate.time + intervalMs)
                        _uiState.value = _uiState.value.copy(
                            radius = newRadius,
                            nextRadiusUpdate = newNextUpdate
                        )

                        // In stayInTheZone, circle stays fixed
                        if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                            _uiState.value = _uiState.value.copy(
                                circleCenter = game.initialLocation
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            showGameOverAlert = true,
                            gameOverMessage = "The zone has collapsed!"
                        )
                    }
                }
            }
        }
    }

    /** Stream game config changes in real time */
    private fun startGameConfigStream(game: Game) {
        viewModelScope.launch {
            firestoreRepository.gameConfigFlow(gameId).collect { updatedGame ->
                if (updatedGame != null) {
                    val (lastUpdate, lastRadius) = updatedGame.findLastUpdate()
                    _uiState.value = _uiState.value.copy(
                        game = updatedGame,
                        radius = lastRadius,
                        nextRadiusUpdate = lastUpdate
                    )
                    // Only reset circle center for stayInTheZone (fixed zone)
                    if (updatedGame.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                        _uiState.value = _uiState.value.copy(
                            circleCenter = updatedGame.initialLocation
                        )
                    }
                }
            }
        }
    }

    /**
     * Circle follows chicken position in followTheChicken and mutualTracking modes.
     * In stayInTheZone, no chicken stream (circle stays on initial coordinates).
     */
    private fun startChickenLocationStream(game: Game) {
        if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) return

        viewModelScope.launch {
            firestoreRepository.chickenLocationFlow(gameId).collect { location ->
                if (location != null) {
                    _uiState.value = _uiState.value.copy(circleCenter = location)
                }
            }
        }
    }

    /**
     * In mutualTracking mode, hunter sends its own position.
     * Throttled to every 5 seconds (matches iOS).
     */
    private fun startHunterLocationSending(game: Game) {
        if (game.gameModEnum != GameMod.MUTUAL_TRACKING) return

        viewModelScope.launch {
            var lastWrite = Date(0)
            locationRepository.locationFlow().collect { latLng ->
                if (Date().time - lastWrite.time >= 5000) {
                    firestoreRepository.setHunterLocation(gameId, hunterId, latLng)
                    lastWrite = Date()
                }
            }
        }
    }

    fun onLeaveGameTapped() {
        _uiState.value = _uiState.value.copy(showLeaveAlert = true)
    }

    fun dismissLeaveAlert() {
        _uiState.value = _uiState.value.copy(showLeaveAlert = false)
    }

    fun confirmLeaveGame(onGoToMenu: () -> Unit) {
        _uiState.value = _uiState.value.copy(showLeaveAlert = false)
        onGoToMenu()
    }

    fun confirmGameOver(onGoToMenu: () -> Unit) {
        _uiState.value = _uiState.value.copy(showGameOverAlert = false)
        onGoToMenu()
    }

    val hunterSubtitle: String
        get() = when (_uiState.value.game.gameModEnum) {
            GameMod.FOLLOW_THE_CHICKEN -> "Catch the \uD83D\uDC14 !"
            GameMod.STAY_IN_THE_ZONE -> "Stay in the zone \uD83D\uDCCD"
            GameMod.MUTUAL_TRACKING -> "Catch the \uD83D\uDC14 (she sees you! \uD83D\uDC40)"
        }
}
