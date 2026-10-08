package dev.rahier.pouleparty.ui.chickenmap

import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.model.ZoneCircle
import dev.rahier.pouleparty.ui.map.MapUiState
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.common.uiText
import dev.rahier.pouleparty.ui.common.UiText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.PresenceRepository
import dev.rahier.pouleparty.data.DebugAction
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.ui.common.LoadState
import dev.rahier.pouleparty.ui.common.errorMessageRes
import dev.rahier.pouleparty.ui.common.loadGameWithSchedule
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
import dev.rahier.pouleparty.model.PlayerRole
import dev.rahier.pouleparty.ui.gamelogic.chickenBroadcastPoint
import dev.rahier.pouleparty.ui.gamelogic.checkGameOverByTime
import dev.rahier.pouleparty.ui.gamelogic.checkZoneStatus
import dev.rahier.pouleparty.ui.gamelogic.detectNewWinners
import dev.rahier.pouleparty.ui.gamelogic.evaluateCountdown
import dev.rahier.pouleparty.ui.map.BaseMapViewModel
import dev.rahier.pouleparty.ui.gamelogic.zoneRenderStateFromCircles
import dev.rahier.pouleparty.ui.gamelogic.shouldCheckZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    val displayName: UiText,
)

data class ChickenMapUiState(
    override val game: Game = Game.mock,
    val loadState: LoadState = LoadState.Loading,
    val hunterAnnotations: List<HunterAnnotation> = emptyList(),
    override val nextRadiusUpdate: Date? = null,
    override val nowDate: Date = Date(),
    override val radius: Int = 1500,
    override val circleCenter: Point? = null,
    /** PP-zone-stored: the ordered circle schedule read once from
     *  `/games/{id}/zone/schedule`. Runtime renders `circles[activeIndex]`
     *  instead of recomputing the drift. Empty = no zone to render. */
    val circles: List<ZoneCircle> = emptyList(),
    val showCancelAlert: Boolean = false,
    override val showGameInfo: Boolean = false,
    val codeCopied: Boolean = false,
    val showFoundCode: Boolean = false,
    val chickenFoundCode: String = "",
    val previousWinnersCount: Int = -1,
    val pendingSubmissionsCount: Int = 0,
    override val winnerNotification: UiText? = null,
    override val hasGameStarted: Boolean = false,
    val hasHuntStarted: Boolean = false,
    override val countdownNumber: Int? = null,
    override val countdownText: UiText? = null,
    val userLocation: Point? = null,
    override val isOutsideZone: Boolean = false,
    override val availablePowerUps: List<PowerUp> = emptyList(),
    override val collectedPowerUps: List<PowerUp> = emptyList(),
    override val showPowerUpInventory: Boolean = false,
    override val powerUpNotification: UiText? = null,
    override val lastActivatedPowerUpType: PowerUpType? = null,
    val activatingPowerUpId: String? = null,
    val shouldNavigateToVictory: Boolean = false,
    /** Flipped to true when the chicken explicitly cancels the game.
     *  Distinguishes "chicken cancelled → go home" from "natural game
     *  end → go to Victory" once `status == DONE` arrives via the
     *  gameConfig stream (both paths write the same terminal status,
     *  but the UX diverges). Mirrors iOS `isCancelling`. */
    val isCancelling: Boolean = false,
    /** True once the game is over for any reason. Drives the bottom-
     *  bar trophy CTA + greys gameplay controls. Mirrors iOS. */
    val isGameOver: Boolean = false,
    val isLaunching: Boolean = false,
    val launchError: UiText? = null,
    val showNewChickenAlert: Boolean = false,
) : MapUiState

