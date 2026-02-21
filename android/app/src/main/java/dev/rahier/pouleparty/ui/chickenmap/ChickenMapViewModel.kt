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
import dev.rahier.pouleparty.model.GameStatus
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
    val winnerNotification: String? = null,
    val hasGameStarted: Boolean = false,
    val hasHuntStarted: Boolean = false,
    val countdownNumber: Int? = null,
    val countdownText: String? = null
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
                val now = Date()
                val gameStarted = now.after(state.game.startDate) || now == state.game.startDate
                val huntStarted = now.after(state.game.hunterStartDate) || now == state.game.hunterStartDate
                _uiState.value = state.copy(nowDate = now, hasGameStarted = gameStarted, hasHuntStarted = huntStarted)

                // Pre-game countdown: 3, 2, 1, RUN!
                val timeToStartMs = state.game.startDate.time - now.time
                val timeToStartSec = timeToStartMs / 1000.0
                if (timeToStartSec in 0.0..3.0) {
                    val number = kotlin.math.ceil(timeToStartSec).toInt()
                    if (number > 0 && _uiState.value.countdownNumber != number) {
                        _uiState.value = _uiState.value.copy(countdownNumber = number, countdownText = null)
                    }
                } else if (timeToStartSec <= 0 && timeToStartSec > -1 && _uiState.value.countdownText == null) {
                    _uiState.value = _uiState.value.copy(countdownNumber = null, countdownText = "RUN! \uD83D\uDC14")
                    delay(1500)
                    _uiState.value = _uiState.value.copy(countdownText = null)
                }

                // Notification when hunters start (only if head start > 0)
                if (state.game.chickenHeadStartMinutes > 0) {
                    val timeToHuntMs = state.game.hunterStartDate.time - now.time
                    val timeToHuntSec = timeToHuntMs / 1000.0
                    if (timeToHuntSec <= 0 && timeToHuntSec > -1
                        && _uiState.value.countdownText == null
                        && _uiState.value.countdownNumber == null) {
                        _uiState.value = _uiState.value.copy(countdownText = "\uD83D\uDD0D Hunters incoming!")
                        delay(1500)
                        _uiState.value = _uiState.value.copy(countdownText = null)
                    }
                }

                if (state.showGameOverAlert) continue

                // Don't process game-over or radius updates before hunt starts
                if (!huntStarted) continue

                if (Date().after(state.game.endDate)) {
                    _uiState.value = _uiState.value.copy(
                        showGameOverAlert = true,
                        gameOverMessage = "Time's up! The Chicken survived!"
                    )
                    try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (_: Exception) {}
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
                        try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (_: Exception) {}
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
            val delayMs = game.startDate.time - System.currentTimeMillis()
            if (delayMs > 0) delay(delayMs)

            // Send current location immediately on connect
            locationRepository.getLastLocation()?.let { latLng ->
                _uiState.value = _uiState.value.copy(circleCenter = latLng)
                firestoreRepository.setChickenLocation(gameId, latLng)
            }

            var lastWrite = Date()
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
     * Gated behind hunterStartDate (hunters aren't active until then).
     */
    private fun startHunterTracking(game: Game) {
        if (game.gameModEnum != GameMod.MUTUAL_TRACKING) return

        viewModelScope.launch {
            val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
            if (delayMs > 0) delay(delayMs)
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
            try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (_: Exception) {}
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
