package dev.rahier.pouleparty.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.ui.gamelogic.PlayerRole
import dev.rahier.pouleparty.util.getTrimmedString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isShowingJoinSheet: Boolean = false,
    val isShowingGameRules: Boolean = false,
    val isShowingGameNotFound: Boolean = false,
    val isShowingLocationRequired: Boolean = false,
    val isMusicMuted: Boolean = false,
    val gameCode: String = "",
    val teamName: String = "",
    val joinStep: JoinFlowStep = JoinFlowStep.EnteringCode,
    val activeGame: Game? = null,
    val activeGameRole: PlayerRole? = null,
    /** Distinguishes "Reprendre" (IN_PROGRESS) from "Prochaine partie"
     *  (UPCOMING) for the Home banner copy + CTA. Null when no active game. */
    val activeGamePhase: dev.rahier.pouleparty.ui.gamelogic.GamePhase? = null,
    /** PP-45: admin-code dialog open / current input / wrong-code error. */
    val isShowingAdminCodeDialog: Boolean = false,
    val adminCodeInput: String = "",
    val isShowingAdminCodeError: Boolean = false,
    /** PP-88: 4-digit buffer + last error from joinAsGameMaster. */
    val gameMasterPasswordInput: String = "",
    val gameMasterPasswordError: String? = null,
) {
    val isCodeValid: Boolean
        get() {
            val trimmed = gameCode.trim().uppercase()
            return trimmed.length == AppConstants.GAME_CODE_LENGTH
                && trimmed.all { it.isLetter() || it.isDigit() }
        }

    val isTeamNameValid: Boolean
        get() = teamName.trim().isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    /** Single entry point for every user interaction. */
    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.StartButtonTapped -> onStartButtonTapped()
            HomeIntent.RulesTapped -> onRulesTapped()
            HomeIntent.RulesDismissed -> onRulesDismissed()
            HomeIntent.GameNotFoundDismissed -> onGameNotFoundDismissed()
            HomeIntent.LocationRequiredDismissed -> onLocationRequiredDismissed()
            HomeIntent.LocationPermissionDenied -> onLocationPermissionDenied()
            HomeIntent.CreatePartyTapped -> { /* host handles — see [canCreateParty] */ }
            HomeIntent.CreatePartyLongPressed -> onCreatePartyLongPressed()
            HomeIntent.JoinSheetDismissed -> onJoinSheetDismissed()
            HomeIntent.ToggleMusic -> toggleMusicMuted()
            HomeIntent.ActiveGameDismissed -> dismissActiveGame()
            HomeIntent.RejoinActiveGameTapped -> rejoinActiveGame()
            HomeIntent.JoinAsHunterTapped -> onJoinAsHunterTapped()
            HomeIntent.SubmitJoinTapped -> onSubmitJoinTapped()
            HomeIntent.RefreshActiveGame -> checkForActiveGame()
            HomeIntent.AdminModeTapped -> onAdminModeTapped()
            HomeIntent.AdminCodeDismissed -> _uiState.update { it.copy(isShowingAdminCodeDialog = false, adminCodeInput = "") }
            HomeIntent.AdminCodeErrorDismissed -> _uiState.update { it.copy(isShowingAdminCodeError = false) }
            HomeIntent.WebCreatePartyTapped -> onWebCreatePartyTapped()
            is HomeIntent.GameCodeChanged -> onGameCodeChanged(intent.code)
            is HomeIntent.TeamNameChanged -> onTeamNameChanged(intent.name)
            is HomeIntent.AdminCodeChanged -> _uiState.update { it.copy(adminCodeInput = intent.code) }
            HomeIntent.JoinAsGameMasterTapped -> onJoinAsGameMasterTapped()
            is HomeIntent.GameMasterPasswordChanged -> onGameMasterPasswordChanged(intent.code)
            HomeIntent.SubmitGameMasterPasswordTapped -> onSubmitGameMasterPasswordTapped()
        }
    }

    private fun onJoinAsGameMasterTapped() {
        val game = (_uiState.value.joinStep as? JoinFlowStep.CodeValidated)?.game ?: return
        _uiState.update {
            it.copy(
                joinStep = JoinFlowStep.GameMasterPasswordEntry(game),
                gameMasterPasswordInput = "",
                gameMasterPasswordError = null,
            )
        }
    }

    private fun onGameMasterPasswordChanged(raw: String) {
        // Strip non-digits, clamp to 4. UI binds the input back to state.
        val clean = raw.filter { it.isDigit() }.take(4)
        _uiState.update { it.copy(gameMasterPasswordInput = clean) }
    }

    private fun onSubmitGameMasterPasswordTapped() {
        val game = (_uiState.value.joinStep as? JoinFlowStep.GameMasterPasswordEntry)?.game ?: return
        val password = _uiState.value.gameMasterPasswordInput
        if (password.length != 4) return
        _uiState.update {
            it.copy(
                joinStep = JoinFlowStep.SubmittingGameMasterPassword(game),
                gameMasterPasswordError = null,
            )
        }
        viewModelScope.launch {
            try {
                val result = firestoreRepository.joinAsGameMaster(game.id, password)
                if (result.success) {
                    _uiState.update {
                        it.copy(
                            isShowingJoinSheet = false,
                            joinStep = JoinFlowStep.EnteringCode,
                            gameCode = "",
                            gameMasterPasswordInput = "",
                            gameMasterPasswordError = null,
                        )
                    }
                    _effects.send(HomeEffect.NavigateToGameMasterMap(game.id))
                } else {
                    val msg = if (result.lockedUntilMs != null) {
                        val mins = ((result.lockedUntilMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1L)
                        "Trop de tentatives. Réessaie dans $mins min."
                    } else {
                        "Mauvais code. ${result.attemptsRemaining} tentative(s) restante(s)."
                    }
                    _uiState.update {
                        it.copy(
                            joinStep = JoinFlowStep.GameMasterPasswordEntry(game),
                            gameMasterPasswordInput = "",
                            gameMasterPasswordError = msg,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        joinStep = JoinFlowStep.GameMasterPasswordEntry(game),
                        gameMasterPasswordError = e.message ?: "Network error",
                    )
                }
            }
        }
    }

    private fun onWebCreatePartyTapped() {
        val lang = java.util.Locale.getDefault().language
        val url = dev.rahier.pouleparty.model.CreatePartyUrl.forLanguage(lang)
        viewModelScope.launch { _effects.send(HomeEffect.OpenWebUrl(url)) }
    }

    private fun onAdminModeTapped() {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(isShowingLocationRequired = true) }
            return
        }
        _uiState.update { it.copy(isShowingAdminCodeDialog = true, adminCodeInput = "") }
    }

    /**
     * Validates the entered admin code against [AdminCode.VALUE]. Returns
     * true if the user should be navigated to the GameCreation wizard with
     * `isAdminCreation = true`. On false, surfaces the wrong-code alert via
     * `isShowingAdminCodeError`. The screen calls this directly (rather than
     * through [onIntent]) because nav has to react to the boolean result.
     */
    fun validateAdminCode(): Boolean {
        val entered = _uiState.value.adminCodeInput.trim()
        return if (entered == dev.rahier.pouleparty.model.AdminCode.VALUE) {
            _uiState.update { it.copy(isShowingAdminCodeDialog = false, adminCodeInput = "") }
            true
        } else {
            _uiState.update {
                it.copy(
                    isShowingAdminCodeDialog = false,
                    adminCodeInput = "",
                    isShowingAdminCodeError = true,
                )
            }
            false
        }
    }

    /**
     * Returns whether the host can trigger party creation navigation.
     * Kept as a direct API (not an Intent) because it's a synchronous
     * permission gate the Compose navigation layer inspects.
     */
    fun canCreateParty(): Boolean = onCreatePartyTapped()

    fun hasLocationPermission(): Boolean = locationRepository.hasFineLocationPermission()

    init {
        _uiState.update {
            it.copy(isMusicMuted = prefs.getBoolean(AppConstants.PREF_IS_MUSIC_MUTED, false))
        }
        checkForActiveGame()
    }

    /**
     * In-flight guard so two concurrent `checkForActiveGame` calls (init +
     * LifecycleEventEffect.ON_RESUME, or back-navigation + tab switch) don't
     * race: only the first fetch runs; the second one becomes a no-op.
     */
    @Volatile
    private var activeGameCheckInFlight: kotlinx.coroutines.Job? = null

    private fun checkForActiveGame() {
        if (activeGameCheckInFlight?.isActive == true) return
        val userId = auth.currentUser?.uid ?: return
        activeGameCheckInFlight = viewModelScope.launch {
            val result = firestoreRepository.findActiveGame(userId)
            val dismissedIds = loadDismissedActiveGameIds()
            if (result != null && !dismissedIds.contains(result.game.id)) {
                _uiState.update {
                    it.copy(
                        activeGame = result.game,
                        activeGameRole = result.role,
                        activeGamePhase = result.phase,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(activeGame = null, activeGameRole = null, activeGamePhase = null)
                }
            }
        }
    }

    private fun loadDismissedActiveGameIds(): Set<String> {
        return prefs.getStringSet(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS, emptySet())?.toSet()
            ?: emptySet()
    }

    private fun addDismissedActiveGameId(gameId: String) {
        val current = loadDismissedActiveGameIds()
        prefs.edit()
            .putStringSet(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS, current + gameId)
            .apply()
    }

    private fun removeDismissedActiveGameId(gameId: String) {
        val current = loadDismissedActiveGameIds()
        if (gameId !in current) return
        prefs.edit()
            .putStringSet(AppConstants.PREF_DISMISSED_ACTIVE_GAME_IDS, current - gameId)
            .apply()
    }

    private fun toggleMusicMuted() {
        val newValue = !_uiState.value.isMusicMuted
        _uiState.update { it.copy(isMusicMuted = newValue) }
        prefs.edit().putBoolean(AppConstants.PREF_IS_MUSIC_MUTED, newValue).apply()
    }

    private fun dismissActiveGame() {
        val dismissedId = _uiState.value.activeGame?.id
        _uiState.update {
            it.copy(activeGame = null, activeGameRole = null, activeGamePhase = null)
        }
        if (!dismissedId.isNullOrEmpty()) {
            addDismissedActiveGameId(dismissedId)
        }
    }

    private fun rejoinActiveGameClearsDismissedFlag(gameId: String) {
        removeDismissedActiveGameId(gameId)
    }

    private fun rejoinActiveGame() {
        val cachedGame = _uiState.value.activeGame ?: return
        val role = _uiState.value.activeGameRole ?: return
        rejoinActiveGameClearsDismissedFlag(cachedGame.id)
        _uiState.update {
            it.copy(activeGame = null, activeGameRole = null, activeGamePhase = null)
        }
        val savedNickname = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
        val hunterName = savedNickname.ifEmpty { "Hunter" }
        viewModelScope.launch {
            // Refetch to catch games that transitioned to done between the
            // banner being shown and the tap. Falls back to the cached game
            // if the refetch fails (better UX than freezing on a transient
            // network error).
            val fresh = runCatching { firestoreRepository.getConfig(cachedGame.id) }.getOrNull()
            val game = fresh ?: cachedGame
            when (game.gameStatusEnum) {
                GameStatus.DONE -> {
                    _uiState.update { it.copy(isShowingGameNotFound = true) }
                    return@launch
                }
                GameStatus.WAITING, GameStatus.IN_PROGRESS -> Unit
            }
            when (role) {
                PlayerRole.CHICKEN -> _effects.send(HomeEffect.NavigateToChickenMap(game.id))
                PlayerRole.HUNTER -> _effects.send(HomeEffect.NavigateToHunterMap(game.id, hunterName))
                PlayerRole.GAME_MASTER -> _effects.send(HomeEffect.NavigateToGameMasterMap(game.id))
            }
        }
    }

    private fun onLocationPermissionDenied() {
        _uiState.update { it.copy(isShowingLocationRequired = true) }
    }

    private fun onStartButtonTapped() {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(isShowingLocationRequired = true) }
            return
        }
        _uiState.update {
            it.copy(
                isShowingJoinSheet = true,
                gameCode = "",
                teamName = "",
                joinStep = JoinFlowStep.EnteringCode
            )
        }
    }

    private fun onJoinSheetDismissed() {
        // Cancel any in-flight code validation so a late Firestore response
        // doesn't update state (or navigate) after the sheet is gone.
        validateCodeJob?.cancel()
        validateCodeJob = null
        _uiState.update {
            it.copy(
                isShowingJoinSheet = false,
                gameCode = "",
                teamName = "",
                joinStep = JoinFlowStep.EnteringCode
            )
        }
    }

    private fun onGameCodeChanged(code: String) {
        val normalized = code.trim().uppercase()
        _uiState.update { it.copy(gameCode = normalized) }
        if (normalized.length == AppConstants.GAME_CODE_LENGTH
            && normalized.all { it.isLetter() || it.isDigit() }
        ) {
            // Skip if already validating or already validated this code
            val current = _uiState.value.joinStep
            if (current is JoinFlowStep.Validating) return
            if (current is JoinFlowStep.CodeValidated && current.game.gameCode == normalized) return
            validateCode(normalized)
        } else {
            _uiState.update { it.copy(joinStep = JoinFlowStep.EnteringCode) }
        }
    }

    @Volatile
    private var validateCodeJob: kotlinx.coroutines.Job? = null

    private fun validateCode(code: String) {
        // Drop any stale validation so a fast re-type doesn't race the previous
        // Firestore read and overwrite state out of order.
        validateCodeJob?.cancel()
        _uiState.update { it.copy(joinStep = JoinFlowStep.Validating) }
        val userId = auth.currentUser?.uid ?: ""
        validateCodeJob = viewModelScope.launch {
            try {
                val game = firestoreRepository.findGameByCode(code)
                if (game == null) {
                    _uiState.update { it.copy(joinStep = JoinFlowStep.CodeNotFound) }
                    return@launch
                }
                // Block the chicken from joining their own game as a hunter:
                // they'd end up in both `chickenId` and `hunterIds` and
                // break the map (PP-26 — the chicken may be any designated
                // user, not just the creator).
                if (game.isChicken(userId)) {
                    _uiState.update { it.copy(joinStep = JoinFlowStep.CodeNotFound) }
                    return@launch
                }
                // PP-90: pre-fill teamName from the saved nickname so the
                // user can join in one tap if they're happy with the default.
                val savedNickname = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
                _uiState.update {
                    it.copy(
                        joinStep = JoinFlowStep.CodeValidated(game),
                        teamName = if (it.teamName.isBlank()) savedNickname else it.teamName,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Sheet was dismissed or a newer validation started — don't
                // update state and don't flip to NetworkError.
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(joinStep = JoinFlowStep.NetworkError) }
            }
        }
    }

    private fun onTeamNameChanged(name: String) {
        // Trim trailing whitespace early so validation / storage use the same
        // string the user actually meant. Keep a single leading space so the
        // user can still type "The Foxes" naturally (multiple spaces collapse
        // when they tap submit).
        _uiState.update { it.copy(teamName = name.trimStart()) }
    }

    private fun onJoinAsHunterTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.CodeValidated) return
        _uiState.update { it.copy(joinStep = JoinFlowStep.JoiningWithTeamName(step.game)) }
    }

    /**
     * PP-90: collects the teamName, writes the registration doc keyed by
     * userId, then navigates to the hunter map. Anyone can join at any
     * point — there's no deadline. The registration subcollection is
     * still written so PP-86 GameMaster can pick a chicken from the
     * teamName list.
     */
    private fun onSubmitJoinTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.JoiningWithTeamName) return
        if (!_uiState.value.isTeamNameValid) return
        val userId = auth.currentUser?.uid ?: return
        if (userId.isEmpty()) return
        val game = step.game
        val teamName = _uiState.value.teamName.trim()
        _uiState.update { it.copy(joinStep = JoinFlowStep.SubmittingJoin(game)) }
        viewModelScope.launch {
            try {
                val registration = Registration(userId = userId, teamName = teamName)
                firestoreRepository.createRegistration(game.id, registration)
                analyticsRepository.registrationCompleted()
                _uiState.update {
                    it.copy(
                        isShowingJoinSheet = false,
                        gameCode = "",
                        teamName = "",
                        joinStep = JoinFlowStep.EnteringCode
                    )
                }
                val effect = when (game.gameStatusEnum) {
                    GameStatus.WAITING, GameStatus.IN_PROGRESS -> HomeEffect.NavigateToHunterMap(game.id, teamName)
                    GameStatus.DONE -> HomeEffect.NavigateToGameDone(game.id)
                }
                _effects.send(effect)
            } catch (e: Exception) {
                _uiState.update { it.copy(joinStep = JoinFlowStep.NetworkError) }
            }
        }
    }

    private fun onRulesTapped() {
        _uiState.update { it.copy(isShowingGameRules = true) }
    }

    private fun onRulesDismissed() {
        _uiState.update { it.copy(isShowingGameRules = false) }
    }

    private fun onGameNotFoundDismissed() {
        _uiState.update { it.copy(isShowingGameNotFound = false) }
    }

    private fun onLocationRequiredDismissed() {
        _uiState.update { it.copy(isShowingLocationRequired = false) }
    }

    private fun onCreatePartyTapped(): Boolean {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(isShowingLocationRequired = true) }
            return false
        }
        return true
    }

    /**
     * Debug easter egg: long-press on the Create Party button. Routes
     * to [DebugMapSetupScreen] where the user places start + final
     * pins and picks a radius — the actual game creation happens from
     * that screen's Launch button so timing/seed can be finalized
     * after the user is done dragging pins.
     */
    private fun onCreatePartyLongPressed() {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(isShowingLocationRequired = true) }
            return
        }
        if (auth.currentUser?.uid.isNullOrEmpty()) {
            android.util.Log.e("HomeViewModel", "Debug party: no current user id")
            return
        }
        viewModelScope.launch { _effects.send(HomeEffect.NavigateToDebugMapConfig) }
    }
}