@HiltViewModel
class ChickenMapViewModel @Inject constructor(
    gameRepository: GameRepository,
    presenceRepository: PresenceRepository,
    gameFunctions: GameFunctions,
    locationRepository: LocationRepository,
    analyticsRepository: AnalyticsRepository,
    auth: FirebaseAuth,
    private val prefs: android.content.SharedPreferences,
    savedStateHandle: SavedStateHandle
) : BaseMapViewModel(gameRepository, presenceRepository, gameFunctions, locationRepository, analyticsRepository, auth) {

    override val gameId: String = savedStateHandle["gameId"] ?: ""
    override val playerId: String = auth.currentUser?.uid ?: ""

    fun currentUserId(): String = playerId
    override val analyticsRole: String = "chicken"
    override val logTag: String = "ChickenMapVM"

    private val becameChicken: Boolean = savedStateHandle["becameChicken"] ?: false

    private val _uiState = MutableStateFlow(ChickenMapUiState(showNewChickenAlert = becameChicken))
    val uiState: StateFlow<ChickenMapUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChickenMapEffect>(Channel.BUFFERED)
    val effects: Flow<ChickenMapEffect> = _effects.receiveAsFlow()

    /** Single entry point for every user interaction. */
    fun onIntent(intent: ChickenMapIntent) {
        when (intent) {
            ChickenMapIntent.RetryLoad -> retryLoad()
            ChickenMapIntent.CancelGameTapped -> onCancelGameTapped()
            ChickenMapIntent.DismissCancelAlert -> dismissCancelAlert()
            ChickenMapIntent.ConfirmCancelGame -> confirmCancelGame()
            ChickenMapIntent.InfoTapped -> onInfoTapped()
            ChickenMapIntent.DismissGameInfo -> dismissGameInfo()
            ChickenMapIntent.FoundButtonTapped -> onFoundButtonTapped()
            ChickenMapIntent.DismissFoundCode -> dismissFoundCode()
            ChickenMapIntent.CodeCopied -> onCodeCopied()
            ChickenMapIntent.PowerUpInventoryTapped -> onPowerUpInventoryTapped()
            ChickenMapIntent.DismissPowerUpInventory -> dismissPowerUpInventory()
            is ChickenMapIntent.ActivatePowerUp -> activatePowerUp(intent.powerUp)
            ChickenMapIntent.ValidationQueueTapped -> viewModelScope.launch {
                _effects.send(ChickenMapEffect.OpenValidationQueue)
            }
            ChickenMapIntent.DismissNewChickenAlert ->
                _uiState.update { it.copy(showNewChickenAlert = false) }
            ChickenMapIntent.LaunchTapped -> onLaunchTapped()
            ChickenMapIntent.LaunchErrorDismissed -> _uiState.update { it.copy(launchError = null) }
            ChickenMapIntent.ViewLeaderboardTapped -> viewModelScope.launch {
                _effects.send(ChickenMapEffect.NavigateToVictory)
            }
            ChickenMapIntent.DebugEndNowTapped -> viewModelScope.launch {
                try { gameFunctions.debugAdvanceGame(gameId, DebugAction.END_NOW) } catch (e: Exception) { Log.w("ChickenMapVM", "[qa] debug action failed", e) }
            }
            ChickenMapIntent.DebugAdvanceStepTapped -> viewModelScope.launch {
                try { gameFunctions.debugAdvanceGame(gameId, DebugAction.ADVANCE_STEP) } catch (e: Exception) { Log.w("ChickenMapVM", "[qa] debug action failed", e) }
            }
        }
    }

    private fun onLaunchTapped() {
        val state = _uiState.value
        if (state.game.gameStatusEnum != GameStatus.READY_TO_LAUNCH) return
        if (state.isLaunching) return
        _uiState.update { it.copy(isLaunching = true, launchError = null) }
        viewModelScope.launch {
            try {
                gameFunctions.launchGame(state.game.id)
                _uiState.update { it.copy(isLaunching = false) }
            } catch (e: Exception) {
                Log.e(logTag, "launchGame failed", e)
                _uiState.update { it.copy(isLaunching = false, launchError = uiText(e.errorMessageRes())) }
            }
        }
    }

    override val currentUserLocation: Point?
        get() = _uiState.value.userLocation

    override val currentAvailablePowerUps: List<PowerUp>
        get() = _uiState.value.availablePowerUps

    override fun notifyPowerUp(message: UiText, type: PowerUpType?) {
        showNotification(message, type)
    }

    init {
        loadGame()
        viewModelScope.launch {
            try {
                val code = gameFunctions.getFoundCode(gameId)
                _uiState.update { it.copy(chickenFoundCode = code) }
            } catch (e: Exception) {
                Log.e("ChickenMapVM", "Failed to fetch foundCode for $gameId", e)
            }
        }
    }

    /** PP-zone-stored: thin wrapper over the shared selector so all three
     *  map ViewModels resolve the active circle identically. */
    private fun zoneStateFromCircles(
        game: Game,
        circles: List<ZoneCircle>,
        now: Date,
    ) = zoneRenderStateFromCircles(
        gameMode = game.gameModEnum,
        hunterStartDate = game.hunterStartDate,
        shrinkIntervalMinutes = game.zone.shrinkIntervalMinutes,
        fallbackRadius = game.zone.radius,
        circles = circles,
        freezeEnd = game.powerUps.activeEffects.zoneFreeze?.toDate(),
        freezeDurationMs = (PowerUpType.ZONE_FREEZE.durationSeconds ?: 0) * 1000L,
        now = now,
    )

    private fun retryLoad() {
        _uiState.update { it.copy(loadState = LoadState.Loading) }
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            val (game, circles) = loadGameWithSchedule(gameRepository, gameId).getOrElse { error ->
                Log.w("ChickenMapVM", "[map] game load failed", error)
                _uiState.update { it.copy(loadState = LoadState.Failed(error.errorMessageRes())) }
                return@launch
            }
            val z = zoneStateFromCircles(game, circles, Date())
            _uiState.update {
                it.copy(
                    game = game,
                    circles = circles,
                    loadState = LoadState.Ready,
                    radius = z.radius,
                    nextRadiusUpdate = z.nextUpdate,
                    circleCenter = z.center ?: it.circleCenter,
                )
            }

            if (game.gameStatusEnum == GameStatus.WAITING) {
                analyticsRepository.gameStarted(gameMode = game.gameMode)
            }

            streamJobs += startTimer()
            streamJobs += viewModelScope.launch { trackLocation(game) }
            streamJobs += viewModelScope.launch { trackHunters(game) }
            streamJobs += viewModelScope.launch { streamGameConfig() }
            streamJobs += viewModelScope.launch { streamPowerUps() }
            streamJobs += viewModelScope.launch { streamPendingSubmissions() }
            streamJobs += viewModelScope.launch { sendHeartbeat(game) }
            streamJobs += viewModelScope.launch { broadcastChickenLocation(game) }
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

                // Countdown phases (chicken perspective). In manual-start
                // mode, neither phase fires until the chicken/GM taps
                // LAUNCH and the server stamps `actualStart`. `startDate`
                // is just "when status flips to readyToLaunch" in that
                // mode \u2014 the real start is `effectiveStartDate`.
                val hasLaunched = !state.game.manualStartEnabled ||
                    state.game.timing.actualStart != null
                val countdownResult = evaluateCountdown(
                    phases = listOf(
                        CountdownPhase(
                            targetDate = state.game.effectiveStartDate,
                            completionText = uiText(R.string.countdown_chicken_run),
                            showNumericCountdown = true,
                            isEnabled = hasLaunched
                        ),
                        CountdownPhase(
                            targetDate = state.game.hunterStartDate,
                            completionText = uiText(R.string.countdown_chicken_hunters_coming),
                            showNumericCountdown = false,
                            isEnabled = hasLaunched && state.game.timing.headStartMinutes > 0
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

                if (_uiState.value.isGameOver) continue
                if (!huntStarted) continue

                // Game over by time
                if (checkGameOverByTime(state.game.endDate)) {
                    // Update status BEFORE cancelling streams to avoid coroutine self-cancellation
                    try {
                        gameRepository.updateGameStatus(gameId, GameStatus.DONE)
                        analyticsRepository.gameEnded(reason = "time_expired", winnersCount = state.game.winners.size)
                    } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
                    cancelStreams()
                    _uiState.update { it.copy(isGameOver = true) }
                    continue
                }

                // PP-zone-stored: select the active circle from the stored
                // schedule. No on-device geometry recompute, no Int-truncation
                // drift, every device reads the same list. The zone shrinks to
                // the 50m final circle and stays there; the game ends by time
                // (or all-found / cancel), not by "zone collapsed".
                // followTheChicken keeps the live chicken GPS center (set in
                // trackLocation); we only update the radius for that mode.
                val z = zoneStateFromCircles(state.game, state.circles, now)
                _uiState.update {
                    it.copy(
                        radius = z.radius,
                        nextRadiusUpdate = z.nextUpdate,
                        circleCenter = z.center ?: it.circleCenter,
                    )
                }
                // Periodic power-ups are spawned by the `spawnPowerUpBatch`
                // Cloud Task scheduled at game creation, no client-side spawn.

                // Power-up proximity check
                checkPowerUpProximity()

                // Zone check (visual warning only, no elimination)
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

    /** Keeps the chicken's own position in state; the zone follows it only in followTheChicken. */
    private suspend fun trackLocation(game: Game) {
        val delayMs = game.startDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        val zoneFollowsChicken = game.gameModEnum != GameMod.STAY_IN_THE_ZONE
        val onFix: (Point) -> Unit = { latLng ->
            _uiState.update {
                it.copy(
                    circleCenter = if (zoneFollowsChicken) latLng else it.circleCenter,
                    userLocation = latLng,
                )
            }
        }
        locationRepository.getLastLocation()?.let(onFix)
        locationRepository.locationFlow().collect(onFix)
    }

    /**
     * The only position writer: one write per throttle window, moving or not,
     * so a radar ping always finds a recent point.
     */
    private suspend fun broadcastChickenLocation(game: Game) {
        val delayMs = game.startDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        while (currentCoroutineContext().isActive && !_uiState.value.isGameOver) {
            val state = _uiState.value
            state.userLocation?.let { location ->
                presenceRepository.setChickenLocation(gameId, chickenBroadcastPoint(location, state.game), state.game.isChickenInvisible)
            }
            delay(AppConstants.LOCATION_THROTTLE_MS)
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
        presenceRepository.hunterLocationsFlow(gameId).collect { hunters ->
            val sorted = hunters.sortedBy { it.hunterId }
            val annotations = sorted.mapIndexed { index, hunter ->
                HunterAnnotation(
                    id = hunter.hunterId,
                    coordinate = Point.fromLngLat(hunter.location.longitude, hunter.location.latitude),
                    displayName = uiText(R.string.hunter_number, index + 1)
                )
            }
            _uiState.update { it.copy(hunterAnnotations = annotations) }
        }
    }

    /** Stream game config to detect winners in real time */
    private suspend fun streamGameConfig() {
        gameRepository.gameConfigFlow(gameId).collect { updatedGame ->
            if (updatedGame != null) {
                if (playerId.isNotEmpty()
                    && !updatedGame.isChicken(playerId)
                    && updatedGame.role(playerId) != null) {
                    val savedNickname = prefs
                        .getString(AppConstants.PREF_USER_NICKNAME, "").orEmpty().trim()
                    val teamName = savedNickname.ifEmpty { AppConstants.DEFAULT_TEAM_NAME }
                    cancelStreams()
                    viewModelScope.launch {
                        _effects.send(ChickenMapEffect.NavigateToHunterMap(gameId, teamName))
                    }
                    return@collect
                }

                // Natural game end: server-confirmed status flipped to
                // DONE and the chicken didn't cancel it themselves
                // (`confirmCancelGame` sets `isCancelling` and navigates
                // home directly via NavigateToMenu). The chicken stays
                // on the map with `isGameOver = true` so the "Game
                // ended" banner appears; tapping the banner fires
                // `ViewLeaderboardTapped` → NavigateToVictory →
                // Victory / leaderboard screen.
                if (updatedGame.gameStatusEnum == GameStatus.DONE
                    && !_uiState.value.isGameOver
                    && !_uiState.value.isCancelling) {
                    cancelStreams()
                    _uiState.update {
                        it.copy(game = updatedGame, isGameOver = true)
                    }
                    return@collect
                }

                val previousCount = _uiState.value.previousWinnersCount
                val oldGame = _uiState.value.game
                _uiState.update {
                    // QA debug games drive zone shrinks server-side (the
                    // `advanceStep` callable rewinds the start anchor), so
                    // re-derive the radius / next-update / circle from the
                    // fresh timing on every config tick. Real games keep the
                    // incremental timer path (zone-freeze aware) untouched.
                    if (updatedGame.isDebugGame) {
                        // QA debug: the `advanceStep` callable rewinds the start
                        // anchor so more shrinks appear "elapsed", re-derive the
                        // active circle from the stored schedule on each tick.
                        val z = zoneStateFromCircles(updatedGame, it.circles, Date())
                        it.copy(
                            game = updatedGame,
                            previousWinnersCount = updatedGame.winners.size,
                            radius = z.radius,
                            nextRadiusUpdate = z.nextUpdate,
                            circleCenter = z.center ?: it.circleCenter,
                        )
                    } else {
                        it.copy(
                            game = updatedGame,
                            previousWinnersCount = updatedGame.winners.size
                        )
                    }
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

                // End the game when all hunters have found the chicken.
                // Chicken is authoritative for the Firestore `status = DONE`
                // write. After the write lands, the gameConfig stream
                // re-fires above and routes the chicken to Victory via
                // the natural-end branch.
                if (!_uiState.value.isGameOver &&
                    updatedGame.hunterIds.isNotEmpty() &&
                    updatedGame.winners.size >= updatedGame.hunterIds.size) {
                    try {
                        gameRepository.updateGameStatus(gameId, GameStatus.DONE)
                        analyticsRepository.gameEnded(reason = "all_hunters_found", winnersCount = updatedGame.winners.size)
                    } catch (e: Exception) {
                        android.util.Log.e("ChickenMapVM", "Failed to set game DONE when all hunters found", e)
                    }
                }
            }
        }
    }

    private fun onCancelGameTapped() {
        if (_uiState.value.isGameOver) return
        _uiState.update { it.copy(showCancelAlert = true) }
    }

    private fun dismissCancelAlert() {
        _uiState.update { it.copy(showCancelAlert = false) }
    }

    private fun confirmCancelGame() {
        _uiState.update { it.copy(showCancelAlert = false, isCancelling = true) }
        viewModelScope.launch {
            try { gameRepository.updateGameStatus(gameId, GameStatus.DONE) } catch (e: Exception) { Log.e("ChickenMapVM", "Failed to update game status", e) }
            _effects.send(ChickenMapEffect.NavigateToMenu)
        }
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

    /** A failed heartbeat must never end the loop: the next tick retries. */
    private suspend fun sendHeartbeat(game: Game) {
        val delayMs = game.startDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)
        while (currentCoroutineContext().isActive && !_uiState.value.isGameOver) {
            try {
                presenceRepository.updateHeartbeat(gameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChickenMapVM", "[presence] heartbeat failed", e)
            }
            delay(AppConstants.HEARTBEAT_INTERVAL_MS)
        }
    }

    private suspend fun streamPowerUps() {
        gameRepository.powerUpsFlow(gameId).collect { allPowerUps ->
            val chickenPowerUps = allPowerUps.filter { !it.typeEnum.isHunterPowerUp && !it.isCollected }
            val collected = allPowerUps.filter {
                it.collectedBy == playerId && it.activatedAt == null
            }
            _uiState.update {
                it.copy(availablePowerUps = chickenPowerUps, collectedPowerUps = collected)
            }
        }
    }

    private suspend fun streamPendingSubmissions() {
        gameRepository.pendingSubmissionsFlow(gameId).collect { subs ->
            _uiState.update { it.copy(pendingSubmissionsCount = subs.size) }
        }
    }

    private fun showNotification(message: UiText, type: PowerUpType? = null) {
        showPowerUpNotification(message, type) { msg, pwrType ->
            _uiState.update { it.copy(powerUpNotification = msg, lastActivatedPowerUpType = pwrType) }
        }
    }

    private fun activatePowerUp(powerUp: PowerUp) {
        if (_uiState.value.activatingPowerUpId != null) return
        if (_uiState.value.game.isActive(powerUp.typeEnum)) {
            showNotification(uiText(R.string.notif_powerup_already_active, uiText(powerUp.typeEnum.titleRes)), powerUp.typeEnum)
            return
        }
        _uiState.update { it.copy(activatingPowerUpId = powerUp.id) }
        viewModelScope.launch {
            try {
                gameFunctions.activatePowerUp(gameId, powerUp.id)
                analyticsRepository.powerUpActivated(type = powerUp.type, role = "chicken")
                _uiState.update { it.copy(showPowerUpInventory = false) }
                showNotification(uiText(R.string.notif_powerup_activated, uiText(powerUp.typeEnum.titleRes)), powerUp.typeEnum)
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
}
