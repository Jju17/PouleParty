package dev.rahier.pouleparty.ui.huntermap

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.CountdownPhase
import dev.rahier.pouleparty.ui.CountdownResult
import dev.rahier.pouleparty.ui.PlayerRole
import dev.rahier.pouleparty.ui.checkGameOverByTime
import dev.rahier.pouleparty.ui.checkZoneStatus
import dev.rahier.pouleparty.ui.detectNewWinners
import dev.rahier.pouleparty.ui.evaluateCountdown
import dev.rahier.pouleparty.ui.processRadiusUpdate
import dev.rahier.pouleparty.ui.shouldCheckZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import javax.inject.Inject

data class HunterMapUiState(
    val game: Game = Game.mock,
    val nextRadiusUpdate: Date? = null,
    val nowDate: Date = Date(),
    val radius: Int = 1500,
    val circleCenter: Point? = null,
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
    val countdownText: String? = null,
    val showGameInfo: Boolean = false,
    val codeCopied: Boolean = false,
    val wrongCodeAttempts: Int = 0,
    val codeCooldownUntil: Long = 0,
    val userLocation: Point? = null,
    val isOutsideZone: Boolean = false,
    val outsideZoneSince: Long = 0,
    val outsideZoneSeconds: Int = 0
)

@HiltViewModel
class HunterMapViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "HunterMapViewModel"
    }

    val gameId: String = savedStateHandle["gameId"] ?: ""
    val hunterName: String = savedStateHandle["hunterName"] ?: "Hunter"
    val hunterId: String = auth.currentUser?.uid ?: ""
    /** Tracked separately from viewModelScope to allow early cancellation on game over. */
    private val streamJobs = mutableListOf<Job>()

    private val _uiState = MutableStateFlow(HunterMapUiState())
    val uiState: StateFlow<HunterMapUiState> = _uiState.asStateFlow()

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            Log.d(TAG, "loadGame — gameId: $gameId, hunterId: '$hunterId', gameMod: checking...")
            if (hunterId.isEmpty()) {
                Log.e(TAG, "hunterId is empty — cannot register hunter or write location. auth.currentUser: ${auth.currentUser}")
                return@launch
            }
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
            Log.d(TAG, "loadGame — gameMod: ${game.gameModEnum}, shouldWriteLocation: ${game.gameModEnum == GameMod.MUTUAL_TRACKING}")
            val (lastUpdate, lastRadius) = game.findLastUpdate()

            _uiState.update {
                it.copy(
                    game = game,
                    radius = lastRadius,
                    nextRadiusUpdate = lastUpdate
                )
            }

            firestoreRepository.registerHunter(gameId, hunterId)
            startTimer()
            streamJobs += viewModelScope.launch { streamGameConfig(game) }
            streamJobs += viewModelScope.launch { streamChickenLocation(game) }
            streamJobs += viewModelScope.launch { trackHunterSelfLocation(game) }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val state = _uiState.value
                val now = Date()
                val gameStarted = now.after(state.game.hunterStartDate) || now == state.game.hunterStartDate
                _uiState.update { it.copy(nowDate = now, hasGameStarted = gameStarted) }

                // Countdown phases (hunter perspective)
                val countdownResult = evaluateCountdown(
                    phases = listOf(
                        CountdownPhase(
                            targetDate = state.game.startDate,
                            completionText = "\uD83D\uDC14 is hiding!",
                            showNumericCountdown = true,
                            isEnabled = state.game.chickenHeadStartMinutes > 0
                        ),
                        CountdownPhase(
                            targetDate = state.game.hunterStartDate,
                            completionText = "LET'S HUNT! \uD83D\uDD0D",
                            showNumericCountdown = true,
                            isEnabled = true
                        )
                    ),
                    now = now,
                    currentCountdownNumber = _uiState.value.countdownNumber,
                    currentCountdownText = _uiState.value.countdownText
                )
                when (countdownResult) {
                    is CountdownResult.NoChange -> {}
                    is CountdownResult.UpdateNumber -> {
                        _uiState.update { it.copy(countdownNumber = countdownResult.number, countdownText = null) }
                    }
                    is CountdownResult.ShowText -> {
                        _uiState.update { it.copy(countdownNumber = null, countdownText = countdownResult.text) }
                        delay(AppConstants.COUNTDOWN_DISPLAY_MS)
                        _uiState.update { it.copy(countdownText = null) }
                    }
                }

                if (state.showGameOverAlert) continue
                if (!gameStarted) continue

                // Game over by time
                if (checkGameOverByTime(state.game.endDate)) {
                    cancelStreams()
                    _uiState.update {
                        it.copy(
                            showGameOverAlert = true,
                            gameOverMessage = "Time's up! The Chicken survived!"
                        )
                    }
                    continue
                }

                // Radius update
                val radiusResult = processRadiusUpdate(
                    nextRadiusUpdate = state.nextRadiusUpdate,
                    currentRadius = state.radius,
                    radiusDeclinePerUpdate = state.game.radiusDeclinePerUpdate,
                    radiusIntervalUpdate = state.game.radiusIntervalUpdate,
                    gameMod = state.game.gameModEnum,
                    initialLocation = state.game.initialLocation,
                    currentCircleCenter = state.circleCenter
                )
                if (radiusResult != null) {
                    if (radiusResult.isGameOver) {
                        cancelStreams()
                        _uiState.update {
                            it.copy(
                                showGameOverAlert = true,
                                gameOverMessage = radiusResult.gameOverMessage ?: "Game over"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                radius = radiusResult.newRadius,
                                nextRadiusUpdate = radiusResult.newNextUpdate,
                                circleCenter = radiusResult.newCircleCenter ?: it.circleCenter
                            )
                        }
                    }
                }

                // Zone check
                val currentState = _uiState.value
                if (shouldCheckZone(PlayerRole.HUNTER, currentState.game.gameModEnum)) {
                    val userLoc = currentState.userLocation
                    val center = currentState.circleCenter
                    if (userLoc != null && center != null) {
                        val zoneResult = checkZoneStatus(userLoc, center, currentState.radius.toDouble())
                        if (zoneResult.isOutsideZone) {
                            val since = if (currentState.outsideZoneSince == 0L) System.currentTimeMillis() else currentState.outsideZoneSince
                            val elapsed = ((System.currentTimeMillis() - since) / 1000).toInt()
                            val remaining = maxOf(0, AppConstants.OUTSIDE_ZONE_GRACE_PERIOD_SECONDS - elapsed)
                            _uiState.update { it.copy(isOutsideZone = true, outsideZoneSince = since, outsideZoneSeconds = remaining) }
                        } else {
                            _uiState.update { it.copy(isOutsideZone = false, outsideZoneSince = 0, outsideZoneSeconds = 0) }
                        }
                    }
                }
            }
        }
    }

    private fun cancelStreams() {
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
    }

    /** Stream game config changes in real time */
    private suspend fun streamGameConfig(game: Game) {
        firestoreRepository.gameConfigFlow(gameId).collect { updatedGame ->
            if (updatedGame != null) {
                // React to game cancelled/ended by chicken or Cloud Function
                if (updatedGame.gameStatusEnum == GameStatus.DONE && !_uiState.value.showGameOverAlert) {
                    cancelStreams()
                    _uiState.update {
                        it.copy(
                            game = updatedGame,
                            showGameOverAlert = true,
                            gameOverMessage = "The game has ended!"
                        )
                    }
                    return@collect
                }

                val (lastUpdate, lastRadius) = updatedGame.findLastUpdate()
                val previousCount = _uiState.value.previousWinnersCount

                _uiState.update {
                    it.copy(
                        game = updatedGame,
                        radius = lastRadius,
                        nextRadiusUpdate = lastUpdate,
                        previousWinnersCount = updatedGame.winners.size
                    )
                }

                // Only reset circle center for stayInTheZone (fixed zone)
                if (updatedGame.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                    _uiState.update { it.copy(circleCenter = updatedGame.initialLocation) }
                }

                // Detect new winners
                val notification = detectNewWinners(
                    winners = updatedGame.winners,
                    previousCount = previousCount,
                    ownHunterId = hunterId
                )
                if (notification != null) {
                    _uiState.update { it.copy(winnerNotification = notification) }
                    delay(AppConstants.WINNER_NOTIFICATION_MS)
                    _uiState.update { it.copy(winnerNotification = null) }
                }
            }
        }
    }

    /**
     * Circle follows chicken position in followTheChicken and mutualTracking modes.
     * In stayInTheZone, no chicken stream (circle stays on initial coordinates).
     */
    private suspend fun streamChickenLocation(game: Game) {
        if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) return

        val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        firestoreRepository.chickenLocationFlow(gameId).collect { location ->
            if (location != null) {
                _uiState.update { it.copy(circleCenter = location) }
            }
        }
    }

    /**
     * Hunter always tracks own location (for zone check).
     * In mutualTracking mode, also writes to Firestore.
     */
    private suspend fun trackHunterSelfLocation(game: Game) {
        val shouldWrite = game.gameModEnum == GameMod.MUTUAL_TRACKING

        val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)

        // Send current location immediately
        locationRepository.getLastLocation()?.let { point ->
            _uiState.update { it.copy(userLocation = point) }
            if (shouldWrite) {
                firestoreRepository.setHunterLocation(gameId, hunterId, point)
            }
        }

        var lastWrite = Date()
        locationRepository.locationFlow().collect { point ->
            _uiState.update { it.copy(userLocation = point) }
            if (shouldWrite && Date().time - lastWrite.time >= AppConstants.LOCATION_THROTTLE_MS) {
                firestoreRepository.setHunterLocation(gameId, hunterId, point)
                lastWrite = Date()
            }
        }
    }

    fun onFoundButtonTapped() {
        _uiState.update { it.copy(isEnteringFoundCode = true) }
    }

    fun onEnteredCodeChanged(code: String) {
        _uiState.update { it.copy(enteredCode = code.take(AppConstants.FOUND_CODE_DIGITS)) }
    }

    fun dismissFoundCodeEntry() {
        _uiState.update { it.copy(isEnteringFoundCode = false, enteredCode = "") }
    }

    fun submitFoundCode() {
        if (_uiState.value.codeCooldownUntil > System.currentTimeMillis()) return

        val code = _uiState.value.enteredCode.trim()
        _uiState.update { it.copy(isEnteringFoundCode = false, enteredCode = "") }

        if (code != _uiState.value.game.foundCode) {
            val attempts = _uiState.value.wrongCodeAttempts + 1
            val cooldown = if (attempts >= AppConstants.CODE_MAX_WRONG_ATTEMPTS) System.currentTimeMillis() + AppConstants.CODE_COOLDOWN_MS else 0L
            _uiState.update {
                it.copy(
                    showWrongCodeAlert = true,
                    wrongCodeAttempts = if (attempts >= AppConstants.CODE_MAX_WRONG_ATTEMPTS) 0 else attempts,
                    codeCooldownUntil = cooldown
                )
            }
            return
        }

        viewModelScope.launch {
            val winner = Winner(
                hunterId = hunterId,
                hunterName = hunterName,
                timestamp = Timestamp.now()
            )
            firestoreRepository.addWinner(gameId, winner)
            _uiState.update { it.copy(shouldNavigateToVictory = true) }
        }
    }

    fun dismissWrongCodeAlert() {
        _uiState.update { it.copy(showWrongCodeAlert = false) }
    }

    fun onLeaveGameTapped() {
        _uiState.update { it.copy(showLeaveAlert = true) }
    }

    fun dismissLeaveAlert() {
        _uiState.update { it.copy(showLeaveAlert = false) }
    }

    fun confirmLeaveGame(onGoToMenu: () -> Unit) {
        _uiState.update { it.copy(showLeaveAlert = false) }
        onGoToMenu()
    }

    fun confirmGameOver(onGoToMenu: () -> Unit) {
        _uiState.update { it.copy(showGameOverAlert = false) }
        onGoToMenu()
    }

    val hunterSubtitle: String
        get() = when (_uiState.value.game.gameModEnum) {
            GameMod.FOLLOW_THE_CHICKEN -> "Catch the \uD83D\uDC14 !"
            GameMod.STAY_IN_THE_ZONE -> "Stay in the zone \uD83D\uDCCD"
            GameMod.MUTUAL_TRACKING -> "Catch the \uD83D\uDC14 (she sees you! \uD83D\uDC40)"
        }

    fun onInfoTapped() {
        _uiState.update { it.copy(showGameInfo = true) }
    }

    fun dismissGameInfo() {
        _uiState.update { it.copy(showGameInfo = false) }
    }

    fun onCodeCopied() {
        _uiState.update { it.copy(codeCopied = true) }
        viewModelScope.launch {
            delay(AppConstants.CODE_COPY_FEEDBACK_MS)
            _uiState.update { it.copy(codeCopied = false) }
        }
    }
}
