package dev.rahier.pouleparty.ui.huntermap

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
import dev.rahier.pouleparty.ui.CountdownPhase
import dev.rahier.pouleparty.ui.CountdownResult
import dev.rahier.pouleparty.ui.PlayerRole
import dev.rahier.pouleparty.ui.checkGameOverByTime
import dev.rahier.pouleparty.ui.checkZoneStatus
import dev.rahier.pouleparty.ui.detectNewWinners
import dev.rahier.pouleparty.ui.evaluateCountdown
import dev.rahier.pouleparty.ui.BaseMapViewModel
import dev.rahier.pouleparty.ui.interpolateZoneCenter
import dev.rahier.pouleparty.ui.processRadiusUpdate
import dev.rahier.pouleparty.ui.seededRandom
import dev.rahier.pouleparty.ui.shouldCheckZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import javax.inject.Inject

data class HunterMapUiState(
    override val game: Game = Game.mock,
    override val nextRadiusUpdate: Date? = null,
    override val nowDate: Date = Date(),
    override val radius: Int = 1500,
    override val circleCenter: Point? = null,
    val showLeaveAlert: Boolean = false,
    val showGameOverAlert: Boolean = false,
    val gameOverMessage: String = "",
    val isEnteringFoundCode: Boolean = false,
    val enteredCode: String = "",
    val showWrongCodeAlert: Boolean = false,
    val previousWinnersCount: Int = -1,
    override val winnerNotification: String? = null,
    val shouldNavigateToVictory: Boolean = false,
    override val hasGameStarted: Boolean = false,
    override val countdownNumber: Int? = null,
    override val countdownText: String? = null,
    override val showGameInfo: Boolean = false,
    val codeCopied: Boolean = false,
    val wrongCodeAttempts: Int = 0,
    val codeCooldownUntil: Long = 0,
    val userLocation: Point? = null,
    override val isOutsideZone: Boolean = false,
    override val availablePowerUps: List<PowerUp> = emptyList(),
    override val collectedPowerUps: List<PowerUp> = emptyList(),
    override val showPowerUpInventory: Boolean = false,
    override val powerUpNotification: String? = null,
    override val lastActivatedPowerUpType: PowerUpType? = null,
    val previewCircle: Pair<Point, Double>? = null,
    val activatingPowerUpId: String? = null,
    val decoyLocation: Point? = null,
    val showRegistrationRequiredAlert: Boolean = false
) : dev.rahier.pouleparty.ui.MapUiState

