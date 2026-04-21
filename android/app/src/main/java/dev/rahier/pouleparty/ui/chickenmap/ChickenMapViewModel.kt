package dev.rahier.pouleparty.ui.chickenmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.AppConstants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dev.rahier.pouleparty.ui.gamelogic.CountdownPhase
import dev.rahier.pouleparty.ui.gamelogic.CountdownResult
import dev.rahier.pouleparty.ui.gamelogic.PlayerRole
import dev.rahier.pouleparty.ui.gamelogic.checkGameOverByTime
import dev.rahier.pouleparty.ui.gamelogic.checkZoneStatus
import dev.rahier.pouleparty.ui.gamelogic.detectNewWinners
import dev.rahier.pouleparty.ui.gamelogic.evaluateCountdown
import dev.rahier.pouleparty.ui.map.BaseMapViewModel
import dev.rahier.pouleparty.ui.gamelogic.interpolateZoneCenter
import dev.rahier.pouleparty.ui.gamelogic.processRadiusUpdate
import dev.rahier.pouleparty.ui.gamelogic.shouldCheckZone
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
import android.util.Log
import java.util.Date
import javax.inject.Inject

data class HunterAnnotation(
    val id: String,
    val coordinate: Point,
    val displayName: String
)

data class ChickenMapUiState(
    override val game: Game = Game.mock,
    val hunterAnnotations: List<HunterAnnotation> = emptyList(),
    override val nextRadiusUpdate: Date? = null,
    override val nowDate: Date = Date(),
    override val radius: Int = 1500,
    override val circleCenter: Point? = null,
    val showCancelAlert: Boolean = false,
    val showGameOverAlert: Boolean = false,
    val gameOverMessage: String = "",
    override val showGameInfo: Boolean = false,
    val codeCopied: Boolean = false,
    val showFoundCode: Boolean = false,
    val previousWinnersCount: Int = -1,
    override val winnerNotification: String? = null,
    override val hasGameStarted: Boolean = false,
    val hasHuntStarted: Boolean = false,
    override val countdownNumber: Int? = null,
    override val countdownText: String? = null,
    val userLocation: Point? = null,
    override val isOutsideZone: Boolean = false,
    override val availablePowerUps: List<PowerUp> = emptyList(),
    override val collectedPowerUps: List<PowerUp> = emptyList(),
    override val showPowerUpInventory: Boolean = false,
    override val powerUpNotification: String? = null,
    override val lastActivatedPowerUpType: PowerUpType? = null,
    val activatingPowerUpId: String? = null,
    val shouldNavigateToVictory: Boolean = false
) : dev.rahier.pouleparty.ui.map.MapUiState

