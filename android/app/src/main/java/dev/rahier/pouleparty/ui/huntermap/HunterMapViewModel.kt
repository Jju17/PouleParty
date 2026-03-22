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
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.model.Winner
import android.location.Location
import dev.rahier.pouleparty.ui.CountdownPhase
import dev.rahier.pouleparty.ui.CountdownResult
import dev.rahier.pouleparty.ui.PlayerRole
import dev.rahier.pouleparty.ui.checkGameOverByTime
import dev.rahier.pouleparty.ui.checkZoneStatus
import dev.rahier.pouleparty.ui.detectNewWinners
import dev.rahier.pouleparty.ui.evaluateCountdown
import dev.rahier.pouleparty.ui.interpolateZoneCenter
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
    val availablePowerUps: List<PowerUp> = emptyList(),
    val collectedPowerUps: List<PowerUp> = emptyList(),
    val showPowerUpInventory: Boolean = false,
    val powerUpNotification: String? = null,
    val lastActivatedPowerUpType: PowerUpType? = null,
    val previewCircle: Pair<Point, Double>? = null,
    val activatingPowerUpId: String? = null,
    val decoyLocation: Point? = null
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
    private var notificationJob: Job? = null

    private val _uiState = MutableStateFlow(HunterMapUiState())
    val uiState: StateFlow<HunterMapUiState> = _uiState.asStateFlow()

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            if (hunterId.isEmpty()) {
                Log.e(TAG, "hunterId is empty — cannot register hunter or write location")
                return@launch
            }
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
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
            streamJobs += viewModelScope.launch { streamPowerUps() }
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
                    currentCircleCenter = state.circleCenter,
                    driftSeed = state.game.driftSeed,
                    isZoneFrozen = state.game.isZoneFrozen,
                    finalLocation = state.game.finalLocation,
                    initialRadius = state.game.initialRadius
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

                // Power-up proximity check
                checkPowerUpProximity()

                // Zone check (visual warning only — no elimination)
                val currentState = _uiState.value
                if (shouldCheckZone(PlayerRole.HUNTER, currentState.game.gameModEnum)) {
                    val userLoc = currentState.userLocation
                    val center = currentState.circleCenter
                    if (userLoc != null && center != null) {
                        val zoneResult = checkZoneStatus(userLoc, center, currentState.radius.toDouble())
                        _uiState.update { it.copy(isOutsideZone = zoneResult.isOutsideZone) }
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

                // For stayInTheZone, only set initial circle if none exists yet
                // (drift center is computed by processRadiusUpdate, don't overwrite it)
                if (updatedGame.gameModEnum == GameMod.STAY_IN_THE_ZONE && _uiState.value.circleCenter == null) {
                    val interpolatedCenter = interpolateZoneCenter(
                        initialCenter = updatedGame.initialLocation,
                        finalCenter = updatedGame.finalLocation,
                        initialRadius = updatedGame.initialRadius,
                        currentRadius = lastRadius.toDouble()
                    )
                    _uiState.update { it.copy(circleCenter = interpolatedCenter) }
                }

                // Decoy: show a fake chicken marker when decoy is active
                if (updatedGame.isDecoyActive) {
                    if (_uiState.value.decoyLocation == null) {
                        val center = _uiState.value.circleCenter ?: updatedGame.initialLocation
                        val angle = Math.random() * 2 * Math.PI
                        val distance = (200 + Math.random() * 300) / 111_320.0 // 200-500m in degrees
                        val decoy = Point.fromLngLat(
                            center.longitude() + distance * Math.sin(angle) / Math.cos(Math.toRadians(center.latitude())),
                            center.latitude() + distance * Math.cos(angle)
                        )
                        _uiState.update { it.copy(decoyLocation = decoy) }
                    }
                } else {
                    if (_uiState.value.decoyLocation != null) {
                        _uiState.update { it.copy(decoyLocation = null) }
                    }
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
     * Circle follows chicken position in followTheChicken mode.
     * In stayInTheZone, chicken only writes when radarPing is active,
     * so hunter will only receive updates during pings.
     */
    private suspend fun streamChickenLocation(game: Game) {
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
     * When chickenCanSeeHunters, also writes to Firestore.
     */
    private suspend fun trackHunterSelfLocation(game: Game) {
        val shouldWrite = game.chickenCanSeeHunters

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

    // ── Power-ups ──────────────────────────────────────

    private suspend fun streamPowerUps() {
        firestoreRepository.powerUpsFlow(gameId).collect { allPowerUps ->
            val hunterPowerUps = allPowerUps.filter { it.typeEnum.isHunterPowerUp && !it.isCollected }
            val collected = allPowerUps.filter {
                it.collectedBy == hunterId && it.activatedAt == null
            }
            _uiState.update {
                it.copy(availablePowerUps = hunterPowerUps, collectedPowerUps = collected)
            }
        }
    }

    private fun showNotification(message: String, type: PowerUpType? = null) {
        notificationJob?.cancel()
        notificationJob = viewModelScope.launch {
            _uiState.update { it.copy(powerUpNotification = message, lastActivatedPowerUpType = type) }
            delay(2000)
            _uiState.update { it.copy(powerUpNotification = null) }
        }
    }

    private fun checkPowerUpProximity() {
        val state = _uiState.value
        val userLoc = state.userLocation ?: return
        val powerUps = state.availablePowerUps.toList()
        for (powerUp in powerUps) {
            val results = FloatArray(1)
            Location.distanceBetween(
                userLoc.latitude(), userLoc.longitude(),
                powerUp.location.latitude, powerUp.location.longitude,
                results
            )
            if (results[0] <= AppConstants.POWER_UP_COLLECTION_RADIUS_METERS) {
                viewModelScope.launch {
                    try {
                        firestoreRepository.collectPowerUp(gameId, powerUp.id, hunterId)
                        showNotification("Collected: ${powerUp.typeEnum.title}!", powerUp.typeEnum)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to collect power-up", e)
                        showNotification("Failed to collect power-up")
                    }
                }
            }
        }
    }

    fun activatePowerUp(powerUp: PowerUp) {
        if (_uiState.value.activatingPowerUpId != null) return
        _uiState.update { it.copy(activatingPowerUpId = powerUp.id) }
        viewModelScope.launch {
            try {
                val duration = powerUp.typeEnum.durationSeconds ?: 0
                val expiresAt = Timestamp(Date(System.currentTimeMillis() + duration * 1000))
                firestoreRepository.activatePowerUp(gameId, powerUp.id, expiresAt)

                when (powerUp.typeEnum) {
                    PowerUpType.ZONE_PREVIEW -> {
                        // Compute next zone preview (client-side only)
                        val state = _uiState.value
                        val nextRadius = state.radius - state.game.radiusDeclinePerUpdate.toInt()
                        if (nextRadius > 0) {
                            val center = state.circleCenter ?: state.game.initialLocation
                            _uiState.update {
                                it.copy(previewCircle = Pair(center, nextRadius.toDouble()))
                            }
                        }
                    }
                    PowerUpType.RADAR_PING -> {
                        firestoreRepository.updateGameActiveEffect(
                            gameId, "activeRadarPingUntil", expiresAt
                        )
                    }
                    else -> {}
                }
                _uiState.update { it.copy(showPowerUpInventory = false) }
                showNotification("Activated: ${powerUp.typeEnum.title}!", powerUp.typeEnum)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to activate power-up", e)
            } finally {
                _uiState.update { it.copy(activatingPowerUpId = null) }
            }
        }
    }

    fun onPowerUpInventoryTapped() {
        _uiState.update { it.copy(showPowerUpInventory = true) }
    }

    fun dismissPowerUpInventory() {
        _uiState.update { it.copy(showPowerUpInventory = false) }
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
        _uiState.update { it.copy(showLeaveAlert = false, previewCircle = null) }
        onGoToMenu()
    }

    fun confirmGameOver(onGoToMenu: () -> Unit) {
        _uiState.update { it.copy(showGameOverAlert = false, previewCircle = null) }
        onGoToMenu()
    }

    val hunterSubtitle: String
        get() {
            if (_uiState.value.game.chickenCanSeeHunters) return "Catch the \uD83D\uDC14 (she sees you! \uD83D\uDC40)"
            return when (_uiState.value.game.gameModEnum) {
                GameMod.FOLLOW_THE_CHICKEN -> "Catch the \uD83D\uDC14 !"
                GameMod.STAY_IN_THE_ZONE -> "Stay in the zone \uD83D\uDCCD"
            }
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
