package dev.rahier.pouleparty.ui.gamecreation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GamePowerUps
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.model.calculateNormalModeSettings
import dev.rahier.pouleparty.ui.gamelogic.availablePowerUpTypes
import dev.rahier.pouleparty.util.calendarAt
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

data class GameCreationUiState(
    val game: Game = Game.mock,
    val currentStepIndex: Int = 0,
    val isParticipating: Boolean = true,
    val gameDurationMinutes: Double = 90.0,
    val showPowerUpSelection: Boolean = false,
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showAlert: Boolean = false,
    val alertMessage: String = "",
    val codeCopied: Boolean = false,
    val goingForward: Boolean = true,
    /** PP-42: lifts the maxPlayers stepper from `2..5` (Free standard) to
     *  `2..500`. Always `false` in PP-42; PP-45 will flip it via the
     *  `jujurahier` admin code modal. */
    val isAdminCreation: Boolean = false,
    /** PP-88: toggle on the gameMasterPassword step. Default ON. */
    val isGameMasterEnabled: Boolean = true,
    /** PP-88: 4-digit GameMaster password collected on the step. */
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
            // Wizard order: When → How long → Mode → Where → Rules.
            // The timing trio precedes the zone block so PP-13's
            // recap sees a valid duration window when it computes
            // the shrink schedule. `GAME_MODE` sits right before
            // `START_ZONE_SETUP` because it decides the zone setup
            // sub-steps themselves (stayInTheZone has a final pin
            // step, followTheChicken doesn't) — keeping them
            // adjacent makes the wizard read as one coherent
            // "configure the playing field" beat.
            base.addAll(listOf(
                GameCreationStep.MAX_PLAYERS,
                GameCreationStep.START_TIME,
                GameCreationStep.DURATION,
                GameCreationStep.HEAD_START,
                GameCreationStep.GAME_MODE,
                GameCreationStep.START_ZONE_SETUP,
            ))
            // PP-12: `FINAL_ZONE_SETUP` only exists in stayInTheZone —
            // followTheChicken's zone tracks the chicken's live
            // position, no `finalCenter` to place.
            if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                base.add(GameCreationStep.FINAL_ZONE_SETUP)
            }
            // PP-13: recap step lives right after the zone pins so
            // the chicken can preview the trajectory.
            base.add(GameCreationStep.ZONES_RECAP)
            // PP-70 / PP-88: GameMaster password parked with the
            // other modifier toggles at the tail end of the wizard.
            base.add(GameCreationStep.GAME_MASTER_PASSWORD)
            base.add(GameCreationStep.POWER_UPS)
            base.add(GameCreationStep.CHICKEN_SEES_HUNTERS)
            base.add(GameCreationStep.RECAP)
            return base
        }

    /** Closed range allowed by the current Stepper. PP-45 plumbs through
     *  `isAdminCreation = true` to unlock the wider range. */
    val maxPlayersRange: IntRange
        get() = if (isAdminCreation) 2..500 else 2..5

    val currentStep: GameCreationStep
        get() = steps.getOrElse(currentStepIndex) { GameCreationStep.PARTICIPATION }

    val progress: Float
        get() = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size

    val canGoBack: Boolean
        get() = currentStepIndex > 0

    /** PP-11: start pin has been placed (zone center is no longer the
     *  Brussels default seeded at wizard creation). Gates the Next
     *  button on `START_ZONE_SETUP`. */
    val isStartZoneConfigured: Boolean
        get() {
            val loc = game.initialLocation
            val isDefault = kotlin.math.abs(loc.latitude() - dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE) < 0.001
                    && kotlin.math.abs(loc.longitude() - dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE) < 0.001
            return !isDefault
        }

    /** PP-12: final pin placed AND at least 100 m from the start
     *  (haversine). Gates Next on `FINAL_ZONE_SETUP`. */
    val isFinalZoneConfigured: Boolean
        get() {
            val finalLoc = game.finalLocation ?: return false
            val start = game.initialLocation
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                start.latitude(), start.longitude(),
                finalLoc.latitude(), finalLoc.longitude(),
                results,
            )
            return results[0] >= 100f
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

    /** PP-90: anyone can join anytime, even mid-game. Minimum start time
     *  is now + 1 minute (avoids clock skew on submission). */
    val minimumStartDate: Date
        get() = Date(System.currentTimeMillis() + 60_000L)
}