@HiltViewModel
class ChickenMapViewModel @Inject constructor(
    firestoreRepository: FirestoreRepository,
    locationRepository: LocationRepository,
    analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : BaseMapViewModel(firestoreRepository, locationRepository, analyticsRepository, auth) {

    override val gameId: String = savedStateHandle["gameId"] ?: ""
    override val playerId: String = auth.currentUser?.uid ?: ""
    override val analyticsRole: String = "chicken"
    override val logTag: String = "ChickenMapVM"

    private val _uiState = MutableStateFlow(ChickenMapUiState())
    val uiState: StateFlow<ChickenMapUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChickenMapEffect>(Channel.BUFFERED)
    val effects: Flow<ChickenMapEffect> = _effects.receiveAsFlow()

    /** Single entry point for every user interaction. */
    fun onIntent(intent: ChickenMapIntent) {
        when (intent) {
            ChickenMapIntent.CancelGameTapped -> onCancelGameTapped()
            ChickenMapIntent.DismissCancelAlert -> dismissCancelAlert()
            ChickenMapIntent.ConfirmCancelGame -> confirmCancelGame()
            ChickenMapIntent.ConfirmGameOver -> confirmGameOver()
            ChickenMapIntent.InfoTapped -> onInfoTapped()
            ChickenMapIntent.DismissGameInfo -> dismissGameInfo()
            ChickenMapIntent.FoundButtonTapped -> onFoundButtonTapped()
            ChickenMapIntent.DismissFoundCode -> dismissFoundCode()
            ChickenMapIntent.CodeCopied -> onCodeCopied()
            ChickenMapIntent.PowerUpInventoryTapped -> onPowerUpInventoryTapped()
            ChickenMapIntent.DismissPowerUpInventory -> dismissPowerUpInventory()
            is ChickenMapIntent.ActivatePowerUp -> activatePowerUp(intent.powerUp)
        }
    }

    override val currentUserLocation: Point?
        get() = _uiState.value.userLocation

    override val currentAvailablePowerUps: List<PowerUp>
        get() = _uiState.value.availablePowerUps

    override fun notifyPowerUp(message: String, type: PowerUpType?) {
        showNotification(message, type)
    }

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
            val (lastUpdate, lastRadius) = game.findLastUpdate()

            val interpolatedCenter = interpolateZoneCenter(
                initialCenter = game.initialLocation,
                finalCenter = game.finalLocation,
                initialRadius = game.zone.radius,
                currentRadius = lastRadius.toDouble()
            )
            _uiState.update {
                it.copy(
                    game = game,
                    radius = lastRadius,
                    nextRadiusUpdate = lastUpdate,
                    circleCenter = interpolatedCenter
                )
            }

            if (game.gameStatusEnum == GameStatus.WAITING) {
                try {
                    firestoreRepository.updateGameStatus(gameId, GameStatus.IN_PROGRESS)
                    analyticsRepository.gameStarted(gameMode = game.gameMode)
                } catch (e: Exception) {
                    Log.e("ChickenMapVM", "Failed to update game status to inProgress", e)
                }
            }

            streamJobs += startTimer()
            streamJobs += viewModelScope.launch { trackLocation(game) }
            streamJobs += viewModelScope.launch { trackHunters(game) }
            streamJobs += viewModelScope.launch { streamGameConfig() }
            streamJobs += viewModelScope.launch { streamPowerUps() }
            streamJobs += viewModelScope.launch { sendHeartbeat(game) }
            if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                streamJobs += viewModelScope.launch { radarPingBroadcastLoop(game) }
            }
        }
    }

    private fun startTimer(): Job {
        return viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val state = _uiState.value
                val now = Date()
                val gameStarted = now.after(state.game.startDate) || now == state.game.startDate
                val huntStarted = now.after(state.game.hunterStartDate) || now == state.game.hunterStartDate
                _uiState.update { it.copy(nowDate = now, hasGameStarted = gameStarted, hasHuntStarted = huntStarted) }

                // Countdown phases (chicken perspective)
                val countdownResult = evaluateCountdown(
                    phases = listOf(
                        CountdownPhase(
                            targetDate = state.game.startDate,
                            completionText = "RUN! \uD83D\uDC14",
                            showNumericCountdown = true,
                            isEnabled = true
                        ),
                        CountdownPhase(
                            targetDate = state.game.hunterStartDate,
                            completionText = "\uD83D\uDD0D Hunters incoming!",
                            showNumericCountdown = false,
                            isEnabled = state.game.timing.headStartMinutes > 0
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
                if (!huntStarted) continue

                // Game over by time
                if (checkGameOverByTime(state.game.endDate)) {
                    // Update status BEFORE cancelling streams to avoid coroutine self-cancellation
                    try {
                        firestoreRepository.updateGameStatus(gameId, GameStatus.DONE)
                        analyticsRepository.gameEnded(reason = "time_expired", winnersCount = state.game.winners.size)
                    } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
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
                        // Update status BEFORE cancelling streams to avoid coroutine self-cancellation
                        try {
                            firestoreRepository.updateGameStatus(gameId, GameStatus.DONE)
                            analyticsRepository.gameEnded(reason = "zone_collapsed", winnersCount = state.game.winners.size)
                        } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
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
                        // Periodic power-ups are spawned by the `spawnPowerUpBatch`
                        // Cloud Task scheduled at game creation — no client-side
                        // spawn here.
                    }
                }

                // Power-up proximity check
                checkPowerUpProximity()

                // Zone check (visual warning only — no elimination)
                val currentState = _uiState.value
                if (shouldCheckZone(PlayerRole.CHICKEN, currentState.game.gameModEnum)) {
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

    /**
     * Chicken sends its position to hunters (except in stayInTheZone mode).
     * Circle follows chicken position in followTheChicken mode.
     * In stayInTheZone, tracks location for zone check only (no Firestore writes).
     */
    private suspend fun trackLocation(game: Game) {
        val delayMs = game.startDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)

        if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
            // stayInTheZone: track location for zone check only
            // When radar ping is active, force-write location so hunters can see the chicken
            locationRepository.getLastLocation()?.let { latLng ->
                _uiState.update { it.copy(userLocation = latLng) }
            }
            var lastWrite = Date()
            locationRepository.locationFlow().collect { latLng ->
                _uiState.update { it.copy(userLocation = latLng) }
                val currentGame = _uiState.value.game
                if (currentGame.isRadarPingActive
                    && Date().time - lastWrite.time >= AppConstants.LOCATION_THROTTLE_MS) {
                    var sendLatLng = latLng
                    // Jammer: add +/-200m random noise
                    if (currentGame.isJammerActive) {
                        val latNoise = (Math.random() - 0.5) * AppConstants.JAMMER_NOISE_DEGREES
                        val lonNoise = (Math.random() - 0.5) * AppConstants.JAMMER_NOISE_DEGREES
                        sendLatLng = Point.fromLngLat(
                            latLng.longitude() + lonNoise,
                            latLng.latitude() + latNoise
                        )
                    }
                    firestoreRepository.setChickenLocation(gameId, sendLatLng)
                    lastWrite = Date()
                }
            }
            return
        }

        // Radar Ping support for stayInTheZone is driven by [radarPingBroadcastLoop]
        // instead of the location flow above, because the flow's 10 m distance
        // filter means a stationary chicken would never emit a write here, even
        // during an active ping.

        // Send current location immediately on connect
        locationRepository.getLastLocation()?.let { latLng ->
            _uiState.update { it.copy(circleCenter = latLng, userLocation = latLng) }
            firestoreRepository.setChickenLocation(gameId, latLng)
        }

        var lastWrite = Date()
        locationRepository.locationFlow().collect { latLng ->
            _uiState.update { it.copy(circleCenter = latLng, userLocation = latLng) }

            // Throttle Firestore writes (skip when invisible)
            if (Date().time - lastWrite.time >= AppConstants.LOCATION_THROTTLE_MS
                && !_uiState.value.game.isChickenInvisible) {
                var sendLatLng = latLng
                // Jammer: add +/-200m random noise to position
                if (_uiState.value.game.isJammerActive) {
                    val latNoise = (Math.random() - 0.5) * AppConstants.JAMMER_NOISE_DEGREES
                    val lonNoise = (Math.random() - 0.5) * AppConstants.JAMMER_NOISE_DEGREES
                    sendLatLng = Point.fromLngLat(
                        latLng.longitude() + lonNoise,
                        latLng.latitude() + latNoise
                    )
                }
                firestoreRepository.setChickenLocation(gameId, sendLatLng)
                lastWrite = Date()
            }
        }
    }

    /**
     * Timer-driven Radar Ping broadcaster for stayInTheZone mode. Ticks every
     * throttle period and writes the last-known chicken location to Firestore
     * while a radar ping is active. Needed because the location flow only fires
     * on movement (≥10 m filter) — a stationary chicken would otherwise never
     * broadcast during a ping.
     */
    private suspend fun radarPingBroadcastLoop(game: Game) {
        val delayMs = game.startDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        while (true) {
            delay(AppConstants.LOCATION_THROTTLE_MS)
            val currentGame = _uiState.value.game
            if (!currentGame.isRadarPingActive) continue
            // Safety net: if invisibility somehow got activated (not spawned in
            // stayInTheZone today, but future-proof), it wins over radar ping.
            if (currentGame.isChickenInvisible) continue
            val latLng = locationRepository.getLastLocation() ?: continue
            var sendLatLng = latLng
            if (currentGame.isJammerActive) {
                val latNoise = (Math.random() - 0.5) * AppConstants.JAMMER_NOISE_DEGREES
                val lonNoise = (Math.random() - 0.5) * AppConstants.JAMMER_NOISE_DEGREES
                sendLatLng = Point.fromLngLat(
                    latLng.longitude() + lonNoise,
                    latLng.latitude() + latNoise
                )
            }
            firestoreRepository.setChickenLocation(gameId, sendLatLng)
        }
    }

    /**
     * When chickenCanSeeHunters, chicken can see all hunter positions.
     * Gated behind hunterStartDate (hunters aren't active until then).
     */
    private suspend fun trackHunters(game: Game) {
        if (!game.chickenCanSeeHunters) return

        val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        firestoreRepository.hunterLocationsFlow(gameId).collect { hunters ->
            val sorted = hunters.sortedBy { it.hunterId }
            val annotations = sorted.mapIndexed { index, hunter ->
                HunterAnnotation(
                    id = hunter.hunterId,
                    coordinate = Point.fromLngLat(hunter.location.longitude, hunter.location.latitude),
                    displayName = "Hunter ${index + 1}"
                )
            }
            _uiState.update { it.copy(hunterAnnotations = annotations) }
        }
    }

    /** Stream game config to detect winners in real time */
    private suspend fun streamGameConfig() {
        firestoreRepository.gameConfigFlow(gameId).collect { updatedGame ->
            if (updatedGame != null) {
                // React to game cancelled/ended by external source
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

                val previousCount = _uiState.value.previousWinnersCount
                val oldGame = _uiState.value.game
                _uiState.update {
                    it.copy(
                        game = updatedGame,
                        previousWinnersCount = updatedGame.winners.size
                    )
                }

                // Detect cross-player power-up activations
                detectCrossPlayerPowerUp(oldGame, updatedGame) { msg, type -> showNotification(msg, type) }

                // Skip winner detection on first snapshot (previousCount == -1 means uninitialized)
                if (previousCount >= 0) {
                    val notification = detectNewWinners(
                        winners = updatedGame.winners,
                        previousCount = previousCount
                    )
                    if (notification != null) {
                        viewModelScope.launch {
                            _uiState.update { it.copy(winnerNotification = notification) }
                            delay(AppConstants.WINNER_NOTIFICATION_MS)
                            _uiState.update { it.copy(winnerNotification = null) }
                        }
                    }
                }

                // End the game when all hunters have found the chicken
                // Chicken is authoritative: it sets game status to DONE
                if (!_uiState.value.shouldNavigateToVictory &&
                    updatedGame.hunterIds.isNotEmpty() &&
                    updatedGame.winners.size >= updatedGame.hunterIds.size) {
                    try {
                        firestoreRepository.updateGameStatus(gameId, GameStatus.DONE)
                        analyticsRepository.gameEnded(reason = "all_hunters_found", winnersCount = updatedGame.winners.size)
                    } catch (e: Exception) {
                        android.util.Log.e("ChickenMapVM", "Failed to set game DONE when all hunters found", e)
                    }
                    cancelStreams()
                    _uiState.update { it.copy(shouldNavigateToVictory = true) }
                    _effects.send(ChickenMapEffect.NavigateToVictory)
                    return@collect
                }
            }
        }
    }

    private fun onCancelGameTapped() {
        _uiState.update { it.copy(showCancelAlert = true) }
    }

    private fun dismissCancelAlert() {
        _uiState.update { it.copy(showCancelAlert = false) }
    }

    private fun confirmCancelGame() {
        _uiState.update { it.copy(showCancelAlert = false) }
        viewModelScope.launch {
            try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
            _effects.send(ChickenMapEffect.NavigateToMenu)
        }
    }

    private fun confirmGameOver() {
        _uiState.update { it.copy(showGameOverAlert = false) }
        viewModelScope.launch { _effects.send(ChickenMapEffect.NavigateToVictory) }
    }

    private fun onInfoTapped() {
        _uiState.update { it.copy(showGameInfo = true) }
    }

    private fun dismissGameInfo() {
        _uiState.update { it.copy(showGameInfo = false) }
    }

    private fun onFoundButtonTapped() {
        _uiState.update { it.copy(showFoundCode = true) }
    }

    private fun dismissFoundCode() {
        _uiState.update { it.copy(showFoundCode = false) }
    }

    private fun onCodeCopied() {
        handleCodeCopied { copied -> _uiState.update { it.copy(codeCopied = copied) } }
    }

    // ── Power-ups ──────────────────────────────────────

    /** Periodically write a heartbeat so hunters can detect chicken disconnect. */
    private suspend fun sendHeartbeat(game: Game) {
        val delayMs = game.startDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        while (true) {
            firestoreRepository.updateHeartbeat(gameId)
            delay(30_000)
        }
    }

    private suspend fun streamPowerUps() {
        firestoreRepository.powerUpsFlow(gameId).collect { allPowerUps ->
            val chickenPowerUps = allPowerUps.filter { !it.typeEnum.isHunterPowerUp && !it.isCollected }
            val collected = allPowerUps.filter {
                it.collectedBy == playerId && it.activatedAt == null
            }
            _uiState.update {
                it.copy(availablePowerUps = chickenPowerUps, collectedPowerUps = collected)
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
                val activeEffectField = when (powerUp.typeEnum) {
                    PowerUpType.INVISIBILITY -> "powerUps.activeEffects.invisibility"
                    PowerUpType.ZONE_FREEZE -> "powerUps.activeEffects.zoneFreeze"
                    PowerUpType.DECOY -> "powerUps.activeEffects.decoy"
                    PowerUpType.JAMMER -> "powerUps.activeEffects.jammer"
                    else -> null
                }
                firestoreRepository.activatePowerUp(gameId, powerUp.id, activeEffectField, expiresAt)
                analyticsRepository.powerUpActivated(type = powerUp.type, role = "chicken")
                _uiState.update { it.copy(showPowerUpInventory = false) }
                showNotification("Activated: ${powerUp.typeEnum.title}!", powerUp.typeEnum)
            } catch (e: Exception) {
                Log.e("ChickenMapVM", "Failed to activate power-up", e)
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

    val chickenSubtitle: String
        get() {
            if (_uiState.value.game.chickenCanSeeHunters) return "You can see them \uD83D\uDC40"
            return when (_uiState.value.game.gameModEnum) {
                GameMod.FOLLOW_THE_CHICKEN -> "Don't be seen !"
                GameMod.STAY_IN_THE_ZONE -> "Stay in the zone \uD83D\uDCCD"
            }
        }
}