@HiltViewModel
class HunterMapViewModel @Inject constructor(
    firestoreRepository: FirestoreRepository,
    locationRepository: LocationRepository,
    analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : BaseMapViewModel(firestoreRepository, locationRepository, analyticsRepository, auth) {

    companion object {
        private const val TAG = "HunterMapViewModel"
    }

    override val gameId: String = savedStateHandle["gameId"] ?: ""
    val hunterName: String = savedStateHandle["hunterName"] ?: "Hunter"
    override val playerId: String = auth.currentUser?.uid ?: ""
    override val analyticsRole: String = "hunter"
    override val logTag: String = TAG
    /** Public alias kept for external callers (e.g. HunterMapScreen). */
    val hunterId: String get() = playerId

    private val _uiState = MutableStateFlow(HunterMapUiState())
    val uiState: StateFlow<HunterMapUiState> = _uiState.asStateFlow()

    override val currentUserLocation: Point?
        get() = _uiState.value.userLocation

    override val currentAvailablePowerUps: List<PowerUp>
        get() = _uiState.value.availablePowerUps

    override fun notifyPowerUp(message: String, type: PowerUpType?) {
        showNotification(message, type)
    }

    private val _effects = Channel<HunterMapEffect>(Channel.BUFFERED)
    val effects: Flow<HunterMapEffect> = _effects.receiveAsFlow()

    /** Single entry point for every user interaction. */
    fun onIntent(intent: HunterMapIntent) {
        when (intent) {
            HunterMapIntent.DismissRegistrationRequiredAlert -> dismissRegistrationRequiredAlert()
            HunterMapIntent.PowerUpInventoryTapped -> onPowerUpInventoryTapped()
            HunterMapIntent.DismissPowerUpInventory -> dismissPowerUpInventory()
            HunterMapIntent.FoundButtonTapped -> onFoundButtonTapped()
            HunterMapIntent.DismissFoundCodeEntry -> dismissFoundCodeEntry()
            HunterMapIntent.SubmitFoundCode -> submitFoundCode()
            HunterMapIntent.VictoryNavigated -> onVictoryNavigated()
            HunterMapIntent.DismissWrongCodeAlert -> dismissWrongCodeAlert()
            HunterMapIntent.LeaveGameTapped -> onLeaveGameTapped()
            HunterMapIntent.DismissLeaveAlert -> dismissLeaveAlert()
            HunterMapIntent.ConfirmLeaveGame -> confirmLeaveGame()
            HunterMapIntent.ConfirmGameOver -> confirmGameOver()
            HunterMapIntent.InfoTapped -> onInfoTapped()
            HunterMapIntent.DismissGameInfo -> dismissGameInfo()
            HunterMapIntent.CodeCopied -> onCodeCopied()
            is HunterMapIntent.ActivatePowerUp -> activatePowerUp(intent.powerUp)
            is HunterMapIntent.EnteredCodeChanged -> onEnteredCodeChanged(intent.code)
        }
    }

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

            // Defensive gate: refuse entry if game requires registration and user isn't registered.
            // This catches client-side bypasses and prevents the Firestore rule from rejecting
            // the hunterIds update (which would otherwise crash the screen).
            if (game.registration.required) {
                val registration = firestoreRepository.findRegistration(gameId, hunterId)
                if (registration == null) {
                    Log.w(TAG, "Hunter $hunterId not registered for game $gameId — bouncing back")
                    _uiState.update { it.copy(showRegistrationRequiredAlert = true) }
                    return@launch
                }
            }

            val (lastUpdate, lastRadius) = game.findLastUpdate()

            _uiState.update {
                it.copy(
                    game = game,
                    radius = lastRadius,
                    nextRadiusUpdate = lastUpdate
                )
            }

            try {
                firestoreRepository.registerHunter(gameId, hunterId)
                analyticsRepository.gameJoined(gameMode = game.gameMode, gameCode = game.gameCode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register hunter $hunterId for game $gameId", e)
                _uiState.update { it.copy(showRegistrationRequiredAlert = true) }
                return@launch
            }
            streamJobs += startTimer()
            streamJobs += viewModelScope.launch { streamGameConfig(game) }
            streamJobs += viewModelScope.launch { streamChickenLocation(game) }
            streamJobs += viewModelScope.launch { trackHunterSelfLocation(game) }
            streamJobs += viewModelScope.launch { streamPowerUps() }
        }
    }

    private fun dismissRegistrationRequiredAlert() {
        _uiState.update { it.copy(showRegistrationRequiredAlert = false) }
    }

    private fun startTimer(): Job {
        return viewModelScope.launch {
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
                            isEnabled = state.game.timing.headStartMinutes > 0
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

                if (_uiState.value.showGameOverAlert) continue
                if (!gameStarted) continue

                // Game over by time
                if (checkGameOverByTime(state.game.endDate)) {
                    // Fallback: also update status from hunter side in case chicken didn't
                    try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (_: Exception) {}
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
                    radiusDeclinePerUpdate = state.game.zone.shrinkMetersPerUpdate,
                    radiusIntervalUpdate = state.game.zone.shrinkIntervalMinutes,
                    gameMod = state.game.gameModEnum,
                    initialLocation = state.game.initialLocation,
                    currentCircleCenter = state.circleCenter,
                    driftSeed = state.game.zone.driftSeed,
                    isZoneFrozen = state.game.isZoneFrozen,
                    finalLocation = state.game.finalLocation,
                    initialRadius = state.game.zone.radius
                )
                if (radiusResult != null) {
                    if (radiusResult.isGameOver) {
                        // Fallback: also update status from hunter side in case chicken didn't
                        try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (_: Exception) {}
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
                                circleCenter = radiusResult.newCircleCenter ?: it.circleCenter,
                                previewCircle = null
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
                val oldGame = _uiState.value.game

                _uiState.update {
                    it.copy(
                        game = updatedGame,
                        radius = lastRadius,
                        nextRadiusUpdate = lastUpdate,
                        previousWinnersCount = updatedGame.winners.size
                    )
                }

                // Detect cross-player power-up activations
                detectCrossPlayerPowerUp(oldGame, updatedGame) { msg, type -> showNotification(msg, type) }

                // For stayInTheZone, only set initial circle if none exists yet
                // (drift center is computed by processRadiusUpdate, don't overwrite it)
                if (updatedGame.gameModEnum == GameMod.STAY_IN_THE_ZONE && _uiState.value.circleCenter == null) {
                    val interpolatedCenter = interpolateZoneCenter(
                        initialCenter = updatedGame.initialLocation,
                        finalCenter = updatedGame.finalLocation,
                        initialRadius = updatedGame.zone.radius,
                        currentRadius = lastRadius.toDouble()
                    )
                    _uiState.update { it.copy(circleCenter = interpolatedCenter) }
                }

                // Decoy: show a fake chicken marker when decoy is active
                if (updatedGame.isDecoyActive) {
                    if (_uiState.value.decoyLocation == null) {
                        val center = _uiState.value.circleCenter ?: updatedGame.initialLocation
                        val decoyTimestamp = (updatedGame.powerUps.activeEffects.decoy?.toDate()?.time ?: 0L) / 1000 // seconds, matching iOS
                        val seed = updatedGame.zone.driftSeed.toLong() xor decoyTimestamp
                        val angle = seededRandom(seed, 0) * 2 * Math.PI
                        val distance = (200 + seededRandom(seed, 1) * 300) / 111_320.0 // 200-500m in degrees
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

                // Skip winner detection on first snapshot (previousCount == -1 means uninitialized)
                if (previousCount >= 0) {
                    val notification = detectNewWinners(
                        winners = updatedGame.winners,
                        previousCount = previousCount,
                        ownHunterId = hunterId
                    )
                    if (notification != null) {
                        viewModelScope.launch {
                            _uiState.update { it.copy(winnerNotification = notification) }
                            delay(AppConstants.WINNER_NOTIFICATION_MS)
                            _uiState.update { it.copy(winnerNotification = null) }
                        }
                    }
                }

                // Navigate to victory when all hunters have found the chicken
                // (Chicken is authoritative for setting game status to DONE)
                if (!_uiState.value.shouldNavigateToVictory &&
                    updatedGame.hunterIds.isNotEmpty() &&
                    updatedGame.winners.size >= updatedGame.hunterIds.size) {
                    cancelStreams()
                    _uiState.update { it.copy(shouldNavigateToVictory = true) }
                    return@collect
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
        showPowerUpNotification(message, type) { msg, pwrType ->
            _uiState.update { it.copy(powerUpNotification = msg, lastActivatedPowerUpType = pwrType) }
        }
    }

    private fun activatePowerUp(powerUp: PowerUp) {
        if (_uiState.value.activatingPowerUpId != null) return
        _uiState.update { it.copy(activatingPowerUpId = powerUp.id) }
        viewModelScope.launch {
            try {
                val duration = powerUp.typeEnum.durationSeconds ?: 0
                val expiresAt = Timestamp(Date(System.currentTimeMillis() + duration * 1000))
                firestoreRepository.activatePowerUp(gameId, powerUp.id, expiresAt)
                analyticsRepository.powerUpActivated(type = powerUp.type, role = "hunter")

                when (powerUp.typeEnum) {
                    PowerUpType.ZONE_PREVIEW -> {
                        // Compute next zone preview (client-side only)
                        val state = _uiState.value
                        val nextRadius = state.radius - state.game.zone.shrinkMetersPerUpdate.toInt()
                        if (nextRadius > 0) {
                            val center = state.circleCenter ?: state.game.initialLocation
                            _uiState.update {
                                it.copy(previewCircle = Pair(center, nextRadius.toDouble()))
                            }
                        }
                    }
                    PowerUpType.RADAR_PING -> {
                        firestoreRepository.updateGameActiveEffect(
                            gameId, "powerUps.activeEffects.radarPing", expiresAt
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

    private fun onPowerUpInventoryTapped() {
        _uiState.update { it.copy(showPowerUpInventory = true) }
    }

    private fun dismissPowerUpInventory() {
        _uiState.update { it.copy(showPowerUpInventory = false) }
    }

    private fun onFoundButtonTapped() {
        _uiState.update { it.copy(isEnteringFoundCode = true) }
    }

    private fun onEnteredCodeChanged(code: String) {
        _uiState.update { it.copy(enteredCode = code.take(AppConstants.FOUND_CODE_DIGITS)) }
    }

    private fun dismissFoundCodeEntry() {
        _uiState.update { it.copy(isEnteringFoundCode = false, enteredCode = "") }
    }

    private fun submitFoundCode() {
        if (_uiState.value.codeCooldownUntil > System.currentTimeMillis()) return

        val code = _uiState.value.enteredCode.trim()
        _uiState.update { it.copy(isEnteringFoundCode = false, enteredCode = "") }

        if (code != _uiState.value.game.foundCode) {
            val attempts = _uiState.value.wrongCodeAttempts + 1
            analyticsRepository.hunterWrongCode(attemptNumber = attempts)
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

        val totalAttempts = _uiState.value.wrongCodeAttempts + 1
        viewModelScope.launch {
            val winner = Winner(
                hunterId = hunterId,
                hunterName = hunterName,
                timestamp = Timestamp.now()
            )
            firestoreRepository.addWinner(gameId, winner)
            analyticsRepository.hunterFoundChicken(attempts = totalAttempts)
            _uiState.update { it.copy(shouldNavigateToVictory = true) }
            _effects.send(HunterMapEffect.NavigateToVictory)
        }
    }

    private fun onVictoryNavigated() {
        _uiState.update { it.copy(shouldNavigateToVictory = false) }
    }

    private fun dismissWrongCodeAlert() {
        _uiState.update { it.copy(showWrongCodeAlert = false) }
    }

    private fun onLeaveGameTapped() {
        _uiState.update { it.copy(showLeaveAlert = true) }
    }

    private fun dismissLeaveAlert() {
        _uiState.update { it.copy(showLeaveAlert = false) }
    }

    private fun confirmLeaveGame() {
        _uiState.update { it.copy(showLeaveAlert = false, previewCircle = null) }
        viewModelScope.launch { _effects.send(HunterMapEffect.NavigateToMenu) }
    }

    private fun confirmGameOver() {
        cancelStreams()
        _uiState.update { it.copy(showGameOverAlert = false, previewCircle = null) }
        viewModelScope.launch { _effects.send(HunterMapEffect.NavigateToVictory) }
    }

    val hunterSubtitle: String
        get() {
            if (_uiState.value.game.chickenCanSeeHunters) return "Catch the \uD83D\uDC14 (she sees you! \uD83D\uDC40)"
            return when (_uiState.value.game.gameModEnum) {
                GameMod.FOLLOW_THE_CHICKEN -> "Catch the \uD83D\uDC14 !"
                GameMod.STAY_IN_THE_ZONE -> "Stay in the zone \uD83D\uDCCD"
            }
        }

    private fun onInfoTapped() {
        _uiState.update { it.copy(showGameInfo = true) }
    }

    private fun dismissGameInfo() {
        _uiState.update { it.copy(showGameInfo = false) }
    }

    private fun onCodeCopied() {
        handleCodeCopied { copied -> _uiState.update { it.copy(codeCopied = copied) } }
    }

    // seededRandom is in GameTimerHelper.kt (shared pure logic)
}
