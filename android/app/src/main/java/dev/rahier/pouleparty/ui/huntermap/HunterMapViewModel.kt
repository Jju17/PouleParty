package dev.rahier.pouleparty.ui.huntermap

import dev.rahier.pouleparty.ui.gamelogic.requestLaunch
import dev.rahier.pouleparty.ui.gamelogic.zoneRenderState
import dev.rahier.pouleparty.config.RemoteConfigProvider
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.model.ZoneCircle
import dev.rahier.pouleparty.ui.map.MapUiState
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.common.uiText
import dev.rahier.pouleparty.ui.common.UiText
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.PresenceRepository
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.ui.common.LoadState
import dev.rahier.pouleparty.ui.common.loadGameWithSchedule
import dev.rahier.pouleparty.ui.common.errorMessageRes
import dev.rahier.pouleparty.data.SubmitFoundCodeReason
import dev.rahier.pouleparty.data.SubmitFoundCodeResult
import dev.rahier.pouleparty.data.LocationRepository
import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.ui.gamelogic.CountdownPhase
import dev.rahier.pouleparty.ui.gamelogic.CountdownResult
import dev.rahier.pouleparty.model.PlayerRole
import dev.rahier.pouleparty.ui.gamelogic.checkGameOverByTime
import dev.rahier.pouleparty.ui.gamelogic.checkZoneStatus
import dev.rahier.pouleparty.ui.gamelogic.detectNewWinners
import dev.rahier.pouleparty.ui.gamelogic.evaluateCountdown
import dev.rahier.pouleparty.ui.gamelogic.evaluateOutOfZonePenalty
import dev.rahier.pouleparty.ui.map.BaseMapViewModel
import kotlinx.coroutines.flow.catch
import dev.rahier.pouleparty.ui.gamelogic.selectActiveCircle
import dev.rahier.pouleparty.ui.gamelogic.zoneRenderStateFromCircles
import dev.rahier.pouleparty.ui.gamelogic.seededRandom
import dev.rahier.pouleparty.ui.gamelogic.shouldCheckZone
import kotlin.coroutines.coroutineContext
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
    override val game: Game = Game(),
    val loadState: LoadState = LoadState.Loading,
    override val nextRadiusUpdate: Date? = null,
    override val nowDate: Date = Date(),
    override val radius: Int = 1500,
    override val circleCenter: Point? = null,
    /** PP-zone-stored: ordered circle schedule read once from
     *  `/games/{id}/zone/schedule`; runtime renders `circles[activeIndex]`. */
    val circles: List<ZoneCircle> = emptyList(),
    val showLeaveAlert: Boolean = false,
    val isLeaving: Boolean = false,
    @param:androidx.annotation.StringRes val leaveErrorRes: Int? = null,
    val isEnteringFoundCode: Boolean = false,
    val enteredCode: String = "",
    val showWrongCodeAlert: Boolean = false,
    val previousWinnersCount: Int = -1,
    override val winnerNotification: UiText? = null,
    val shouldNavigateToVictory: Boolean = false,
    val isGameOver: Boolean = false,
    override val hasGameStarted: Boolean = false,
    override val countdownNumber: Int? = null,
    override val countdownText: UiText? = null,
    override val showGameInfo: Boolean = false,
    val codeCopied: Boolean = false,
    val wrongCodeAttempts: Int = 0,
    val codeCooldownUntil: Long = 0,
    val userLocation: Point? = null,
    override val isOutsideZone: Boolean = false,
    override val availablePowerUps: List<PowerUp> = emptyList(),
    override val collectedPowerUps: List<PowerUp> = emptyList(),
    override val showPowerUpInventory: Boolean = false,
    override val powerUpNotification: UiText? = null,
    override val lastActivatedPowerUpType: PowerUpType? = null,
    val previewCircle: Pair<Point, Double>? = null,
    val activatingPowerUpId: String? = null,
    val decoyLocation: Point? = null,
    // Latest known Chicken position broadcasted via `chickenLocationFlow`.
    // Tracked in every mode (not just followTheChicken) so Radar Ping has a
    // fresh point to reveal the instant it's activated. HunterMapScreen
    // gates marker visibility on `game.isRadarPingActive`, without that
    // gate this would be a free locator.
    val chickenLocation: Point? = null,
    val hasChallenges: Boolean = false,
    val winnerRegistrationFailed: Boolean = false,
    val pendingFoundCode: String? = null,
    // Raised while a `submitFoundCode` CF call is in flight so a fast
    // double-tap on the submit button can't enqueue two CF calls. The CF
    // itself is also idempotent (returns AlreadyWinner on re-submission),
    // but this UX gate avoids the round-trip.
    val isSubmittingWinner: Boolean = false,
    val lastPenaltyAt: Long? = null,
) : MapUiState

