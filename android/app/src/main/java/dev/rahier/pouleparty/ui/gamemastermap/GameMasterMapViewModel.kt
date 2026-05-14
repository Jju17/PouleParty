package dev.rahier.pouleparty.ui.gamemastermap

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.ui.chickenmap.HunterAnnotation
import dev.rahier.pouleparty.ui.gamelogic.detectNewWinners
import dev.rahier.pouleparty.ui.gamelogic.interpolateZoneCenter
import dev.rahier.pouleparty.ui.map.MapUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * Read-only map state for the GameMaster role (PP-24). Mirrors iOS
 * `GameMasterMapFeature.State` — same shape as the Chicken/Hunter
 * states (so shared composables work) but power-up tray fields are
 * always empty and `isOutsideZone` is never set (the GM is a pure
 * spectator and is not zone-checked).
 */
data class GameMasterMapUiState(
    override val game: Game = Game.mock,
    val chickenLocation: Point? = null,
    val chickenIsInvisible: Boolean = false,
    val hunterAnnotations: List<HunterAnnotation> = emptyList(),
    /** Raw hunter locations cached so marker labels can be rebuilt when
     *  the registrations stream emits — a hunter's team name may land
     *  after their first location ping. */
    val hunterLocations: List<dev.rahier.pouleparty.model.HunterLocation> = emptyList(),
    val powerUpAnnotations: List<PowerUp> = emptyList(),
    /** PP-86: registrations preloaded once at game load so the drawer
     *  can render teamNames + offer designation. */
    val registrations: List<dev.rahier.pouleparty.model.Registration> = emptyList(),
    /** PP-86: hunter awaiting confirmation. Non-null = alert showing. */
    val pendingChickenDesignation: dev.rahier.pouleparty.model.Registration? = null,
    /** PP-86: last error message from `designateChicken` to surface. */
    val designationError: String? = null,
    override val nextRadiusUpdate: Date? = null,
    override val nowDate: Date = Date(),
    override val radius: Int = 1500,
    override val circleCenter: Point? = null,
    override val showGameInfo: Boolean = false,
    val showHuntersDrawer: Boolean = false,
    override val winnerNotification: String? = null,
    override val hasGameStarted: Boolean = false,
    override val countdownNumber: Int? = null,
    override val countdownText: String? = null,
    val previousWinnersCount: Int = -1,
    override val isOutsideZone: Boolean = false,
    override val availablePowerUps: List<PowerUp> = emptyList(),
    override val collectedPowerUps: List<PowerUp> = emptyList(),
    override val showPowerUpInventory: Boolean = false,
    override val powerUpNotification: String? = null,
    override val lastActivatedPowerUpType: PowerUpType? = null,
) : MapUiState

