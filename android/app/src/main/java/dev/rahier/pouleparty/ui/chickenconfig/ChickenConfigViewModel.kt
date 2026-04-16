package dev.rahier.pouleparty.ui.chickenconfig

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
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
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ChickenConfigUiState(
    val game: Game = Game.mock,
    val codeCopied: Boolean = false,
    val showAlert: Boolean = false,
    val alertMessage: String = "",
    val showMapConfig: Boolean = false,
    val showTimePicker: Boolean = false,
    val isExpertMode: Boolean = false,
    val gameDurationMinutes: Double = 120.0,
    val showPowerUpSelection: Boolean = false
) {
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
class ChickenConfigViewModel @Inject constructor(
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
        ChickenConfigUiState(
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
                    headStartMinutes = 5.0
                ),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue,
                foundCode = Game.generateFoundCode(),
                creatorId = auth.currentUser?.uid ?: "",
                pricing = Pricing(
                    model = pricingModel,
                    pricePerPlayer = pricePerPlayerCents,
                    deposit = depositAmountCents
                )
            )
        )
    )
    val uiState: StateFlow<ChickenConfigUiState> = _uiState.asStateFlow()

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

    fun onStartTimeTapped() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun dismissTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun updateStartDate(hour: Int, minute: Int) {
        val cal = calendarAt(hour = hour, minute = minute)
        // Ensure at least 2 min from now
        val minDate = Date(System.currentTimeMillis() + 120_000)
        if (cal.time.before(minDate)) {
            cal.time = minDate
        }
        _uiState.update { it.copy(game = it.game.withStartDate(cal.time), showTimePicker = false) }
    }

    fun updateGameMod(mod: GameMod) {
        _uiState.update { it.copy(game = it.game.copy(gameMode = mod.firestoreValue)) }
    }

    fun toggleChickenCanSeeHunters(value: Boolean) {
        _uiState.update { it.copy(game = it.game.withChickenCanSeeHunters(value)) }
    }

    fun updateRadiusIntervalUpdate(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(zone = it.game.zone.copy(shrinkIntervalMinutes = value))) }
    }

    fun updateRadiusDecline(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(zone = it.game.zone.copy(shrinkMetersPerUpdate = value))) }
    }

    fun updateChickenHeadStart(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(timing = it.game.timing.copy(headStartMinutes = value))) }
        recalculateIfNormalMode()
    }

    fun updateInitialRadius(value: Double) {
        _uiState.update { it.copy(game = it.game.copy(zone = it.game.zone.copy(radius = value))) }
        recalculateIfNormalMode()
    }

    fun toggleExpertMode(expert: Boolean) {
        _uiState.update { it.copy(isExpertMode = expert) }
        if (!expert) {
            recalculateIfNormalMode()
        }
    }

    fun updateGameDuration(minutes: Double) {
        _uiState.update { it.copy(gameDurationMinutes = minutes) }
        recalculateIfNormalMode()
    }

    private fun recalculateIfNormalMode() {
        val state = _uiState.value
        if (state.isExpertMode) return
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

    fun onMapSetupTapped() {
        _uiState.update { it.copy(showMapConfig = true) }
    }

    fun dismissMapConfig() {
        _uiState.update { it.copy(showMapConfig = false) }
    }

    fun onLocationSelected(point: com.mapbox.geojson.Point) {
        _uiState.update {
            it.copy(game = it.game.withInitialLocation(point))
        }
    }

    fun onFinalLocationSelected(point: com.mapbox.geojson.Point?) {
        _uiState.update {
            it.copy(game = it.game.withFinalLocation(point))
        }
    }

    fun togglePowerUps(enabled: Boolean) {
        _uiState.update { it.copy(game = it.game.copy(powerUps = it.game.powerUps.copy(enabled = enabled))) }
    }

    fun togglePowerUpType(type: dev.rahier.pouleparty.model.PowerUpType) {
        _uiState.update { state ->
            val current = state.game.powerUps.enabledTypes
            val unavailable = if (state.game.gameModEnum == GameMod.STAY_IN_THE_ZONE) {
                setOf(PowerUpType.INVISIBILITY.firestoreValue, PowerUpType.DECOY.firestoreValue, PowerUpType.JAMMER.firestoreValue)
            } else emptySet()
            val newList = if (current.contains(type.firestoreValue)) {
                // Count available (non-unavailable) enabled types
                val availableEnabledCount = current.count { it !in unavailable }
                val isAvailable = type.firestoreValue !in unavailable
                // Don't allow deselecting the last available one
                if (!isAvailable || availableEnabledCount > 1) current - type.firestoreValue else current
            } else {
                current + type.firestoreValue
            }
            state.copy(game = state.game.copy(powerUps = state.game.powerUps.copy(enabledTypes = newList)))
        }
    }

    fun onPowerUpSelectionTapped() {
        _uiState.update { it.copy(showPowerUpSelection = true) }
    }

    fun dismissPowerUpSelection() {
        _uiState.update { it.copy(showPowerUpSelection = false) }
    }

    fun startGame(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Auto-calculate endDate
                val game = _uiState.value.game
                val state = _uiState.value
                val endDate = if (state.isExpertMode) {
                    // Expert mode: endDate from radius parameters
                    val shrinks = ceil(game.zone.radius / game.zone.shrinkMetersPerUpdate)
                    val durationMs = (shrinks * game.zone.shrinkIntervalMinutes * 60 * 1000).toLong()
                    Date(game.hunterStartDate.time + durationMs)
                } else {
                    // Normal mode: endDate = startDate + total game duration
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
