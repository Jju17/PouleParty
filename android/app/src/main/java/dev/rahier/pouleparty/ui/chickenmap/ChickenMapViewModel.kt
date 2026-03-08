package dev.rahier.pouleparty.ui.chickenmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.AppConstants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import android.location.Location
import dev.rahier.pouleparty.ui.CountdownPhase
import dev.rahier.pouleparty.ui.CountdownResult
import dev.rahier.pouleparty.ui.PlayerRole
import dev.rahier.pouleparty.ui.checkGameOverByTime
import dev.rahier.pouleparty.ui.checkZoneStatus
import dev.rahier.pouleparty.ui.detectNewWinners
import dev.rahier.pouleparty.ui.evaluateCountdown
import dev.rahier.pouleparty.ui.RoadSnapService
import dev.rahier.pouleparty.ui.generatePowerUps
import dev.rahier.pouleparty.ui.snapPowerUpsToRoads
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
import android.util.Log
import java.util.Date
import javax.inject.Inject
import javax.inject.Named

data class HunterAnnotation(
    val id: String,
    val coordinate: Point,
    val displayName: String
)

data class ChickenMapUiState(
    val game: Game = Game.mock,
    val hunterAnnotations: List<HunterAnnotation> = emptyList(),
    val nextRadiusUpdate: Date? = null,
    val nowDate: Date = Date(),
    val radius: Int = 1500,
    val circleCenter: Point? = null,
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
    val countdownText: String? = null,
    val userLocation: Point? = null,
    val isOutsideZone: Boolean = false,
    val availablePowerUps: List<PowerUp> = emptyList(),
    val collectedPowerUps: List<PowerUp> = emptyList(),
    val showPowerUpInventory: Boolean = false,
    val powerUpNotification: String? = null,
    val lastSpawnBatchIndex: Int = 0
)

