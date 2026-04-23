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
import dev.rahier.pouleparty.model.PricingModel
import dev.rahier.pouleparty.model.Registration
import dev.rahier.pouleparty.ui.gamelogic.PlayerRole
import dev.rahier.pouleparty.ui.payment.PaymentContext
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
    /**
     * The Stripe PaymentSheet confirmed a deposit but no matching
     * `registrations/{uid}` doc appeared within the verification window —
     * surface an error so the user can contact the organizer instead of
     * showing a ghost "pending" banner indefinitely.
     */
    val isShowingPaymentVerificationFailed: Boolean = false,
    val isMusicMuted: Boolean = false,
    val gameCode: String = "",
    val teamName: String = "",
    val joinStep: JoinFlowStep = JoinFlowStep.EnteringCode,
    val activeGame: Game? = null,
    val activeGameRole: PlayerRole? = null,
    /** Distinguishes "Reprendre" (IN_PROGRESS) from "Prochaine partie"
     *  (UPCOMING) for the Home banner copy + CTA. Null when no active game. */
    val activeGamePhase: dev.rahier.pouleparty.ui.gamelogic.GamePhase? = null,
    val pendingRegistration: PendingRegistration? = null,
    /** Non-null when the hunter Caution payment screen should be shown as an overlay. */
    val paymentContext: PaymentContext? = null,
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
            HomeIntent.JoinSheetDismissed -> onJoinSheetDismissed()
            HomeIntent.ToggleMusic -> toggleMusicMuted()
            HomeIntent.ActiveGameDismissed -> dismissActiveGame()
            HomeIntent.RejoinActiveGameTapped -> rejoinActiveGame()
            HomeIntent.RegisterTapped -> onRegisterTapped()
            HomeIntent.SubmitRegistrationTapped -> onSubmitRegistrationTapped()
            HomeIntent.JoinValidatedGameTapped -> joinValidatedGame()
            HomeIntent.PendingRegistrationJoinTapped -> joinPendingRegistration()
            HomeIntent.RefreshActiveGame -> checkForActiveGame()
            HomeIntent.PaymentVerificationDismissed -> _uiState.update { it.copy(isShowingPaymentVerificationFailed = false) }
            is HomeIntent.GameCodeChanged -> onGameCodeChanged(intent.code)
            is HomeIntent.TeamNameChanged -> onTeamNameChanged(intent.name)
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
            it.copy(
                isMusicMuted = prefs.getBoolean(AppConstants.PREF_IS_MUSIC_MUTED, false),
                pendingRegistration = loadPendingRegistration()
            )
        }
        checkForActiveGame()
        refreshPendingRegistration()
    }

    private fun refreshPendingRegistration() {
        val pending = _uiState.value.pendingRegistration ?: return
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(pending.gameId)
            if (game == null) {
                clearPendingRegistration()
                _uiState.update { it.copy(pendingRegistration = null) }
                return@launch
            }
            val updated = pending.copy(
                gameCode = game.gameCode,
                startMs = game.startDate.time,
                isFinished = game.gameStatusEnum == GameStatus.DONE
            )
            savePendingRegistration(updated)
            _uiState.update { it.copy(pendingRegistration = updated) }
        }
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
            // Refetch to catch games that transitioned to done / pending_payment
            // between the banner being shown and the tap. Falls back to the
            // cached game if the refetch fails (better UX than freezing on a
            // transient network error).
            val fresh = runCatching { firestoreRepository.getConfig(cachedGame.id) }.getOrNull()
            val game = fresh ?: cachedGame
            when (game.gameStatusEnum) {
                GameStatus.PENDING_PAYMENT, GameStatus.PAYMENT_FAILED, GameStatus.DONE -> {
                    _uiState.update { it.copy(isShowingGameNotFound = true) }
                    return@launch
                }
                GameStatus.WAITING, GameStatus.IN_PROGRESS -> Unit
            }
            when (role) {
                PlayerRole.CHICKEN -> _effects.send(HomeEffect.NavigateToChickenMap(game.id))
                PlayerRole.HUNTER -> _effects.send(HomeEffect.NavigateToHunterMap(game.id, hunterName))
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
                // Block hunters from joining their own chicken game: if they
                // do, they'd end up in both `creatorId` and `hunterIds`, which
                // breaks the map (who sees whose position?).
                if (game.creatorId == userId && userId.isNotEmpty()) {
                    _uiState.update { it.copy(joinStep = JoinFlowStep.CodeNotFound) }
                    return@launch
                }
                if (game.registration.required) {
                    val registration = firestoreRepository.findRegistration(game.id, userId)
                    if (game.isRegistrationClosed && registration == null) {
                        _uiState.update { it.copy(joinStep = JoinFlowStep.RegistrationClosed(game)) }
                        return@launch
                    }
                    _uiState.update { it.copy(joinStep = JoinFlowStep.CodeValidated(game, registration != null)) }
                } else {
                    _uiState.update { it.copy(joinStep = JoinFlowStep.CodeValidated(game, true)) }
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

    private fun onRegisterTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.CodeValidated) return
        _uiState.update { it.copy(joinStep = JoinFlowStep.Registering(step.game)) }
    }

    private fun onSubmitRegistrationTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.Registering) return
        if (!_uiState.value.isTeamNameValid) return
        val userId = auth.currentUser?.uid ?: return
        if (userId.isEmpty()) return
        val game = step.game

        // Caution: registration is created server-side by the Stripe webhook after
        // a successful deposit PaymentIntent. Client must never set `paid: true` —
        // rules block it. Route through the PaymentScreen overlay instead.
        if (game.pricingModelEnum == PricingModel.DEPOSIT) {
            _uiState.update { it.copy(paymentContext = PaymentContext.HunterCaution(gameId = game.id)) }
            return
        }

        val teamName = _uiState.value.teamName.trim()
        _uiState.update { it.copy(joinStep = JoinFlowStep.SubmittingRegistration(game)) }
        viewModelScope.launch {
            try {
                val registration = Registration(userId = userId, teamName = teamName, paid = false)
                firestoreRepository.createRegistration(game.id, registration)
                analyticsRepository.registrationCompleted(pricingModel = game.pricing.model)
                val pending = PendingRegistration(
                    gameId = game.id,
                    gameCode = game.gameCode,
                    teamName = teamName,
                    startMs = game.startDate.time
                )
                savePendingRegistration(pending)
                _uiState.update {
                    it.copy(
                        pendingRegistration = pending,
                        isShowingJoinSheet = false,
                        gameCode = "",
                        teamName = "",
                        joinStep = JoinFlowStep.EnteringCode
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(joinStep = JoinFlowStep.NetworkError) }
            }
        }
    }

    fun onHunterPaymentCompleted() {
        val step = _uiState.value.joinStep
        val game = (step as? JoinFlowStep.Registering)?.game ?: return
        val teamName = _uiState.value.teamName.trim()
        analyticsRepository.registrationCompleted(pricingModel = game.pricing.model)
        // Optimistic UI: show the pending banner right away so the user gets
        // feedback, but kick off a verification task to confirm the Stripe
        // webhook actually wrote the registration doc. If the webhook never
        // fires (network / Cloud Tasks failure / rotated secret), clear the
        // banner + surface an error instead of leaving a ghost pending.
        val pending = PendingRegistration(
            gameId = game.id,
            gameCode = game.gameCode,
            teamName = teamName,
            startMs = game.startDate.time
        )
        savePendingRegistration(pending)
        _uiState.update {
            it.copy(
                paymentContext = null,
                pendingRegistration = pending,
                isShowingJoinSheet = false,
                gameCode = "",
                teamName = "",
                joinStep = JoinFlowStep.EnteringCode,
            )
        }
        val userId = auth.currentUser?.uid
        viewModelScope.launch {
            _effects.send(HomeEffect.NavigateToPaymentConfirmed(game.id, "hunter_caution"))
            if (userId.isNullOrEmpty()) return@launch
            val confirmed = verifyHunterRegistrationPaid(game.id, userId)
            if (!confirmed) {
                clearPendingRegistration()
                _uiState.update { it.copy(pendingRegistration = null, isShowingPaymentVerificationFailed = true) }
            }
        }
    }

    /**
     * Polls `findRegistration(gameId, uid)` every `POLL_INTERVAL_MS` until it
     * returns `paid=true` or the `TOTAL_TIMEOUT_MS` budget is exhausted.
     * Returns true iff the Stripe webhook confirmed the deposit. The polling
     * runs as a background coroutine — the caller controls the UI.
     */
    private suspend fun verifyHunterRegistrationPaid(gameId: String, userId: String): Boolean {
        val POLL_INTERVAL_MS = 3_000L
        val TOTAL_TIMEOUT_MS = 30_000L
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < TOTAL_TIMEOUT_MS) {
            val registration = runCatching {
                firestoreRepository.findRegistration(gameId, userId)
            }.getOrNull()
            if (registration != null && registration.paid) {
                return true
            }
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }
        return false
    }

    fun onPaymentCancelled() {
        _uiState.update { it.copy(paymentContext = null) }
    }

    private fun joinValidatedGame() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.CodeValidated) return
        if (step.game.registration.required && !step.alreadyRegistered) return

        // Bail out loudly for payment-limbo games: previously we fell through
        // to `return@launch`, dropping the user back on Home with no feedback
        // while their tap silently did nothing.
        if (step.game.gameStatusEnum == GameStatus.PENDING_PAYMENT ||
            step.game.gameStatusEnum == GameStatus.PAYMENT_FAILED) {
            _uiState.update {
                it.copy(
                    isShowingJoinSheet = false,
                    gameCode = "",
                    teamName = "",
                    joinStep = JoinFlowStep.EnteringCode,
                    isShowingGameNotFound = true,
                )
            }
            return
        }

        val savedNickname = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
        val hunterName = savedNickname.ifEmpty { "Hunter" }
        clearPendingRegistration()
        _uiState.update {
            it.copy(
                isShowingJoinSheet = false,
                gameCode = "",
                teamName = "",
                joinStep = JoinFlowStep.EnteringCode,
                pendingRegistration = null
            )
        }
        viewModelScope.launch {
            val effect = when (step.game.gameStatusEnum) {
                GameStatus.WAITING, GameStatus.IN_PROGRESS -> HomeEffect.NavigateToHunterMap(step.game.id, hunterName)
                GameStatus.DONE -> HomeEffect.NavigateToGameDone(step.game.id)
                GameStatus.PENDING_PAYMENT, GameStatus.PAYMENT_FAILED -> return@launch
            }
            _effects.send(effect)
        }
    }

    private fun joinPendingRegistration() {
        val pending = _uiState.value.pendingRegistration ?: return
        val savedNickname = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
        val hunterName = savedNickname.ifEmpty { "Hunter" }
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(pending.gameId)
            if (game == null) {
                _uiState.update { it.copy(isShowingGameNotFound = true) }
                return@launch
            }
            clearPendingRegistration()
            _uiState.update { it.copy(pendingRegistration = null) }
            val effect = when (game.gameStatusEnum) {
                GameStatus.WAITING, GameStatus.IN_PROGRESS -> HomeEffect.NavigateToHunterMap(game.id, hunterName)
                GameStatus.DONE -> HomeEffect.NavigateToGameDone(game.id)
                GameStatus.PENDING_PAYMENT, GameStatus.PAYMENT_FAILED -> {
                    _uiState.update { it.copy(isShowingGameNotFound = true) }
                    return@launch
                }
            }
            _effects.send(effect)
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

    // ── Pending registration persistence ──────────────────

    private fun loadPendingRegistration(): PendingRegistration? {
        val gameId = prefs.getString(AppConstants.PREF_PENDING_REGISTRATION_GAME_ID, null) ?: return null
        val gameCode = prefs.getString(AppConstants.PREF_PENDING_REGISTRATION_GAME_CODE, null) ?: return null
        val teamName = prefs.getString(AppConstants.PREF_PENDING_REGISTRATION_TEAM_NAME, null) ?: return null
        val startMs = prefs.getLong(AppConstants.PREF_PENDING_REGISTRATION_START_MS, 0L)
        val isFinished = prefs.getBoolean(AppConstants.PREF_PENDING_REGISTRATION_IS_FINISHED, false)
        if (gameId.isEmpty() || gameCode.isEmpty()) return null

        // TTL: if the stored game's start is more than 7 days in the past, the
        // banner is almost certainly a zombie (user never opened the app
        // after the game ended). Drop it rather than display forever.
        val PENDING_TTL_MS = 7L * 24 * 60 * 60 * 1000
        if (startMs > 0 && System.currentTimeMillis() - startMs > PENDING_TTL_MS) {
            clearPendingRegistration()
            return null
        }
        return PendingRegistration(gameId, gameCode, teamName, startMs, isFinished)
    }

    private fun savePendingRegistration(pending: PendingRegistration) {
        prefs.edit()
            .putString(AppConstants.PREF_PENDING_REGISTRATION_GAME_ID, pending.gameId)
            .putString(AppConstants.PREF_PENDING_REGISTRATION_GAME_CODE, pending.gameCode)
            .putString(AppConstants.PREF_PENDING_REGISTRATION_TEAM_NAME, pending.teamName)
            .putLong(AppConstants.PREF_PENDING_REGISTRATION_START_MS, pending.startMs)
            .putBoolean(AppConstants.PREF_PENDING_REGISTRATION_IS_FINISHED, pending.isFinished)
            .apply()
    }

    private fun clearPendingRegistration() {
        prefs.edit()
            .remove(AppConstants.PREF_PENDING_REGISTRATION_GAME_ID)
            .remove(AppConstants.PREF_PENDING_REGISTRATION_GAME_CODE)
            .remove(AppConstants.PREF_PENDING_REGISTRATION_TEAM_NAME)
            .remove(AppConstants.PREF_PENDING_REGISTRATION_START_MS)
            .remove(AppConstants.PREF_PENDING_REGISTRATION_IS_FINISHED)
            .apply()
    }
}
