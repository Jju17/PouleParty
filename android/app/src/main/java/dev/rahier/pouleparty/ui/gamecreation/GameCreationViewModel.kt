package dev.rahier.pouleparty.ui.gamecreation

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.config.RemoteConfigProvider
import dev.rahier.pouleparty.data.AnalyticsRepository
import java.util.Calendar
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.GameRepository
import dev.rahier.pouleparty.data.GameFunctions
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GamePowerUps
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.model.calculateNormalModeSettings
import dev.rahier.pouleparty.ui.common.errorMessageRes
import dev.rahier.pouleparty.ui.gamelogic.availablePowerUpTypes
import dev.rahier.pouleparty.util.calendarAt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

private const val TAG = "GameCreationVM"

data class GameCreationUiState(
    val game: Game = Game.mock,
    val currentStepIndex: Int = 0,
    val isParticipating: Boolean = true,
    val gameDurationMinutes: Double = 90.0,
    val showPowerUpSelection: Boolean = false,
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    @param:androidx.annotation.StringRes val createErrorRes: Int? = null,
    /** Set when the game exists but its referee code could not be saved. */
    val gameMasterCodeFailedGameId: String? = null,
    val codeCopied: Boolean = false,
    val goingForward: Boolean = true,
    val isAdminCreation: Boolean = false,
    val isGameMasterEnabled: Boolean = true,
    val gameMasterPassword: String = "",
) {
    val steps: List<GameCreationStep>
        get() {
            val base = mutableListOf(
                GameCreationStep.PARTICIPATION,
            )
            if (!isParticipating) {
                base.add(GameCreationStep.CHICKEN_SELECTION)
            }
            base.addAll(listOf(
                GameCreationStep.MAX_PLAYERS,
                GameCreationStep.START_TIME,
                GameCreationStep.DURATION,
                GameCreationStep.HEAD_START,
                GameCreationStep.GAME_MODE,
                GameCreationStep.START_ZONE_SETUP,
            ))
            if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                base.add(GameCreationStep.FINAL_ZONE_SETUP)
            }
            base.add(GameCreationStep.ZONES_RECAP)
            base.add(GameCreationStep.GAME_MASTER_PASSWORD)
            base.add(GameCreationStep.POWER_UPS)
            base.add(GameCreationStep.CHICKEN_SEES_HUNTERS)
            base.add(GameCreationStep.RECAP)
            return base
        }

    val maxPlayersRange: IntRange
        get() = if (isAdminCreation) 2..500 else 2..5

    val currentStep: GameCreationStep
        get() = steps.getOrElse(currentStepIndex) { GameCreationStep.PARTICIPATION }

    val progress: Float
        get() = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size

    val canGoBack: Boolean
        get() = currentStepIndex > 0

    val isStartZoneConfigured: Boolean
        get() {
            val loc = game.initialLocation
            val isDefault = kotlin.math.abs(loc.latitude() - AppConstants.DEFAULT_LATITUDE) < 0.001
                    && kotlin.math.abs(loc.longitude() - AppConstants.DEFAULT_LONGITUDE) < 0.001
            return !isDefault
        }

    val isFinalZoneConfigured: Boolean
        get() {
            val finalLoc = game.finalLocation ?: return false
            val start = game.initialLocation
            return dev.rahier.pouleparty.model.distanceMeters(start, finalLoc) >= 100.0
        }

    /** Combined gate kept for the recap step and callers that only ask
     *  "is the zone ready overall?". */
    val isZoneConfigured: Boolean
        get() {
            if (!isStartZoneConfigured) return false
            if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                return isFinalZoneConfigured
            }
            return true
        }

    val minimumStartDate: Date
        get() = Date(System.currentTimeMillis() + 60_000L)
}