@HiltViewModel
class GameCreationViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    private val auth: FirebaseAuth,
    private val remoteConfig: dev.rahier.pouleparty.config.RemoteConfigProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val isAdminCreation: Boolean = savedStateHandle["isAdminCreation"] ?: false

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
                    driftSeed = (1..999_999).random()
                ),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                foundCode = Game.generateFoundCode(),
                creatorId = auth.currentUser?.uid ?: "",
                chickenId = auth.currentUser?.uid ?: "",
                isAdminCreation = isAdminCreation
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

    /**
     * PP-13 phase 1 — recompute the initial radius from the start +
     * final pins (or the user-picked size in followTheChicken) and
     * allocate a fresh `driftSeed` on first visit. Mirrors the iOS
     * `zonesRecapEntered` handler. Phase 2 will replace this with a
     * call to the PP-69 Cloud Function.
     */
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
            val newSeed = if (gameWithEnd.zone.driftSeed == 0) dev.rahier.pouleparty.model.generateDriftSeed() else gameWithEnd.zone.driftSeed
            // PP-13 bug fix: pick a non-centered initial disc that
            // still contains both pins (stayInTheZone only —
            // followTheChicken's disc tracks the chicken's live
            // position so there's no second point to contain).
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
                        center = com.google.firebase.firestore.GeoPoint(newCenter.latitude(), newCenter.longitude()),
                        radius = radius,
                        driftSeed = newSeed,
                        shrinkIntervalMinutes = interval,
                        shrinkMetersPerUpdate = decline,
                    )
                )
            )
        }
    }

    /**
     * PP-14 phase 1 — Shuffle: regenerate `driftSeed` AND re-pick the
     * initial disc center using the new seed (stayInTheZone only).
     * The preview circles redraw deterministically.
     */
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
                        center = com.google.firebase.firestore.GeoPoint(newCenter.latitude(), newCenter.longitude()),
                        driftSeed = newSeed,
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
            val cal = java.util.Calendar.getInstance().apply {
                time = state.game.startDate
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                set(java.util.Calendar.SECOND, 0)
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
            // Sync endDate so PP-13's recap sees a valid duration
            // window long before the wizard finishes — see iOS
            // sibling for the rationale.
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
            // PP-35: lean on the strict availability helper so we don't
            // drift from the UI's filter rules.
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
        // PP-11 / PP-13: the user-placed pin lives on `zone.startPin`
        // while `zone.center` mirrors it on PP-11 — PP-13's recap
        // will overwrite the center later with a computed non-centered
        // value, but `startPin` stays at the user's placement.
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
        _uiState.update { it.copy(showAlert = false) }
    }

    /** Mirrors iOS `clampStartDateToMinimum`: pushes the start date forward
     *  if it falls before the wizard's minimum allowed (now + 1 min). */
    private fun clampStartDateToMinimum() {
        val minimum = Date(System.currentTimeMillis() + 60_000L)
        val current = _uiState.value.game.startDate
        if (current.before(minimum)) {
            _uiState.update {
                val newGame = it.game.copy(
                    timing = it.game.timing.copy(start = com.google.firebase.Timestamp(minimum))
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

    private fun startGame() {
        clampStartDateToMinimum()
        val game = _uiState.value.game
        val state = _uiState.value
        val endDate = Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
        val finalGame = game.withEndDate(endDate)
        val enableGameMaster = state.isGameMasterEnabled && state.gameMasterPassword.length == 4
        val gmPassword = state.gameMasterPassword

        viewModelScope.launch {
            try {
                firestoreRepository.setConfig(finalGame)
                if (enableGameMaster) {
                    try {
                        firestoreRepository.setGameMasterPassword(finalGame.id, gmPassword)
                    } catch (_: Exception) {
                        // Game is created — chicken can retry from
                        // Settings (PP-88 follow-up).
                    }
                }
                analyticsRepository.gameCreated(
                    gameMode = finalGame.gameMode,
                    maxPlayers = finalGame.maxPlayers,
                    powerUpsEnabled = finalGame.powerUps.enabled
                )
                _effects.send(GameCreationEffect.GameStarted(finalGame.id))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showAlert = true,
                        alertMessage = "Could not create the game. Please check your connection and try again."
                    )
                }
            }
        }
    }
}
