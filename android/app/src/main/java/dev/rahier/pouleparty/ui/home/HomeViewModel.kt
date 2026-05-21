package dev.rahier.pouleparty.ui.home

import android.content.SharedPreferences
import android.util.Log
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
    /** PP-52: validation-code text typed by the hunter (or pre-filled
     *  from a deeplink) on the ValidationCodeEntry step. */
    val validationCodeInput: String = "",
    /** PP-52: localized error string when validateRegistrationCode
     *  returns false (wrong code) or fails. */
    val validationCodeError: String? = null,
    /** PP-52: deeplink-supplied validation code awaiting a resolved
     *  game that actually requires one. Cleared once applied. */
    val pendingValidationCode: String? = null,
) {
    val isCodeValid: Boolean
        get() {
            val trimmed = gameCode.trim().uppercase()
            return trimmed.length == AppConstants.GAME_CODE_LENGTH
                && trimmed.all { it.isLetter() || it.isDigit() }
        }

    val isTeamNameValid: Boolean
        get() = teamName.trim().isNotEmpty()

    val isValidationCodeValid: Boolean
        get() {
            val trimmed = validationCodeInput.trim()
            return trimmed.length >= 4 && trimmed.all { it.isLetterOrDigit() }
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val locationRepository: LocationRepository,
    private val analyticsRepository: dev.rahier.pouleparty.data.AnalyticsRepository,
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth,
    // CRIT-8 (audit 2026-05-17): @ApplicationContext for `getString` calls
    // when surfacing user-facing errors from the VM. Stays on the app
    // singleton so it's process-scoped — no Activity leak risk.
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context,
) : ViewModel() {

    init {
        // PP-52 — observe Universal Link / App Link payloads pushed by
        // MainActivity into the singleton `DeeplinkBus`. Any pending
        // validation code is forwarded into the JoinFlow via the
        // regular intent path, then consumed so the same code can't
        // fire twice.
        viewModelScope.launch {
            dev.rahier.pouleparty.data.DeeplinkBus.validationCode.collect { code ->
                if (!code.isNullOrEmpty()) {
                    onIntent(HomeIntent.DeeplinkValidationCodeReceived(code))
                    // Auto-open the join sheet so the prefilled code
                    // is immediately useful even if the visitor wasn't
                    // already on the join flow.
                    _uiState.update { it.copy(isShowingJoinSheet = true) }
                    dev.rahier.pouleparty.data.DeeplinkBus.consume()
                }
            }
        }
    }

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
            HomeIntent.AdminCodeDismissed -> _uiState.update { it.copy(isShowingAdminCodeDialog = false, adminCodeInput = "") }
            HomeIntent.AdminCodeErrorDismissed -> _uiState.update { it.copy(isShowingAdminCodeError = false) }
            is HomeIntent.GameCodeChanged -> onGameCodeChanged(intent.code)
            is HomeIntent.TeamNameChanged -> onTeamNameChanged(intent.name)
            is HomeIntent.AdminCodeChanged -> _uiState.update { it.copy(adminCodeInput = intent.code) }
            HomeIntent.JoinAsGameMasterTapped -> onJoinAsGameMasterTapped()
            is HomeIntent.GameMasterPasswordChanged -> onGameMasterPasswordChanged(intent.code)
            HomeIntent.SubmitGameMasterPasswordTapped -> onSubmitGameMasterPasswordTapped()
            is HomeIntent.ValidationCodeChanged -> onValidationCodeChanged(intent.code)
            HomeIntent.SubmitValidationCodeTapped -> onSubmitValidationCodeTapped()
            is HomeIntent.DeeplinkValidationCodeReceived -> onDeeplinkValidationCodeReceived(intent.code)
            HomeIntent.DeeplinkDismissed -> onDeeplinkDismissed()
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
                    // CRIT-8 (audit 2026-05-17): resolve via @ApplicationContext
                    // so NL / EN users see localized copy instead of French
                    // literals.
                    val msg = if (result.lockedUntilMs != null) {
                        val mins = ((result.lockedUntilMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1L).toInt()
                        appContext.getString(dev.rahier.pouleparty.R.string.join_flow_gm_too_many_attempts, mins)
                    } else {
                        appContext.getString(dev.rahier.pouleparty.R.string.join_flow_gm_wrong_code, result.attemptsRemaining)
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
                    // PP-52: if the user arrived via a deeplink that
                    // carried a validation code, drop it into the
                    // validation field now (only when the game
                    // actually requires one).
                    val applyDeeplink = !game.registrationBatchId.isNullOrEmpty()
                        && !it.pendingValidationCode.isNullOrEmpty()
                        && it.validationCodeInput.isEmpty()
                    it.copy(
                        joinStep = JoinFlowStep.CodeValidated(game),
                        teamName = if (it.teamName.isBlank()) savedNickname else it.teamName,
                        validationCodeInput = if (applyDeeplink) it.pendingValidationCode!! else it.validationCodeInput,
                        pendingValidationCode = if (applyDeeplink) null else it.pendingValidationCode,
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
        // PP-52: paid-batch games route through the validation code
        // step first; everyone else lands directly on the teamName
        // form (PP-90).
        val game = step.game
        val batchId = game.registrationBatchId
        if (!batchId.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    joinStep = JoinFlowStep.ValidationCodeEntry(game),
                    validationCodeError = null,
                )
            }
        } else {
            _uiState.update { it.copy(joinStep = JoinFlowStep.JoiningWithTeamName(game)) }
        }
    }

    // MARK: - PP-52 validation code

    private fun onValidationCodeChanged(code: String) {
        _uiState.update { it.copy(validationCodeInput = code.trimStart()) }
    }

    private fun onSubmitValidationCodeTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.ValidationCodeEntry) return
        if (!_uiState.value.isValidationCodeValid) return
        val game = step.game
        val batchId = game.registrationBatchId
        if (batchId.isNullOrEmpty()) return
        val code = _uiState.value.validationCodeInput.trim().uppercase()
        _uiState.update {
            it.copy(
                joinStep = JoinFlowStep.SubmittingValidationCode(game),
                validationCodeError = null,
            )
        }
        viewModelScope.launch {
            try {
                val ok = firestoreRepository.validateRegistrationCode(batchId, code)
                if (ok) {
                    _uiState.update { it.copy(joinStep = JoinFlowStep.JoiningWithTeamName(game)) }
                } else {
                    _uiState.update {
                        it.copy(
                            joinStep = JoinFlowStep.ValidationCodeEntry(game),
                            validationCodeError = appContext.getString(
                                dev.rahier.pouleparty.R.string.join_flow_validation_code_invalid
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        joinStep = JoinFlowStep.ValidationCodeEntry(game),
                        validationCodeError = e.localizedMessage
                            ?: appContext.getString(dev.rahier.pouleparty.R.string.join_flow_network_error),
                    )
                }
            }
        }
    }

    private fun onDeeplinkValidationCodeReceived(code: String) {
        val tag = "PP-52-Deeplink"
        val normalized = code.trim().uppercase()
        if (normalized.isEmpty()) return
        _uiState.update {
            it.copy(
                validationCodeInput = normalized,
                pendingValidationCode = null,
                validationCodeError = null,
                joinStep = JoinFlowStep.ResolvingDeeplink(normalized),
            )
        }
        viewModelScope.launch {
            try {
                val result = firestoreRepository.lookupGameByValidationCode(normalized)
                when (result) {
                    is FirestoreRepository.ValidationCodeLookupResult.GameReady -> {
                        // Validation done server-side by the lookup —
                        // skip the manual validationCodeEntry step and
                        // drop the user straight on the teamName form.
                        val savedNickname = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
                        _uiState.update {
                            it.copy(
                                joinStep = JoinFlowStep.JoiningWithTeamName(result.game),
                                teamName = if (it.teamName.isBlank()) savedNickname else it.teamName,
                            )
                        }
                    }
                    is FirestoreRepository.ValidationCodeLookupResult.GameNotYetCreated -> {
                        _uiState.update {
                            it.copy(joinStep = JoinFlowStep.DeeplinkGameNotYetReady(normalized))
                        }
                    }
                    FirestoreRepository.ValidationCodeLookupResult.InvalidCode -> {
                        _uiState.update {
                            it.copy(joinStep = JoinFlowStep.DeeplinkInvalidCode)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "[HomeViewModel] lookup error", e)
                _uiState.update { it.copy(joinStep = JoinFlowStep.NetworkError) }
            }
        }
    }

    private fun onDeeplinkDismissed() {
        // Reset to manual code entry but preserve `validationCodeInput`
        // so a hunter who then types the gameCode still gets the
        // validation auto-applied on the ValidationCodeEntry step.
        _uiState.update {
            it.copy(joinStep = JoinFlowStep.EnteringCode)
        }
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

    private fun onCreatePartyLongPressed() {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(isShowingLocationRequired = true) }
            return
        }
        _uiState.update { it.copy(isShowingAdminCodeDialog = true, adminCodeInput = "") }
    }
}