@HiltViewModel
class ChickenMapViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val auth: FirebaseAuth,
    @Named("mapboxAccessToken") private val mapboxAccessToken: String,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val userId: String = auth.currentUser?.uid ?: ""
    /** Tracked separately from viewModelScope to allow early cancellation on game over. */
    private val streamJobs = mutableListOf<Job>()

    private val _uiState = MutableStateFlow(ChickenMapUiState())
    val uiState: StateFlow<ChickenMapUiState> = _uiState.asStateFlow()

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
            val (lastUpdate, lastRadius) = game.findLastUpdate()

            _uiState.update {
                it.copy(
                    game = game,
                    radius = lastRadius,
                    nextRadiusUpdate = lastUpdate,
                    circleCenter = game.initialLocation
                )
            }

            if (game.gameStatusEnum == GameStatus.WAITING) {
                try { firestoreRepository.updateGameStatus(gameId, GameStatus.IN_PROGRESS) } catch (_: Exception) {}
            }

            startTimer()
            streamJobs += viewModelScope.launch { trackLocation(game) }
            streamJobs += viewModelScope.launch { trackHunters(game) }
            streamJobs += viewModelScope.launch { streamGameConfig() }
            streamJobs += viewModelScope.launch { streamPowerUps() }
            viewModelScope.launch { spawnInitialPowerUps(game) }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
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
                            isEnabled = state.game.chickenHeadStartMinutes > 0
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
                if (!huntStarted) continue

                // Game over by time
                if (checkGameOverByTime(state.game.endDate)) {
                    cancelStreams()
                    _uiState.update {
                        it.copy(
                            showGameOverAlert = true,
                            gameOverMessage = "Time's up! The Chicken survived!"
                        )
                    }
                    try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
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
                    isZoneFrozen = state.game.isZoneFrozen
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
                        try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
                    } else {
                        _uiState.update {
                            it.copy(
                                radius = radiusResult.newRadius,
                                nextRadiusUpdate = radiusResult.newNextUpdate,
                                circleCenter = radiusResult.newCircleCenter ?: it.circleCenter
                            )
                        }
                        // Spawn new power-ups on zone shrink
                        val nextBatch = _uiState.value.lastSpawnBatchIndex + 1
                        spawnPeriodicPowerUps(nextBatch)
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

    private fun cancelStreams() {
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
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
            locationRepository.getLastLocation()?.let { latLng ->
                _uiState.update { it.copy(userLocation = latLng) }
            }
            locationRepository.locationFlow().collect { latLng ->
                _uiState.update { it.copy(userLocation = latLng) }
            }
            return
        }

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
                firestoreRepository.setChickenLocation(gameId, latLng)
                lastWrite = Date()
            }
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
                val previousCount = _uiState.value.previousWinnersCount
                _uiState.update {
                    it.copy(
                        game = updatedGame,
                        previousWinnersCount = updatedGame.winners.size
                    )
                }

                val notification = detectNewWinners(
                    winners = updatedGame.winners,
                    previousCount = previousCount
                )
                if (notification != null) {
                    _uiState.update { it.copy(winnerNotification = notification) }
                    delay(AppConstants.WINNER_NOTIFICATION_MS)
                    _uiState.update { it.copy(winnerNotification = null) }
                }
            }
        }
    }

    fun onCancelGameTapped() {
        _uiState.update { it.copy(showCancelAlert = true) }
    }

    fun dismissCancelAlert() {
        _uiState.update { it.copy(showCancelAlert = false) }
    }

    fun confirmCancelGame(onGoToMenu: () -> Unit) {
        _uiState.update { it.copy(showCancelAlert = false) }
        viewModelScope.launch {
            try { firestoreRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
            onGoToMenu()
        }
    }

    fun confirmGameOver(onGoToMenu: () -> Unit) {
        _uiState.update { it.copy(showGameOverAlert = false) }
        onGoToMenu()
    }

    fun onInfoTapped() {
        _uiState.update { it.copy(showGameInfo = true) }
    }

    fun dismissGameInfo() {
        _uiState.update { it.copy(showGameInfo = false) }
    }

    fun onFoundButtonTapped() {
        _uiState.update { it.copy(showFoundCode = true) }
    }

    fun dismissFoundCode() {
        _uiState.update { it.copy(showFoundCode = false) }
    }

    fun onCodeCopied() {
        _uiState.update { it.copy(codeCopied = true) }
        viewModelScope.launch {
            delay(AppConstants.CODE_COPY_FEEDBACK_MS)
            _uiState.update { it.copy(codeCopied = false) }
        }
    }

    // ── Power-ups ──────────────────────────────────────

    private suspend fun spawnInitialPowerUps(game: Game) {
        if (!game.powerUpsEnabled) return
        val center = game.initialLocation
        var powerUps = generatePowerUps(
            center = center,
            radius = game.initialRadius,
            count = AppConstants.POWER_UP_INITIAL_BATCH_SIZE,
            driftSeed = game.driftSeed,
            batchIndex = 0,
            enabledTypes = game.enabledPowerUpTypes
        )
        powerUps = snapPowerUpsToRoads(powerUps, mapboxAccessToken)
        try {
            firestoreRepository.spawnPowerUps(gameId, powerUps)
        } catch (e: Exception) {
            Log.e("ChickenMapVM", "Failed to spawn initial power-ups", e)
        }
    }

    fun spawnPeriodicPowerUps(batchIndex: Int) {
        val state = _uiState.value
        if (!state.game.powerUpsEnabled) return
        val center = state.circleCenter ?: state.game.initialLocation
        var powerUps = generatePowerUps(
            center = center,
            radius = state.radius.toDouble(),
            count = AppConstants.POWER_UP_PERIODIC_BATCH_SIZE,
            driftSeed = state.game.driftSeed,
            batchIndex = batchIndex,
            enabledTypes = state.game.enabledPowerUpTypes
        )
        viewModelScope.launch {
            try {
                powerUps = snapPowerUpsToRoads(powerUps, mapboxAccessToken)
                firestoreRepository.spawnPowerUps(gameId, powerUps)
                _uiState.update { it.copy(lastSpawnBatchIndex = batchIndex) }
            } catch (e: Exception) {
                Log.e("ChickenMapVM", "Failed to spawn periodic power-ups", e)
            }
        }
    }

    private suspend fun streamPowerUps() {
        firestoreRepository.powerUpsFlow(gameId).collect { allPowerUps ->
            val chickenPowerUps = allPowerUps.filter { !it.typeEnum.isHunterPowerUp && !it.isCollected }
            val collected = allPowerUps.filter {
                it.collectedBy == userId && it.activatedAt == null
            }
            _uiState.update {
                it.copy(availablePowerUps = chickenPowerUps, collectedPowerUps = collected)
            }
        }
    }

    private fun checkPowerUpProximity() {
        val state = _uiState.value
        val userLoc = state.userLocation ?: return
        for (powerUp in state.availablePowerUps) {
            val results = FloatArray(1)
            Location.distanceBetween(
                userLoc.latitude(), userLoc.longitude(),
                powerUp.location.latitude, powerUp.location.longitude,
                results
            )
            if (results[0] <= AppConstants.POWER_UP_COLLECTION_RADIUS_METERS) {
                viewModelScope.launch {
                    try {
                        firestoreRepository.collectPowerUp(gameId, powerUp.id, userId)
                        _uiState.update {
                            it.copy(powerUpNotification = "Collected: ${powerUp.typeEnum.title}!")
                        }
                        delay(2000)
                        _uiState.update { it.copy(powerUpNotification = null) }
                    } catch (e: Exception) {
                        Log.e("ChickenMapVM", "Failed to collect power-up", e)
                    }
                }
                break // collect one at a time
            }
        }
    }

    fun activatePowerUp(powerUp: PowerUp) {
        viewModelScope.launch {
            try {
                val duration = powerUp.typeEnum.durationSeconds ?: 0
                val expiresAt = Timestamp(Date(System.currentTimeMillis() + duration * 1000))
                firestoreRepository.activatePowerUp(gameId, powerUp.id, expiresAt)

                when (powerUp.typeEnum) {
                    PowerUpType.INVISIBILITY -> {
                        firestoreRepository.updateGameActiveEffect(
                            gameId, "activeInvisibilityUntil", expiresAt
                        )
                    }
                    PowerUpType.ZONE_FREEZE -> {
                        firestoreRepository.updateGameActiveEffect(
                            gameId, "activeZoneFreezeUntil", expiresAt
                        )
                    }
                    else -> {}
                }
                _uiState.update {
                    it.copy(
                        showPowerUpInventory = false,
                        powerUpNotification = "Activated: ${powerUp.typeEnum.title}!"
                    )
                }
                delay(2000)
                _uiState.update { it.copy(powerUpNotification = null) }
            } catch (e: Exception) {
                Log.e("ChickenMapVM", "Failed to activate power-up", e)
            }
        }
    }

    fun onPowerUpInventoryTapped() {
        _uiState.update { it.copy(showPowerUpInventory = true) }
    }

    fun dismissPowerUpInventory() {
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