@HiltViewModel
class GameMasterMapViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val playerId: String = auth.currentUser?.uid ?: ""
    private val streamJobs = mutableListOf<Job>()
    private var winnerNotificationJob: Job? = null

    private val _uiState = MutableStateFlow(GameMasterMapUiState())
    val uiState: StateFlow<GameMasterMapUiState> = _uiState.asStateFlow()

    private val _effects = Channel<GameMasterMapEffect>(Channel.BUFFERED)
    val effects: Flow<GameMasterMapEffect> = _effects.receiveAsFlow()

    init {
        loadGame()
    }

    fun onIntent(intent: GameMasterMapIntent) {
        when (intent) {
            GameMasterMapIntent.InfoTapped -> _uiState.update { it.copy(showGameInfo = true) }
            GameMasterMapIntent.DismissGameInfo -> _uiState.update { it.copy(showGameInfo = false) }
            GameMasterMapIntent.HuntersDrawerTapped -> _uiState.update { it.copy(showHuntersDrawer = true) }
            GameMasterMapIntent.DismissHuntersDrawer -> _uiState.update { it.copy(showHuntersDrawer = false) }
            GameMasterMapIntent.LeaveGameTapped -> viewModelScope.launch {
                _effects.send(GameMasterMapEffect.ReturnedToMenu)
            }
            is GameMasterMapIntent.DesignateHunterTapped ->
                _uiState.update { it.copy(pendingChickenDesignation = intent.registration) }
            GameMasterMapIntent.DesignateCancelTapped ->
                _uiState.update { it.copy(pendingChickenDesignation = null) }
            GameMasterMapIntent.DesignationErrorDismissed ->
                _uiState.update { it.copy(designationError = null) }
            GameMasterMapIntent.DesignateConfirmTapped -> {
                val reg = _uiState.value.pendingChickenDesignation ?: return
                val gameId = _uiState.value.game.id
                _uiState.update { it.copy(pendingChickenDesignation = null) }
                viewModelScope.launch {
                    try {
                        firestoreRepository.designateChicken(gameId, reg.userId)
                        _uiState.update { it.copy(showHuntersDrawer = false) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(designationError = e.message ?: "Failed to designate chicken") }
                    }
                }
            }
        }
    }

    private fun loadGame() {
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(gameId)
            if (game == null) {
                Log.w("GameMasterMapVM", "Game $gameId not found")
                return@launch
            }
            val (nextUpdate, lastRadius) = game.findLastUpdate()
            val center = interpolateZoneCenter(
                initialCenter = game.initialLocation,
                finalCenter = game.finalLocation,
                initialRadius = game.zone.radius,
                currentRadius = lastRadius.toDouble(),
            )
            _uiState.update {
                it.copy(
                    game = game,
                    nextRadiusUpdate = nextUpdate,
                    radius = lastRadius,
                    circleCenter = center,
                    hasGameStarted = Date().after(game.startDate),
                    previousWinnersCount = game.winners.size,
                )
            }
            startStreams(game)
        }
    }

    private fun startStreams(initialGame: Game) {
        streamJobs += viewModelScope.launch {
            firestoreRepository.gameConfigFlow(gameId).collect { game ->
                if (game != null) onGameUpdated(game)
            }
        }
        // PP-86 + GM-live-fix: stream registrations so the hunter
        // counter + drawer team-name list update the instant a hunter
        // joins, instead of staying frozen on a one-shot load. Also
        // rebuild marker labels so a hunter's `teamName` replaces the
        // index-based `Hunter N` fallback as soon as their registration
        // doc lands.
        streamJobs += viewModelScope.launch {
            firestoreRepository.registrationsFlow(gameId).collect { regs ->
                _uiState.update { state ->
                    state.copy(
                        registrations = regs,
                        hunterAnnotations = buildHunterAnnotations(state.hunterLocations, regs),
                    )
                }
            }
        }
        streamJobs += viewModelScope.launch {
            firestoreRepository.chickenLocationFlow(gameId).collect { chickenLoc ->
                // PP-87: GM always shows the chicken regardless of the
                // `invisible` flag, but surfaces the flag so the marker
                // can render in a distinct "hidden" style.
                val point = chickenLoc?.let {
                    Point.fromLngLat(it.location.longitude, it.location.latitude)
                }
                _uiState.update {
                    it.copy(
                        chickenLocation = point,
                        chickenIsInvisible = chickenLoc?.invisible ?: false,
                    )
                }
            }
        }
        streamJobs += viewModelScope.launch {
            firestoreRepository.hunterLocationsFlow(gameId).collect { locations ->
                _uiState.update { state ->
                    state.copy(
                        hunterLocations = locations,
                        hunterAnnotations = buildHunterAnnotations(locations, state.registrations),
                    )
                }
            }
        }
        if (initialGame.powerUps.enabled) {
            streamJobs += viewModelScope.launch {
                firestoreRepository.powerUpsFlow(gameId).collect { all ->
                    _uiState.update { it.copy(powerUpAnnotations = all.filter { p -> (p.collectedBy ?: "").isEmpty() }) }
                }
            }
        }
        streamJobs += viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(nowDate = Date()) }
                val state = _uiState.value
                val next = state.nextRadiusUpdate
                if (next != null && state.nowDate.after(next)) {
                    val (newNext, newRadius) = state.game.findLastUpdate()
                    val newCenter = interpolateZoneCenter(
                        initialCenter = state.game.initialLocation,
                        finalCenter = state.game.finalLocation,
                        initialRadius = state.game.zone.radius,
                        currentRadius = newRadius.toDouble(),
                    )
                    _uiState.update {
                        it.copy(
                            nextRadiusUpdate = newNext,
                            radius = newRadius,
                            circleCenter = newCenter,
                        )
                    }
                }
                _uiState.update { it.copy(hasGameStarted = Date().after(it.game.startDate)) }
                delay(1000L)
            }
        }
    }

    private fun onGameUpdated(game: Game) {
        val previousCount = _uiState.value.previousWinnersCount
        val notif = if (previousCount >= 0) {
            detectNewWinners(winners = game.winners, previousCount = previousCount)
        } else null
        _uiState.update {
            it.copy(
                game = game,
                winnerNotification = notif ?: it.winnerNotification,
                previousWinnersCount = game.winners.size,
            )
        }
        if (notif != null) {
            winnerNotificationJob?.cancel()
            winnerNotificationJob = viewModelScope.launch {
                delay(3000L)
                _uiState.update { it.copy(winnerNotification = null) }
            }
        }
    }

    override fun onCleared() {
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
        winnerNotificationJob?.cancel()
        super.onCleared()
    }
}

/**
 * Build hunter markers for the GameMaster map. The label prefers the
 * hunter's registered `teamName`; it falls back to an index-based
 * `Hunter N` (sorted by hunterId) so a hunter who hasn't yet been
 * matched with a registration doc still gets a stable label.
 */
private fun buildHunterAnnotations(
    locations: List<dev.rahier.pouleparty.model.HunterLocation>,
    registrations: List<dev.rahier.pouleparty.model.Registration>,
): List<HunterAnnotation> {
    val sorted = locations.sortedBy { it.hunterId }
    val teamNameByUserId = registrations.associate { it.userId to it.teamName }
    return sorted.mapIndexed { index, hunter ->
        HunterAnnotation(
            id = hunter.hunterId,
            coordinate = Point.fromLngLat(hunter.location.longitude, hunter.location.latitude),
            displayName = teamNameByUserId[hunter.hunterId] ?: "Hunter ${index + 1}",
        )
    }
}
