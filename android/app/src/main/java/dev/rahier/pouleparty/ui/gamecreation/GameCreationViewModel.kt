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
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.model.calculateNormalModeSettings
import kotlin.math.ceil
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
    val gameDurationMinutes: Double = 120.0,
    val isExpertMode: Boolean = false,
    val showPowerUpSelection: Boolean = false,
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
                GameCreationStep.START_TIME,
                GameCreationStep.DURATION,
                GameCreationStep.HEAD_START,
                GameCreationStep.POWER_UPS
            ))
            if (game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN) {
                base.add(GameCreationStep.CHICKEN_SEES_HUNTERS)
            }
            base.add(GameCreationStep.REGISTRATION)
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
}

@HiltViewModel
class GameCreationViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
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
                numberOfPlayers = numberOfPlayers,
                radiusIntervalUpdate = 5.0,
                initialRadius = 1500.0,
                radiusDeclinePerUpdate = 100.0,
                chickenHeadStartMinutes = 5.0,
                gameMod = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                foundCode = Game.generateFoundCode(),
                creatorId = auth.currentUser?.uid ?: "",
                driftSeed = (1..999_999).random(),
                pricingModel = pricingModel,
                pricePerPlayer = pricePerPlayerCents,
                depositAmount = depositAmountCents,
                requiresRegistration = pricingModel == "deposit"
            )
        )
    )
    val uiState: StateFlow<GameCreationUiState> = _uiState.asStateFlow()

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

    fun next() {
        val state = _uiState.value
        val nextIndex = state.currentStepIndex + 1
        if (nextIndex < state.steps.size) {
            _uiState.update { it.copy(currentStepIndex = nextIndex, goingForward = true) }
        }
    }

    fun back() {
        val state = _uiState.value
        if (state.currentStepIndex > 0) {
            _uiState.update { it.copy(currentStepIndex = state.currentStepIndex - 1, goingForward = false) }
        }
    }

    fun setParticipating(participating: Boolean) {
        _uiState.update { it.copy(isParticipating = participating) }
    }

    fun updateGameMod(mod: GameMod) {
        _uiState.update { it.copy(game = it.game.copy(gameMod = mod.firestoreValue)) }
    }

    fun onStartTimeTapped() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun dismissTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun updateStartDate(hour: Int, minute: Int) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
        }
        val minDate = Date(System.currentTimeMillis() + 120_000)
        if (cal.time.before(minDate)) {
            cal.time = minDate
        }
        _uiState.update { it.copy(game = it.game.withStartDate(cal.time), showTimePicker = false) }
    }

    fun updateDuration(minutes: Double) {
        _uiState.update { it.copy(gameDurationMinutes = minutes) }
        recalculateIfNormalMode()
    }

    fun updateHeadStart(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(chickenHeadStartMinutes = value)) }
        recalculateIfNormalMode()
    }

    fun updateInitialRadius(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(initialRadius = value)) }
        recalculateIfNormalMode()
    }

    fun togglePowerUps(enabled: Boolean) {
        _uiState.update { it.copy(game = it.game.copy(powerUpsEnabled = enabled)) }
    }

    fun togglePowerUpType(type: PowerUpType) {
        _uiState.update { state ->
            val current = state.game.enabledPowerUpTypes
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
            state.copy(game = state.game.copy(enabledPowerUpTypes = newList))
        }
    }

    fun toggleChickenCanSeeHunters(value: Boolean) {
        _uiState.update { it.copy(game = it.game.withChickenCanSeeHunters(value)) }
    }

    fun toggleRequiresRegistration(value: Boolean) {
        _uiState.update { state ->
            // Deposit games always require registration
            if (state.game.pricingModel == "deposit" && !value) return@update state
            state.copy(game = state.game.copy(requiresRegistration = value))
        }
    }

    fun onLocationSelected(point: Point) {
        _uiState.update { it.copy(game = it.game.withInitialLocation(point)) }
    }

    fun onFinalLocationSelected(point: Point?) {
        _uiState.update { it.copy(game = it.game.withFinalLocation(point)) }
    }

    fun onPowerUpSelectionTapped() {
        _uiState.update { it.copy(showPowerUpSelection = true) }
    }

    fun dismissPowerUpSelection() {
        _uiState.update { it.copy(showPowerUpSelection = false) }
    }

    fun onCodeCopied() {
        _uiState.update { it.copy(codeCopied = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _uiState.update { it.copy(codeCopied = false) }
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(showAlert = false) }
    }

    private fun recalculateIfNormalMode() {
        val state = _uiState.value
        if (state.isExpertMode) return
        val effectiveDuration = maxOf(state.gameDurationMinutes - state.game.chickenHeadStartMinutes, 1.0)
        val (interval, decline) = calculateNormalModeSettings(
            state.game.initialRadius, effectiveDuration
        )
        _uiState.update {
            it.copy(game = it.game.copy(
                radiusIntervalUpdate = interval,
                radiusDeclinePerUpdate = decline
            ))
        }
    }

    fun startGame(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val game = _uiState.value.game
                val state = _uiState.value
                val endDate = if (state.isExpertMode) {
                    val shrinks = ceil(game.initialRadius / game.radiusDeclinePerUpdate)
                    val durationMs = (shrinks * game.radiusIntervalUpdate * 60 * 1000).toLong()
                    Date(game.hunterStartDate.time + durationMs)
                } else {
                    Date(game.startDate.time + (state.gameDurationMinutes * 60 * 1000).toLong())
                }
                val finalGame = game.withEndDate(endDate)
                firestoreRepository.setConfig(finalGame)
                onSuccess(finalGame.id)
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
