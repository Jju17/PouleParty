package dev.rahier.pouleparty.ui.chickenmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.HunterLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class HunterAnnotation(
    val id: String,
    val coordinate: LatLng,
    val displayName: String
)

data class ChickenMapUiState(
    val game: Game = Game.mock,
    val hunterAnnotations: List<HunterAnnotation> = emptyList(),
    val nextRadiusUpdate: Date? = null,
    val nowDate: Date = Date(),
    val radius: Int = 1500,
    val circleCenter: LatLng? = null,
    val showCancelAlert: Boolean = false,
    val showGameOverAlert: Boolean = false,
    val gameOverMessage: String = "",
    val showGameInfo: Boolean = false,
    val codeCopied: Boolean = false,
    val showFoundCode: Boolean = false,
    val previousWinnersCount: Int = 0,
    val winnerNotification: String? = null
)

@HiltViewModel
class ChickenMapViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""

    private val _uiState = MutableStateFlow(ChickenMapUiState())
    val uiState: StateFlow<ChickenMapUiState> = _uiState.asStateFlow()

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
            startLocationTracking(game)
            startHunterTracking(game)
            startGameConfigStream()
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

                        // In stayInTheZone, circle is fixed — update with new radius
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

    /**
     * Chicken sends its position to hunters (except in stayInTheZone mode).
     * Circle follows chicken position in followTheChicken and mutualTracking.
     */
    private fun startLocationTracking(game: Game) {
        if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) return

        viewModelScope.launch {
            var lastWrite = Date(0)
            locationRepository.locationFlow().collect { latLng ->
                _uiState.value = _uiState.value.copy(circleCenter = latLng)

                // Throttle Firestore writes to every 5 seconds
                if (Date().time - lastWrite.time >= 5000) {
                    firestoreRepository.setChickenLocation(gameId, latLng)
                    lastWrite = Date()
                }
            }
        }
    }

    /**
     * In mutualTracking mode, chicken can see all hunter positions.
     */
    private fun startHunterTracking(game: Game) {
        if (game.gameModEnum != GameMod.MUTUAL_TRACKING) return

        viewModelScope.launch {
            firestoreRepository.hunterLocationsFlow(gameId).collect { hunters ->
                val sorted = hunters.sortedBy { it.hunterId }
                val annotations = sorted.mapIndexed { index, hunter ->
                    HunterAnnotation(
                        id = hunter.hunterId,
                        coordinate = LatLng(hunter.location.latitude, hunter.location.longitude),
                        displayName = "Hunter ${index + 1}"
                    )
                }
                _uiState.value = _uiState.value.copy(hunterAnnotations = annotations)
            }
        }
    }

    /** Stream game config to detect winners in real time */
    private fun startGameConfigStream() {
        viewModelScope.launch {
            firestoreRepository.gameConfigFlow(gameId).collect { updatedGame ->
                if (updatedGame != null) {
                    val previousCount = _uiState.value.previousWinnersCount
                    _uiState.value = _uiState.value.copy(
                        game = updatedGame,
                        previousWinnersCount = updatedGame.winners.size
                    )

                    // Detect new winners
                    if (updatedGame.winners.size > previousCount) {
                        val latest = updatedGame.winners.last()
                        _uiState.value = _uiState.value.copy(
                            winnerNotification = "${latest.hunterName} a trouvé la poule !"
                        )
                        delay(4000)
                        _uiState.value = _uiState.value.copy(winnerNotification = null)
                    }
                }
            }
        }
    }

    fun onCancelGameTapped() {
        _uiState.value = _uiState.value.copy(showCancelAlert = true)
    }

    fun dismissCancelAlert() {
        _uiState.value = _uiState.value.copy(showCancelAlert = false)
    }

    fun confirmCancelGame(onGoToMenu: () -> Unit) {
        _uiState.value = _uiState.value.copy(showCancelAlert = false)
        viewModelScope.launch {
            firestoreRepository.deleteConfig(gameId)
            onGoToMenu()
        }
    }

    fun confirmGameOver(onGoToMenu: () -> Unit) {
        _uiState.value = _uiState.value.copy(showGameOverAlert = false)
        onGoToMenu()
    }

    fun onInfoTapped() {
        _uiState.value = _uiState.value.copy(showGameInfo = true)
    }

    fun dismissGameInfo() {
        _uiState.value = _uiState.value.copy(showGameInfo = false)
    }

    fun onFoundButtonTapped() {
        _uiState.value = _uiState.value.copy(showFoundCode = true)
    }

    fun dismissFoundCode() {
        _uiState.value = _uiState.value.copy(showFoundCode = false)
    }

    fun onCodeCopied() {
        _uiState.value = _uiState.value.copy(codeCopied = true)
        viewModelScope.launch {
            delay(1000)
            _uiState.value = _uiState.value.copy(codeCopied = false)
        }
    }

    val chickenSubtitle: String
        get() = when (_uiState.value.game.gameModEnum) {
            GameMod.FOLLOW_THE_CHICKEN -> "Don't be seen !"
            GameMod.STAY_IN_THE_ZONE -> "Stay in the zone \uD83D\uDCCD"
            GameMod.MUTUAL_TRACKING -> "You can see them \uD83D\uDC40"
        }
}