@HiltViewModel
class HunterMapViewModel @Inject constructor(
    gameRepository: GameRepository,
    presenceRepository: PresenceRepository,
    gameFunctions: GameFunctions,
    locationRepository: LocationRepository,
    analyticsRepository: AnalyticsRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle,
    private val remoteConfig: RemoteConfigProvider,
) : BaseMapViewModel(gameRepository, presenceRepository, gameFunctions, locationRepository, analyticsRepository, auth) {

    companion object {
        private const val TAG = "HunterMapViewModel"
    }

    override val gameId: String = savedStateHandle["gameId"] ?: ""
    val hunterName: String = savedStateHandle["hunterName"] ?: AppConstants.DEFAULT_TEAM_NAME
    override val playerId: String = auth.currentUser?.uid ?: ""
    override val analyticsRole: String = "hunter"
    override val logTag: String = TAG
    /** Public alias kept for external callers (e.g. HunterMapScreen). */
    val hunterId: String get() = playerId

    fun currentUserId(): String = playerId

    private val _uiState = MutableStateFlow(HunterMapUiState())
    val uiState: StateFlow<HunterMapUiState> = _uiState.asStateFlow()

    override val currentUserLocation: Point?
        get() = _uiState.value.userLocation

    override val currentAvailablePowerUps: List<PowerUp>
        get() = _uiState.value.availablePowerUps

    override fun notifyPowerUp(message: UiText, type: PowerUpType?) {
        showNotification(message, type)
    }

    private val _effects = Channel<HunterMapEffect>(Channel.BUFFERED)
    val effects: Flow<HunterMapEffect> = _effects.receiveAsFlow()

    /** Single entry point for every user interaction. */
    fun onIntent(intent: HunterMapIntent) {
        when (intent) {
            HunterMapIntent.RetryLoad -> retryLoad()
            HunterMapIntent.ChallengesSheetDismissed -> Unit
            HunterMapIntent.AppResumed -> onAppResumed()
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
            HunterMapIntent.DismissLeaveError -> _uiState.update { it.copy(leaveErrorRes = null) }
            HunterMapIntent.InfoTapped -> onInfoTapped()
            HunterMapIntent.DismissGameInfo -> dismissGameInfo()
            HunterMapIntent.CodeCopied -> onCodeCopied()
            is HunterMapIntent.ActivatePowerUp -> activatePowerUp(intent.powerUp)
            is HunterMapIntent.EnteredCodeChanged -> onEnteredCodeChanged(intent.code)
            HunterMapIntent.RetryWinnerRegistration -> retryWinnerRegistration()
            HunterMapIntent.DismissWinnerRegistrationError -> dismissWinnerRegistrationError()
            HunterMapIntent.ViewLeaderboardTapped -> viewModelScope.launch {
                _effects.send(HunterMapEffect.NavigateToVictory)
            }
        }
    }

    init {
        loadGame()
        observeChallengesAvailability()
    }

    private fun observeChallengesAvailability() {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            gameRepository.challengesStream(gameId)
                .catch { e ->
                    Log.w(TAG, "challengesStream error: leaving hasChallenges as-is", e)
                }
                .collect { list ->
                    _uiState.update { it.copy(hasChallenges = list.isNotEmpty()) }
                }
        }
    }


    private fun retryLoad() {
        _uiState.update { it.copy(loadState = LoadState.Loading) }
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            if (hunterId.isEmpty()) {
                Log.e(TAG, "[map] no signed-in hunter")
                _uiState.update { it.copy(loadState = LoadState.Failed(R.string.api_error_unauthenticated)) }
                return@launch
            }
            val (game, circles) = loadGameWithSchedule(gameRepository, gameId).getOrElse { error ->
                Log.w(TAG, "[map] game load failed", error)
                _uiState.update { it.copy(loadState = LoadState.Failed(error.errorMessageRes())) }
                return@launch
            }
            val z = game.zoneRenderState(circles, Date())

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

            try {
                gameFunctions.joinGame(gameId, hunterName.trim())
                analyticsRepository.gameJoined(gameMode = game.gameMode, gameCode = game.gameCode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register hunter $hunterId for game $gameId", e)
                return@launch
            }
            streamJobs += startTimer()
            streamJobs += viewModelScope.launch { streamGameConfig(game) }
            streamJobs += viewModelScope.launch { streamChickenLocation(game) }
            streamJobs += viewModelScope.launch { trackHunterSelfLocation(game) }
            streamJobs += viewModelScope.launch { streamPowerUps() }
        }
    }

    private fun startTimer(): Job {
        return viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_uiState.value.isGameOver) {
                    if (_uiState.value.isOutsideZone) {
                        _uiState.update { it.copy(isOutsideZone = false) }
                    }
                    break
                }
                val state = _uiState.value
                val now = Date()
                val gameStarted = now.after(state.game.hunterStartDate) || now == state.game.hunterStartDate
                _uiState.update { it.copy(nowDate = now, hasGameStarted = gameStarted) }

                // Countdown phases (hunter perspective). Same manual-start
                // gate as the chicken: nothing fires before LAUNCH.
                val hasLaunched = !state.game.manualStartEnabled ||
                    state.game.timing.actualStart != null
                val countdownResult = evaluateCountdown(
                    phases = listOf(
                        CountdownPhase(
                            targetDate = state.game.effectiveStartDate,
                            completionText = uiText(R.string.countdown_hunter_chicken_hiding),
                            showNumericCountdown = true,
                            isEnabled = hasLaunched && state.game.timing.headStartMinutes > 0
                        ),
                        CountdownPhase(
                            targetDate = state.game.hunterStartDate,
                            completionText = uiText(R.string.countdown_hunter_go),
                            showNumericCountdown = true,
                            isEnabled = hasLaunched
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

                if (!gameStarted) continue

                // Game over by time
                if (checkGameOverByTime(state.game.endDate)) {
                    // Fallback: also update status from hunter side in case chicken didn't
                    try {
                        gameRepository.updateGameStatus(gameId, GameStatus.DONE)
                    } catch (e: Exception) {
                        Log.w(TAG, "[lifecycle] end-of-game status write failed", e)
                    }
                    cancelStreams()
                    _uiState.update { it.copy(isGameOver = true) }
                    continue
                }

                // PP-zone-stored: resolve the active circle from the stored
                // schedule (no on-device recompute). Zone shrinks to the 50m
                // final circle and stays; game ends by time, not "collapsed".
                // followTheChicken keeps the live chicken GPS center.
                val z = state.game.zoneRenderState(state.circles, now)
                _uiState.update {
                    it.copy(
                        radius = z.radius,
                        nextRadiusUpdate = z.nextUpdate,
                        circleCenter = z.center ?: it.circleCenter,
                        previewCircle = null,
                    )
                }

                // Power-up proximity check
                checkPowerUpProximity()

                // Zone check (visual warning only, no elimination)
                val currentState = _uiState.value
                if (shouldCheckZone(PlayerRole.HUNTER, currentState.game.gameModEnum)) {
                    val userLoc = currentState.userLocation
                    val center = currentState.circleCenter
                    if (userLoc != null && center != null) {
                        val zoneResult = checkZoneStatus(userLoc, center, currentState.radius.toDouble())
                        _uiState.update { it.copy(isOutsideZone = zoneResult.isOutsideZone) }
                    }
                }

                val zoneState = _uiState.value
                val decision = evaluateOutOfZonePenalty(
                    isOutsideZone = zoneState.isOutsideZone,
                    isGameOver = zoneState.isGameOver,
                    hunterId = hunterId,
                    lastPenaltyAt = zoneState.lastPenaltyAt,
                    nowMs = now.time,
                )
                if (decision.resetLastPenaltyAt) {
                    _uiState.update { it.copy(lastPenaltyAt = null) }
                } else if (decision.newLastPenaltyAt != null) {
                    _uiState.update { it.copy(lastPenaltyAt = decision.newLastPenaltyAt) }
                }
                if (decision.shouldFirePenalty) {
                    val gid = zoneState.game.id
                    viewModelScope.launch {
                        try {
                            gameFunctions.applyOutOfZonePenalty(gid)
                        } catch (e: Exception) {
                            Log.e(TAG, "Out-of-zone penalty write failed", e)
                        }
                    }
                }
            }
        }
    }

    /** Stream game config changes in real time */
    private suspend fun streamGameConfig(game: Game) {
        gameRepository.gameConfigFlow(gameId).collect { updatedGame ->
            if (updatedGame != null) {
                if (hunterId.isNotEmpty() && updatedGame.isChicken(hunterId)) {
                    cancelStreams()
                    viewModelScope.launch {
                        _effects.send(HunterMapEffect.NavigateToChickenMap(gameId))
                    }
                    return@collect
                }

                // React to game cancelled/ended by chicken or Cloud Function.
                // The hunter stays on the map with `isGameOver = true` so
                // the "Game ended" banner appears; tapping the banner
                // fires `ViewLeaderboardTapped` → NavigateToVictory →
                // Victory / leaderboard screen.
                if (updatedGame.gameStatusEnum == GameStatus.DONE && !_uiState.value.isGameOver) {
                    cancelStreams()
                    _uiState.update {
                        it.copy(game = updatedGame, isGameOver = true)
                    }
                    return@collect
                }

                val previousCount = _uiState.value.previousWinnersCount
                val oldGame = _uiState.value.game

                // PP-zone-stored: re-resolve the active circle from the stored
                // schedule on every config tick (covers QA debug anchor-rewind
                // too). Geometry is read, never recomputed on-device.
                val z = updatedGame.zoneRenderState(_uiState.value.circles, Date())
                _uiState.update {
                    it.copy(
                        game = updatedGame,
                        radius = z.radius,
                        nextRadiusUpdate = z.nextUpdate,
                        circleCenter = z.center ?: it.circleCenter,
                        previousWinnersCount = updatedGame.winners.size
                    )
                }

                // Detect cross-player power-up activations
                detectCrossPlayerPowerUp(oldGame, updatedGame) { msg, type -> showNotification(msg, type) }

                // Decoy: show a fake chicken marker when decoy is active
                if (updatedGame.isDecoyActive) {
                    if (_uiState.value.decoyLocation == null) {
                        val center = _uiState.value.circleCenter ?: updatedGame.initialLocation
                        val decoyTimestamp = (updatedGame.powerUps.activeEffects.decoy?.toDate()?.time ?: 0L) / 1000 // seconds, matching iOS
                        val seed = updatedGame.zone.driftSeed xor decoyTimestamp
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

                if (!_uiState.value.isGameOver &&
                    updatedGame.hunterIds.isNotEmpty() &&
                    updatedGame.winners.size >= updatedGame.hunterIds.size) {
                    cancelStreams()
                    _uiState.update { it.copy(isGameOver = true) }
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
        presenceRepository.chickenLocationFlow(gameId).collect { chickenLoc ->
            if (chickenLoc == null || chickenLoc.invisible) {
                _uiState.update { it.copy(chickenLocation = null) }
                return@collect
            }
            val point = Point.fromLngLat(
                chickenLoc.location.longitude,
                chickenLoc.location.latitude,
            )
            // Always cache the latest Chicken position so Radar Ping has a
            // fresh point to reveal, the UI gates rendering on
            // `game.isRadarPingActive`, so this is not a free locator.
            // Only update the zone centre (`circleCenter`) in
            // followTheChicken: in stayInTheZone the centre is the
            // deterministic drifted centre set by streamGameConfig +
            // processRadiusUpdate, and a stray chicken broadcast (radar
            // ping write, stale chickenLocations/latest replayed on
            // listener connect) pulling it onto the Chicken would make
            // the zone check fire against the Chicken's position and
            // flag a hunter standing inside the visible circle as
            // "outside". Mirrors the gate iOS HunterMap applies on
            // `.newLocationFetched`.
            val followsChicken = _uiState.value.game.gameModEnum != GameMod.STAY_IN_THE_ZONE
            _uiState.update {
                it.copy(
                    chickenLocation = point,
                    circleCenter = if (followsChicken) point else it.circleCenter,
                )
            }
        }
    }

    private suspend fun trackHunterSelfLocation(game: Game) {
        val shouldWrite = game.chickenCanSeeHunters || game.gameMasterIds.isNotEmpty()

        val delayMs = game.hunterStartDate.time - System.currentTimeMillis()
        if (delayMs > 0) delay(delayMs)

        // Seed state immediately with the cached fix so the rest of the
        // screen has a userLocation to work with (zone check, power-up
        // proximity). Fire the first Firestore write in the same beat so
        // the chicken's map doesn't wait up to 5 s for the first tick.
        locationRepository.getLastLocation()?.let { point ->
            _uiState.update { it.copy(userLocation = point) }
            if (shouldWrite) {
                presenceRepository.setHunterLocation(gameId, hunterId, point)
            }
        }

        if (shouldWrite) {
            streamJobs += viewModelScope.launch { periodicHunterLocationWriter() }
        }

        locationRepository.locationFlow().collect { point ->
            _uiState.update { it.copy(userLocation = point) }
        }
    }

    private suspend fun periodicHunterLocationWriter() {
        var hasWritten = false
        while (coroutineContext.isActive) {
            delay(if (hasWritten) AppConstants.LOCATION_THROTTLE_MS else 100L)
            val point = _uiState.value.userLocation ?: continue
            try {
                presenceRepository.setHunterLocation(gameId, hunterId, point)
                hasWritten = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send periodic hunter location", e)
            }
        }
    }

    /**
     * Force a single hunter-location refresh when the app returns to
     * the foreground. Android can suspend the writer coroutine while
     * the screen is off or backgrounded, which means the chicken's
     * map shows a stale marker until the first tick after resume.
     * Called from [HunterMapScreen]'s `LifecycleEventEffect(ON_RESUME)`.
     */
    private fun onAppResumed() {
        val state = _uiState.value
        if (!state.game.chickenCanSeeHunters && state.game.gameMasterIds.isEmpty()) return
        if (hunterId.isEmpty()) return
        if (!state.hasGameStarted) return
        viewModelScope.launch {
            // `getLastLocation()` suspends, lives inside the coroutine, not
            // at the synchronous early-return prelude above. Prefer the
            // freshest in-state fix if we have one; otherwise ask the repo.
            val point = state.userLocation ?: locationRepository.getLastLocation() ?: return@launch
            try {
                presenceRepository.setHunterLocation(gameId, hunterId, point)
                Log.i(TAG, "Hunter location refreshed on app resume")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh hunter location on resume", e)
            }
        }
    }

    // ── Power-ups ──────────────────────────────────────

    private suspend fun streamPowerUps() {
        gameRepository.powerUpsFlow(gameId).collect { allPowerUps ->
            val hunterPowerUps = allPowerUps.filter { it.typeEnum.isHunterPowerUp && !it.isCollected }
            val collected = allPowerUps.filter {
                it.collectedBy == hunterId && it.activatedAt == null
            }
            _uiState.update {
                it.copy(availablePowerUps = hunterPowerUps, collectedPowerUps = collected)
            }
        }
    }

    private fun showNotification(message: UiText, type: PowerUpType? = null) {
        showPowerUpNotification(message, type) { msg, pwrType ->
            _uiState.update { it.copy(powerUpNotification = msg, lastActivatedPowerUpType = pwrType) }
        }
    }

    private fun activatePowerUp(powerUp: PowerUp) {
        if (_uiState.value.activatingPowerUpId != null) return
        // Guard against double activation of the same timed effect.
        // `activatePowerUp` writes `powerUps.activeEffects.<field>` =
        // now + duration, overwriting any existing timestamp. A second
        // activation while the first is still running shifts the effect
        // window mid-flight, which desyncs findLastUpdate between
        // Chicken + Hunter. Same guard landed on iOS. Belt-and-braces
        // in addition to the inventory UI button being disabled on
        // `game.isActive(type)`.
        if (_uiState.value.game.isActive(powerUp.typeEnum)) {
            showNotification(uiText(R.string.notif_powerup_already_active, uiText(powerUp.typeEnum.titleRes)), powerUp.typeEnum)
            return
        }
        _uiState.update { it.copy(activatingPowerUpId = powerUp.id) }
        viewModelScope.launch {
            try {
                gameFunctions.activatePowerUp(gameId, powerUp.id)
                analyticsRepository.powerUpActivated(type = powerUp.type, role = "hunter")

                if (powerUp.typeEnum == PowerUpType.ZONE_PREVIEW) {
                    // PP-zone-stored: the NEXT zone boundary is just the next
                    // entry in the stored schedule, no client recompute. In
                    // followTheChicken the next circle recentres on the
                    // Chicken's live GPS, so we keep the current center and
                    // only preview the next radius.
                    val state = _uiState.value
                    val active = selectActiveCircle(
                        hunterStartDate = state.game.hunterStartDate,
                        shrinkIntervalMinutes = state.game.zone.shrinkIntervalMinutes,
                        circleCount = state.circles.size,
                        freezeEnd = state.game.powerUps.activeEffects.zoneFreeze?.toDate(),
                        freezeDurationMs = (PowerUpType.ZONE_FREEZE.durationSeconds ?: 0) * 1000L,
                    )
                    val nextCircle = state.circles.getOrNull(active.circleIndex + 1)
                    if (nextCircle != null) {
                        val previewCenter = if (state.game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                            nextCircle.center
                        } else {
                            state.circleCenter ?: state.game.initialLocation
                        }
                        _uiState.update {
                            it.copy(previewCircle = Pair(previewCenter, nextCircle.radiusMeters))
                        }
                    }
                }
                _uiState.update { it.copy(showPowerUpInventory = false) }
                showNotification(uiText(R.string.notif_powerup_activated, uiText(powerUp.typeEnum.titleRes)), powerUp.typeEnum)
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
        // Lock against double-tap: if a winner submission is already in
        // flight, ignore further taps until it resolves.
        if (_uiState.value.isSubmittingWinner) return

        val code = _uiState.value.enteredCode.trim()
        _uiState.update { it.copy(isEnteringFoundCode = false, enteredCode = "") }

        val totalAttempts = _uiState.value.wrongCodeAttempts + 1
        recordFoundCodeSubmission(code, totalAttempts)
    }

    private fun handleWrongCodeRejected(serverLockedUntilMs: Long? = null) {
        val attempts = _uiState.value.wrongCodeAttempts + 1
        analyticsRepository.hunterWrongCode(attemptNumber = attempts)
        val localCooldown = if (attempts >= remoteConfig.codeMaxWrongAttempts)
            System.currentTimeMillis() + remoteConfig.codeCooldownMs
        else 0L
        val cooldown = maxOf(localCooldown, serverLockedUntilMs ?: 0L)
        _uiState.update {
            it.copy(
                showWrongCodeAlert = true,
                wrongCodeAttempts = if (attempts >= remoteConfig.codeMaxWrongAttempts) 0 else attempts,
                codeCooldownUntil = cooldown,
                pendingFoundCode = null,
                winnerRegistrationFailed = false,
                isSubmittingWinner = false,
            )
        }
    }

    private fun recordFoundCodeSubmission(code: String, totalAttempts: Int) {
        _uiState.update {
            it.copy(
                pendingFoundCode = code,
                winnerRegistrationFailed = false,
                isSubmittingWinner = true,
            )
        }
        viewModelScope.launch {
            try {
                val result = gameFunctions.submitFoundCode(gameId, code, hunterName)
                val accepted = when (result) {
                    SubmitFoundCodeResult.Success -> true
                    is SubmitFoundCodeResult.Failure ->
                        result.reason == SubmitFoundCodeReason.AlreadyWinner
                }
                if (accepted) {
                    analyticsRepository.hunterFoundChicken(attempts = totalAttempts)
                    _uiState.update {
                        it.copy(
                            pendingFoundCode = null,
                            shouldNavigateToVictory = true,
                            isSubmittingWinner = false,
                        )
                    }
                    _effects.send(HunterMapEffect.NavigateToVictory)
                } else {
                    val failure = result as SubmitFoundCodeResult.Failure
                    Log.w(TAG, "submitFoundCode rejected: ${failure.reason}")
                    if (failure.reason == SubmitFoundCodeReason.InvalidCode || failure.reason == SubmitFoundCodeReason.Cooldown) {
                        handleWrongCodeRejected(failure.lockedUntilMs)
                    } else {
                        _uiState.update {
                            it.copy(
                                winnerRegistrationFailed = true,
                                isSubmittingWinner = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submitFoundCode failed, will surface retry prompt", e)
                _uiState.update {
                    it.copy(
                        winnerRegistrationFailed = true,
                        isSubmittingWinner = false,
                    )
                }
            }
        }
    }

    private fun retryWinnerRegistration() {
        if (_uiState.value.isSubmittingWinner) return
        val code = _uiState.value.pendingFoundCode ?: return
        val totalAttempts = _uiState.value.wrongCodeAttempts + 1
        recordFoundCodeSubmission(code, totalAttempts)
    }

    private fun dismissWinnerRegistrationError() {
        // Dismiss clears the error overlay but keeps `pendingFoundCode` so
        // the hunter can re-trigger the retry from the found-code flow if
        // needed.
        _uiState.update { it.copy(winnerRegistrationFailed = false) }
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
        if (_uiState.value.isLeaving) return
        _uiState.update { it.copy(showLeaveAlert = false, leaveErrorRes = null, isLeaving = true) }
        viewModelScope.launch {
            val needsServerLeave = _uiState.value.game.gameStatusEnum != GameStatus.DONE
            val failure = if (needsServerLeave) runCatching { gameFunctions.leaveGame(gameId) }.exceptionOrNull() else null
            if (failure != null) {
                Log.w(TAG, "[leave] leaveGame failed", failure)
                _uiState.update { it.copy(isLeaving = false, leaveErrorRes = failure.errorMessageRes()) }
                return@launch
            }
            _uiState.update { it.copy(isLeaving = false, previewCircle = null) }
            _effects.send(HunterMapEffect.NavigateToMenu)
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