@HiltViewModel
class GameCreationViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameFunctions: GameFunctions,
    private val locationRepository: LocationRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val auth: FirebaseAuth,
    private val remoteConfig: RemoteConfigProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val isAdminCreation: Boolean = savedStateHandle["isAdminCreation"] ?: false
    private val isDebugGame: Boolean = savedStateHandle["isDebugGame"] ?: false

    private val _uiState = MutableStateFlow(
        GameCreationUiState(
            isAdminCreation = isAdminCreation,
            game = Game(
                id = gameId,
                name = "",
                maxPlayers = 5,
                zone = Zone(
                    radius = remoteConfig.defaultInitialRadius,
                    shrinkIntervalMinutes = 5.0,
                    shrinkMetersPerUpdate = 100.0,
                    driftSeed = (1..999_999).random().toLong()
                ),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                // foundCode is generated SERVER-SIDE in onGameCreated and stored
                // only in /private/security (never on the public doc).
                creatorId = auth.currentUser?.uid ?: "",
                roles = (auth.currentUser?.uid ?: "").let { uid -> mapOf(uid to "chicken") },
                isAdminCreation = isAdminCreation,
                isDebugGame = isDebugGame
            )
        )
    )
    val uiState: StateFlow<GameCreationUiState> = _uiState.asStateFlow()

    private val _effects = Channel<GameCreationEffect>(Channel.BUFFERED)
    val effects: Flow<GameCreationEffect> = _effects.receiveAsFlow()

    /** Single entry point for every user interaction. */
    fun onIntent(intent: GameCreationIntent) {
        when (intent) {
            GameCreationIntent.Next -> next()
            GameCreationIntent.RetryGameMasterCode -> retryGameMasterCode()
            GameCreationIntent.ContinueWithoutGameMaster -> continueWithoutGameMaster()
            GameCreationIntent.Back -> back()
            GameCreationIntent.StartTimeTapped -> onStartTimeTapped()
            GameCreationIntent.DismissDatePicker -> dismissDatePicker()
            GameCreationIntent.DismissTimePicker -> dismissTimePicker()
            GameCreationIntent.PowerUpSelectionTapped -> onPowerUpSelectionTapped()
            GameCreationIntent.DismissPowerUpSelection -> dismissPowerUpSelection()
            GameCreationIntent.CodeCopied -> onCodeCopied()
            GameCreationIntent.DismissAlert -> dismissAlert()
            GameCreationIntent.StartGameTapped -> startGame()
            is GameCreationIntent.ParticipatingChanged -> setParticipating(intent.isParticipating)
            is GameCreationIntent.GameModeChanged -> updateGameMod(intent.mode)
            is GameCreationIntent.StartDateChanged -> updateStartDateOnly(intent.year, intent.month, intent.day)
            is GameCreationIntent.StartTimeChanged -> updateStartTime(intent.hour, intent.minute)
            is GameCreationIntent.DurationChanged -> updateDuration(intent.minutes)
            is GameCreationIntent.HeadStartChanged -> updateHeadStart(intent.minutes)
            is GameCreationIntent.InitialRadiusChanged -> updateInitialRadius(intent.radius)
            is GameCreationIntent.MaxPlayersChanged -> updateMaxPlayers(intent.value)
            is GameCreationIntent.PowerUpsToggled -> togglePowerUps(intent.enabled)
            is GameCreationIntent.PowerUpTypeToggled -> togglePowerUpType(intent.type)
            is GameCreationIntent.ChickenCanSeeHuntersToggled -> toggleChickenCanSeeHunters(intent.value)
            is GameCreationIntent.ManualStartToggled -> toggleManualStart(intent.enabled)
            is GameCreationIntent.LocationSelected -> onLocationSelected(intent.point)
            is GameCreationIntent.FinalLocationSelected -> onFinalLocationSelected(intent.point)
            is GameCreationIntent.GameMasterEnabledChanged -> _uiState.update { it.copy(isGameMasterEnabled = intent.enabled) }
            is GameCreationIntent.GameMasterPasswordChanged -> _uiState.update {
                it.copy(gameMasterPassword = intent.password.filter { ch -> ch.isDigit() }.take(4))
            }
            GameCreationIntent.ZonesRecapEntered -> onZonesRecapEntered()
            GameCreationIntent.ShuffleDriftSeed -> onShuffleDriftSeed()
        }
    }

    private fun onZonesRecapEntered() {
        _uiState.update { state ->
            val game = state.game
            // Defensive: if the user skipped both duration and
            // startDate edits, the Game model's stale defaults could
            // leave `endDate < startDate` and the preview shrink
            // schedule comes out empty. Re-sync here too.
            val syncedEnd = Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
            val gameWithEnd = game.withEndDate(syncedEnd)
            val radiusHint = if (gameWithEnd.gameModEnum == GameMod.FOLLOW_THE_CHICKEN) gameWithEnd.zone.radius else null
            val radius = dev.rahier.pouleparty.model.computeZoneRadius(
                start = gameWithEnd.startPinPoint,
                finalCenter = gameWithEnd.finalLocation,
                gameMode = gameWithEnd.gameModEnum,
                radiusHint = radiusHint,
            )
            val effectiveDuration = maxOf(state.gameDurationMinutes - gameWithEnd.timing.headStartMinutes, 1.0)
            val (interval, decline) = dev.rahier.pouleparty.model.calculateNormalModeSettings(radius, effectiveDuration)
            val newSeed: Int = if (gameWithEnd.zone.driftSeed == 0L) dev.rahier.pouleparty.model.generateDriftSeed() else gameWithEnd.zone.driftSeed.toInt()
            val finalLoc = gameWithEnd.finalLocation
            val newCenter = if (gameWithEnd.gameModEnum == GameMod.STAY_IN_THE_ZONE && finalLoc != null) {
                dev.rahier.pouleparty.model.pickInitialZoneCenter(
                    startPin = gameWithEnd.startPinPoint,
                    finalCenter = finalLoc,
                    radius = radius,
                    seed = newSeed,
                )
            } else {
                gameWithEnd.startPinPoint
            }
            state.copy(
                game = gameWithEnd.copy(
                    zone = gameWithEnd.zone.copy(
                        center = GeoPoint(newCenter.latitude(), newCenter.longitude()),
                        radius = radius,
                        driftSeed = newSeed.toLong(),
                        shrinkIntervalMinutes = interval,
                        shrinkMetersPerUpdate = decline,
                    )
                )
            )
        }
    }

    private fun onShuffleDriftSeed() {
        _uiState.update { state ->
            val game = state.game
            val newSeed = dev.rahier.pouleparty.model.generateDriftSeed()
            val finalLoc = game.finalLocation
            val newCenter = if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE && finalLoc != null) {
                dev.rahier.pouleparty.model.pickInitialZoneCenter(
                    startPin = game.startPinPoint,
                    finalCenter = finalLoc,
                    radius = game.zone.radius,
                    seed = newSeed,
                )
            } else {
                game.startPinPoint
            }
            state.copy(
                game = game.copy(
                    zone = game.zone.copy(
                        center = GeoPoint(newCenter.latitude(), newCenter.longitude()),
                        driftSeed = newSeed.toLong(),
                    )
                )
            )
        }
    }

    init {
        resolveInitialLocation()
    }

    private fun resolveInitialLocation() {
        if (locationRepository.hasFineLocationPermission()) {
            viewModelScope.launch {
                val location = locationRepository.getLastLocation() ?: return@launch
                _uiState.update { it.copy(game = it.game.withInitialLocation(location)) }
            }
        }
    }

    private fun next() {
        val state = _uiState.value
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
            _uiState.update { it.copy(currentStepIndex = nextIndex, goingForward = true) }
        }
        clampStartDateToMinimum()
    }

    private fun back() {
        val state = _uiState.value
        if (state.currentStepIndex > 0) {
            _uiState.update { it.copy(currentStepIndex = state.currentStepIndex - 1, goingForward = false) }
        }
        clampStartDateToMinimum()
    }

    private fun setParticipating(participating: Boolean) {
        _uiState.update { it.copy(isParticipating = participating) }
    }

    private fun updateGameMod(mod: GameMod) {
        _uiState.update { state ->
            // Switching to Follow the Chicken: the final zone is dynamically the
            // chicken's live position, so clear any manually-placed final zone.
            val updatedGame = if (mod == GameMod.FOLLOW_THE_CHICKEN) {
                state.game.copy(gameMode = mod.firestoreValue, zone = state.game.zone.copy(finalCenter = null))
            } else {
                state.game.copy(gameMode = mod.firestoreValue)
            }
            state.copy(game = updatedGame)
        }
    }

    private fun onStartTimeTapped() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    private fun dismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
    }

    private fun dismissTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    /**
     * Apply a new date (year/month/day) to the start date, keeping the existing
     * hour/minute. Then advances to the time picker so the user can pick the time.
     */
    private fun updateStartDateOnly(year: Int, month: Int, day: Int) {
        _uiState.update { state ->
            val cal = Calendar.getInstance().apply {
                time = state.game.startDate
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.SECOND, 0)
            }
            val endDate = Date(cal.timeInMillis + (state.gameDurationMinutes * 60 * 1000).toLong())
            state.copy(
                game = state.game.withStartDate(cal.time).withEndDate(endDate),
                showDatePicker = false,
                showTimePicker = true
            )
        }
    }

    /**
     * Apply a new time (hour/minute) to the start date, keeping the existing
     * year/month/day. If the resulting datetime is in the past (less than 2 minutes
     * from now), clamp it forward.
     */
    private fun updateStartTime(hour: Int, minute: Int) {
        _uiState.update { state ->
            val cal = calendarAt(state.game.startDate, hour, minute)
            val minDate = state.minimumStartDate
            if (cal.time.before(minDate)) {
                cal.time = minDate
            }
            val endDate = Date(cal.timeInMillis + (state.gameDurationMinutes * 60 * 1000).toLong())
            state.copy(
                game = state.game.withStartDate(cal.time).withEndDate(endDate),
                showTimePicker = false,
            )
        }
    }

    private fun updateDuration(minutes: Double) {
        _uiState.update { state ->
            val endDate = Date(state.game.startDate.time + (minutes * 60 * 1000).toLong())
            state.copy(
                gameDurationMinutes = minutes,
                game = state.game.withEndDate(endDate),
            )
        }
        recalculateNormalMode()
    }

    private fun updateHeadStart(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(timing = it.game.timing.copy(headStartMinutes = value))) }
        recalculateNormalMode()
    }

    private fun updateInitialRadius(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(zone = it.game.zone.copy(radius = value))) }
        recalculateNormalMode()
    }

    private fun updateMaxPlayers(value: Int) {
        _uiState.update { state ->
            val range = state.maxPlayersRange
            val clamped = value.coerceIn(range.first, range.last)
            state.copy(game = state.game.copy(maxPlayers = clamped))
        }
    }

    private fun togglePowerUps(enabled: Boolean) {
        _uiState.update { it.copy(game = it.game.copy(powerUps = it.game.powerUps.copy(enabled = enabled))) }
    }

    private fun togglePowerUpType(type: PowerUpType) {
        _uiState.update { state ->
            val current = state.game.powerUps.enabledTypes
            val availableRaw = availablePowerUpTypes(state.game.gameModEnum)
                .map { it.firestoreValue }
                .toSet()
            val newList = if (current.contains(type.firestoreValue)) {
                val availableEnabledCount = current.count { it in availableRaw }
                val isAvailable = type.firestoreValue in availableRaw
                if (!isAvailable || availableEnabledCount > 1) current - type.firestoreValue else current
            } else {
                current + type.firestoreValue
            }
            state.copy(game = state.game.copy(powerUps = state.game.powerUps.copy(enabledTypes = newList)))
        }
    }

    private fun toggleChickenCanSeeHunters(value: Boolean) {
        _uiState.update { it.copy(game = it.game.withChickenCanSeeHunters(value)) }
    }

    private fun toggleManualStart(enabled: Boolean) {
        _uiState.update { it.copy(game = it.game.copy(manualStartEnabled = enabled)) }
    }

    private fun onLocationSelected(point: Point) {
        _uiState.update { it.copy(game = it.game.withStartPin(point)) }
    }

    private fun onFinalLocationSelected(point: Point?) {
        _uiState.update { it.copy(game = it.game.withFinalLocation(point)) }
    }

    private fun onPowerUpSelectionTapped() {
        _uiState.update { it.copy(showPowerUpSelection = true) }
    }

    private fun dismissPowerUpSelection() {
        _uiState.update { it.copy(showPowerUpSelection = false) }
    }

    private fun onCodeCopied() {
        _uiState.update { it.copy(codeCopied = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _uiState.update { it.copy(codeCopied = false) }
        }
    }

    private fun dismissAlert() {
        _uiState.update { it.copy(createErrorRes = null) }
    }

    private fun retryGameMasterCode() {
        val gameId = _uiState.value.gameMasterCodeFailedGameId ?: return
        _uiState.update { it.copy(gameMasterCodeFailedGameId = null) }
        viewModelScope.launch { saveGameMasterCodeThenEnter(gameId, _uiState.value.gameMasterPassword) }
    }

    private fun continueWithoutGameMaster() {
        val gameId = _uiState.value.gameMasterCodeFailedGameId ?: return
        _uiState.update { it.copy(gameMasterCodeFailedGameId = null) }
        viewModelScope.launch { _effects.send(GameCreationEffect.GameStarted(gameId)) }
    }

    private suspend fun saveGameMasterCodeThenEnter(gameId: String, password: String) {
        try {
            gameFunctions.setGameMasterPassword(gameId, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[create] referee code not saved", e)
            _uiState.update { it.copy(gameMasterCodeFailedGameId = gameId) }
            return
        }
        _effects.send(GameCreationEffect.GameStarted(gameId))
    }

    /** Mirrors iOS `clampStartDateToMinimum`: pushes the start date forward
     *  if it falls before the wizard's minimum allowed (now + 1 min). */
    private fun clampStartDateToMinimum() {
        val minimum = Date(System.currentTimeMillis() + 60_000L)
        val current = _uiState.value.game.startDate
        if (current.before(minimum)) {
            _uiState.update {
                val newGame = it.game.copy(
                    timing = it.game.timing.copy(start = Timestamp(minimum))
                )
                it.copy(game = newGame)
            }
        }
    }

    private fun recalculateNormalMode() {
        val state = _uiState.value
        val effectiveDuration = maxOf(state.gameDurationMinutes - state.game.timing.headStartMinutes, 1.0)
        val (interval, decline) = calculateNormalModeSettings(
            state.game.zone.radius, effectiveDuration
        )
        _uiState.update {
            it.copy(game = it.game.copy(
                zone = it.game.zone.copy(
                    shrinkIntervalMinutes = interval,
                    shrinkMetersPerUpdate = decline
                )
            ))
        }
    }

    /**
     * Compresses a QA debug game's timing so every phase is reachable in
     * minutes: near-now start, no head start, short duration and the minimum
     * 1-min shrink interval (the floor enforced by firestore.rules). Manual
     * launch stays on so the host triggers the start on demand. Mirrors iOS
     * `GameCreationFeature.applyDebugTiming`.
     */
    private fun applyDebugTiming(game: Game): Game {
        val durationMinutes = 5.0
        val shrinkIntervalMinutes = 1.0
        val start = _uiState.value.minimumStartDate
        val end = Date(start.time + (durationMinutes * 60 * 1000).toLong())
        val shrinks = maxOf(1.0, durationMinutes / shrinkIntervalMinutes)
        val decline = maxOf(0.0, (game.zone.radius - 100.0) / shrinks)
        return game.copy(
            manualStartEnabled = true,
            timing = game.timing.copy(
                start = Timestamp(start),
                end = Timestamp(end),
                headStartMinutes = 0.0,
            ),
            zone = game.zone.copy(
                shrinkIntervalMinutes = shrinkIntervalMinutes,
                shrinkMetersPerUpdate = decline,
            ),
        )
    }

    private fun startGame() {
        clampStartDateToMinimum()
        val game = _uiState.value.game
        val state = _uiState.value
        val endDate = Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
        val baseGame = game.withEndDate(endDate)
        // Debug games override the timing so every phase is reachable in
        // minutes (near-now start, 0 head start, 5-min duration, 1-min shrink).
        val finalGame = if (isDebugGame) applyDebugTiming(baseGame) else baseGame
        val enableGameMaster = state.isGameMasterEnabled && state.gameMasterPassword.length == 4
        val gmPassword = state.gameMasterPassword

        viewModelScope.launch {
            try {
                gameRepository.setConfig(finalGame)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[create] game write failed", e)
                _uiState.update { it.copy(createErrorRes = e.errorMessageRes()) }
                return@launch
            }
            analyticsRepository.gameCreated(
                gameMode = finalGame.gameMode,
                maxPlayers = finalGame.maxPlayers,
                powerUpsEnabled = finalGame.powerUps.enabled
            )
            if (enableGameMaster) {
                saveGameMasterCodeThenEnter(finalGame.id, gmPassword)
            } else {
                _effects.send(GameCreationEffect.GameStarted(finalGame.id))
            }
        }
    }
}
