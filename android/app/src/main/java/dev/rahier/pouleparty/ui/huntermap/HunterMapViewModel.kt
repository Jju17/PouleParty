package dev.rahier.pouleparty.ui.huntermap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.Winner
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
    val gameOverMessage: String = "",
    val isEnteringFoundCode: Boolean = false,
    val enteredCode: String = "",
    val showWrongCodeAlert: Boolean = false,
    val previousWinnersCount: Int = 0,
    val winnerNotification: String? = null,
    val shouldNavigateToVictory: Boolean = false,
    val hasGameStarted: Boolean = false,
    val countdownNumber: Int? = null,
    val countdownText: String? = null
)

@HiltViewModel
class HunterMapViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val gameId: String = savedStateHandle["gameId"] ?: ""
    val hunterName: String = savedStateHandle["hunterName"] ?: "Hunter"
    val hunterId: String = UUID.randomUUID().toString()

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
                nextRadiusUpdate = lastUpdate
            )

            firestoreRepository.registerHunter(gameId, hunterId)
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
                val now = Date()
                val gameStarted = now.after(state.game.hunterStartDate) || now == state.game.hunterStartDate
                _uiState.value = state.copy(nowDate = now, hasGameStarted = gameStarted)

                // Phase 1: Chicken departure countdown (only if head start > 0)
                if (state.game.chickenHeadStartMinutes > 0) {
                    val timeToChickenMs = state.game.startDate.time - now.time
                    val timeToChickenSec = timeToChickenMs / 1000.0
                    if (timeToChickenSec in 0.0..3.0) {
                        val number = kotlin.math.ceil(timeToChickenSec).toInt()
                        if (number > 0 && _uiState.value.countdownNumber != number) {
                            _uiState.value = _uiState.value.copy(countdownNumber = number, countdownText = null)
                        }
                    } else if (timeToChickenSec <= 0 && timeToChickenSec > -1 && _uiState.value.countdownText == null) {
                        _uiState.value = _uiState.value.copy(countdownNumber = null, countdownText = "\uD83D\uDC14 is hiding!")
                        delay(1500)
                        _uiState.value = _uiState.value.copy(countdownText = null)
                    }
                }

                // Phase 2: Hunt start countdown: 3, 2, 1, LET'S HUNT!
                val timeToHuntMs = state.game.hunterStartDate.time - now.time
                val timeToHuntSec = timeToHuntMs / 1000.0
                if (timeToHuntSec in 0.0..3.0) {
                    val number = kotlin.math.ceil(timeToHuntSec).toInt()
                    if (number > 0 && _uiState.value.countdownNumber != number) {
                        _uiState.value = _uiState.value.copy(countdownNumber = number, countdownText = null)
                    }
                } else if (timeToHuntSec <= 0 && timeToHuntSec > -1 && _uiState.value.countdownText == null) {
                    _uiState.value = _uiState.value.copy(countdownNumber = null, countdownText = "LET'S HUNT! \uD83D\uDD0D")
                    delay(1500)
                    _uiState.value = _uiState.value.copy(countdownText = null)
                }

                if (state.showGameOverAlert) continue

                // Don't process game-over or radius updates before hunt starts
                if (!gameStarted) continue

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
                    val previousCount = _uiState.value.previousWinnersCount

                    _uiState.value = _uiState.value.copy(
                        game = updatedGame,
                        radius = lastRadius,
                        nextRadiusUpdate = lastUpdate,
                        previousWinnersCount = updatedGame.winners.size
                    )

                    // Only reset circle center for stayInTheZone (fixed zone)
                    if (updatedGame.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                        _uiState.value = _uiState.value.copy(
                            circleCenter = updatedGame.initialLocation
                        )
                    }

                    // Detect new winners
                    if (updatedGame.winners.size > previousCount) {
                        val latest = updatedGame.winners.last()
                        if (latest.hunterId != hunterId) {
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
    }

    /**
     * Circle follows chicken position in followTheChicken and mutualTracking modes.
     * In stayInTheZone, no chicken stream (circle stays on initial coordinates).
     */
    private fun startChickenLocationStream(game: Game) {
        if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) return

        viewModelScope.launch {
            val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
            if (delayMs > 0) delay(delayMs)
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
            val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
            if (delayMs > 0) delay(delayMs)

            // Send current location immediately on connect
            locationRepository.getLastLocation()?.let { latLng ->
                firestoreRepository.setHunterLocation(gameId, hunterId, latLng)
            }

            var lastWrite = Date()
            locationRepository.locationFlow().collect { latLng ->
                if (Date().time - lastWrite.time >= 5000) {
                    firestoreRepository.setHunterLocation(gameId, hunterId, latLng)
                    lastWrite = Date()
                }
            }
        }
    }

    fun onFoundButtonTapped() {
        _uiState.value = _uiState.value.copy(isEnteringFoundCode = true)
    }

    fun onEnteredCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(enteredCode = code)
    }

    fun dismissFoundCodeEntry() {
        _uiState.value = _uiState.value.copy(isEnteringFoundCode = false, enteredCode = "")
    }

    fun submitFoundCode() {
        val code = _uiState.value.enteredCode.trim()
        _uiState.value = _uiState.value.copy(isEnteringFoundCode = false, enteredCode = "")

        if (code != _uiState.value.game.foundCode) {
            _uiState.value = _uiState.value.copy(showWrongCodeAlert = true)
            return
        }

        viewModelScope.launch {
            val winner = Winner(
                hunterId = hunterId,
                hunterName = hunterName,
                timestamp = Timestamp.now()
            )
            firestoreRepository.addWinner(gameId, winner)
            _uiState.value = _uiState.value.copy(shouldNavigateToVictory = true)
        }
    }

    fun dismissWrongCodeAlert() {
        _uiState.value = _uiState.value.copy(showWrongCodeAlert = false)
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
