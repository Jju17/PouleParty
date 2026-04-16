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
import dev.rahier.pouleparty.model.GameRegistration
import dev.rahier.pouleparty.model.Pricing
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.model.calculateNormalModeSettings
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
    val goingForward: Boolean = true
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
                GameCreationStep.GAME_MODE,
                GameCreationStep.ZONE_SETUP,
                GameCreationStep.REGISTRATION,
                GameCreationStep.START_TIME,
                GameCreationStep.DURATION,
                GameCreationStep.HEAD_START,
                GameCreationStep.POWER_UPS
            ))
            base.add(GameCreationStep.CHICKEN_SEES_HUNTERS)
            base.add(GameCreationStep.RECAP)
            return base
        }

    val currentStep: GameCreationStep
        get() = steps.getOrElse(currentStepIndex) { GameCreationStep.PARTICIPATION }

    val progress: Float
        get() = if (steps.isEmpty()) 0f else (currentStepIndex + 1).toFloat() / steps.size

    val canGoBack: Boolean
        get() = currentStepIndex > 0

    val isZoneConfigured: Boolean
        get() {
            val loc = game.initialLocation
            val isDefault = kotlin.math.abs(loc.latitude() - dev.rahier.pouleparty.AppConstants.DEFAULT_LATITUDE) < 0.001
                    && kotlin.math.abs(loc.longitude() - dev.rahier.pouleparty.AppConstants.DEFAULT_LONGITUDE) < 0.001
            if (isDefault) return false
            if (game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                return game.finalLocation != null
            }
            return true
        }

    /** Minimum start time based on registration settings.
     *  Open join: now + 1 minute.
     *  Registration required: now + deadline + 5 minutes buffer. */
    val minimumStartDate: Date
        get() {
            val bufferMs = 5 * 60 * 1000L
            if (game.registration.required) {
                val deadline = game.registration.closesMinutesBefore
                return if (deadline != null) {
                    Date(System.currentTimeMillis() + deadline * 60 * 1000L + bufferMs)
                } else {
                    Date(System.currentTimeMillis() + bufferMs)
                }
            }
            return Date(System.currentTimeMillis() + 60_000L)
        }
}

@HiltViewModel
class GameCreationViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val pricingModel: String = savedStateHandle["pricingModel"] ?: "free"
    private val numberOfPlayers: Int = savedStateHandle["numberOfPlayers"] ?: 5
    private val pricePerPlayerCents: Int = savedStateHandle["pricePerPlayerCents"] ?: 0
    private val depositAmountCents: Int = savedStateHandle["depositAmountCents"] ?: 0

    private val _uiState = MutableStateFlow(
        GameCreationUiState(
            game = Game(
                id = gameId,
                name = "",
                maxPlayers = numberOfPlayers,
                zone = Zone(
                    radius = 1500.0,
                    shrinkIntervalMinutes = 5.0,
                    shrinkMetersPerUpdate = 100.0,
                    driftSeed = (1..999_999).random()
                ),
                timing = Timing(
                    headStartMinutes = 0.0
                ),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                foundCode = Game.generateFoundCode(),
                creatorId = auth.currentUser?.uid ?: "",
                pricing = Pricing(
                    model = pricingModel,
                    pricePerPlayer = pricePerPlayerCents,
                    deposit = depositAmountCents
                ),
                registration = GameRegistration(
                    required = pricingModel == "deposit"
                )
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
            is GameCreationIntent.PowerUpsToggled -> togglePowerUps(intent.enabled)
            is GameCreationIntent.PowerUpTypeToggled -> togglePowerUpType(intent.type)
            is GameCreationIntent.ChickenCanSeeHuntersToggled -> toggleChickenCanSeeHunters(intent.value)
            is GameCreationIntent.RequiresRegistrationToggled -> toggleRequiresRegistration(intent.required)
            is GameCreationIntent.RegistrationClosesBeforeStartChanged -> setRegistrationClosesBeforeStart(intent.minutes)
            is GameCreationIntent.LocationSelected -> onLocationSelected(intent.point)
            is GameCreationIntent.FinalLocationSelected -> onFinalLocationSelected(intent.point)
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
            state.copy(
                game = state.game.withStartDate(cal.time),
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
            state.copy(game = state.game.withStartDate(cal.time), showTimePicker = false)
        }
    }

    private fun updateDuration(minutes: Double) {
        _uiState.update { it.copy(gameDurationMinutes = minutes) }
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

    private fun togglePowerUps(enabled: Boolean) {
        _uiState.update { it.copy(game = it.game.copy(powerUps = it.game.powerUps.copy(enabled = enabled))) }
    }

    private fun togglePowerUpType(type: PowerUpType) {
        _uiState.update { state ->
            val current = state.game.powerUps.enabledTypes
            val unavailable = if (state.game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                setOf(PowerUpType.INVISIBILITY.firestoreValue, PowerUpType.DECOY.firestoreValue, PowerUpType.JAMMER.firestoreValue)
            } else emptySet()
            val newList = if (current.contains(type.firestoreValue)) {
                val availableEnabledCount = current.count { it !in unavailable }
                val isAvailable = type.firestoreValue !in unavailable
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

    private fun toggleRequiresRegistration(value: Boolean) {
        _uiState.update { state ->
            // Deposit games always require registration
            if (state.game.pricing.model == "deposit" && !value) return@update state
            val updatedGame = state.game.copy(
                registration = state.game.registration.copy(
                    required = value,
                    closesMinutesBefore = if (!value) null else (state.game.registration.closesMinutesBefore ?: 15)
                )
            )
            state.copy(game = updatedGame)
        }
        clampStartDateToMinimum()
    }

    private fun setRegistrationClosesBeforeStart(minutes: Int?) {
        _uiState.update { state ->
            state.copy(game = state.game.copy(registration = state.game.registration.copy(closesMinutesBefore = minutes)))
        }
        clampStartDateToMinimum()
    }

    /** Pushes the start date forward if it falls before the minimum allowed. */
    private fun clampStartDateToMinimum() {
        _uiState.update { state ->
            val minimum = state.minimumStartDate
            if (state.game.startDate.before(minimum)) {
                state.copy(game = state.game.withStartDate(minimum))
            } else {
                state
            }
        }
    }

    private fun onLocationSelected(point: Point) {
        _uiState.update { it.copy(game = it.game.withInitialLocation(point)) }
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
        viewModelScope.launch {
            try {
                val game = _uiState.value.game
                val state = _uiState.value
                val endDate = Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
                val finalGame = game.withEndDate(endDate)
                firestoreRepository.setConfig(finalGame)
                analyticsRepository.gameCreated(
                    gameMode = finalGame.gameMode,
                    maxPlayers = finalGame.maxPlayers,
                    pricingModel = finalGame.pricing.model,
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
