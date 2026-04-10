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
import dev.rahier.pouleparty.ui.PlayerRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val pendingRegistration: PendingRegistration? = null
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
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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

    fun refreshActiveGame() {
        checkForActiveGame()
    }

    private fun checkForActiveGame() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = firestoreRepository.findActiveGame(userId)
            if (result != null) {
                _uiState.update { it.copy(activeGame = result.first, activeGameRole = result.second) }
            } else {
                _uiState.update { it.copy(activeGame = null, activeGameRole = null) }
            }
        }
    }

    fun toggleMusicMuted() {
        val newValue = !_uiState.value.isMusicMuted
        _uiState.update { it.copy(isMusicMuted = newValue) }
        prefs.edit().putBoolean(AppConstants.PREF_IS_MUSIC_MUTED, newValue).apply()
    }

    fun dismissActiveGame() {
        _uiState.update { it.copy(activeGame = null, activeGameRole = null) }
    }

    fun rejoinGame(
        onRejoinAsChicken: (String) -> Unit,
        onRejoinAsHunter: (String, String) -> Unit
    ) {
        val game = _uiState.value.activeGame ?: return
        val role = _uiState.value.activeGameRole ?: return
        _uiState.update { it.copy(activeGame = null, activeGameRole = null) }
        val savedNickname = (prefs.getString(AppConstants.PREF_USER_NICKNAME, "") ?: "").trim()
        val hunterName = savedNickname.ifEmpty { "Hunter" }
        when (role) {
            PlayerRole.CHICKEN -> onRejoinAsChicken(game.id)
            PlayerRole.HUNTER -> onRejoinAsHunter(game.id, hunterName)
        }
    }

    fun hasLocationPermission(): Boolean = locationRepository.hasFineLocationPermission()

    fun onLocationPermissionDenied() {
        _uiState.update { it.copy(isShowingLocationRequired = true) }
    }

    fun onStartButtonTapped() {
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

    fun onJoinSheetDismissed() {
        _uiState.update {
            it.copy(
                isShowingJoinSheet = false,
                gameCode = "",
                teamName = "",
                joinStep = JoinFlowStep.EnteringCode
            )
        }
    }

    fun onGameCodeChanged(code: String) {
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

    private fun validateCode(code: String) {
        _uiState.update { it.copy(joinStep = JoinFlowStep.Validating) }
        val userId = auth.currentUser?.uid ?: ""
        viewModelScope.launch {
            try {
                val game = firestoreRepository.findGameByCode(code)
                if (game == null) {
                    _uiState.update { it.copy(joinStep = JoinFlowStep.CodeNotFound) }
                    return@launch
                }
                val alreadyRegistered = if (game.requiresRegistration) {
                    firestoreRepository.findRegistration(game.id, userId) != null
                } else {
                    true
                }
                _uiState.update { it.copy(joinStep = JoinFlowStep.CodeValidated(game, alreadyRegistered)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(joinStep = JoinFlowStep.NetworkError) }
            }
        }
    }

    fun onTeamNameChanged(name: String) {
        _uiState.update { it.copy(teamName = name) }
    }

    fun onRegisterTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.CodeValidated) return
        _uiState.update { it.copy(joinStep = JoinFlowStep.Registering(step.game)) }
    }

    fun onSubmitRegistrationTapped() {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.Registering) return
        if (!_uiState.value.isTeamNameValid) return
        val userId = auth.currentUser?.uid ?: return
        if (userId.isEmpty()) return
        val teamName = _uiState.value.teamName.trim()
        val game = step.game
        _uiState.update { it.copy(joinStep = JoinFlowStep.SubmittingRegistration(game)) }
        viewModelScope.launch {
            try {
                val registration = Registration(userId = userId, teamName = teamName, paid = false)
                firestoreRepository.createRegistration(game.id, registration)
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

    fun onJoinTapped(onGameFound: (String, String) -> Unit, onGameDone: (String) -> Unit) {
        val step = _uiState.value.joinStep
        if (step !is JoinFlowStep.CodeValidated) return
        if (step.game.requiresRegistration && !step.alreadyRegistered) return
        val savedNickname = (prefs.getString(AppConstants.PREF_USER_NICKNAME, "") ?: "").trim()
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
        when (step.game.gameStatusEnum) {
            GameStatus.WAITING, GameStatus.IN_PROGRESS -> onGameFound(step.game.id, hunterName)
            GameStatus.DONE -> onGameDone(step.game.id)
        }
    }

    fun onPendingRegistrationJoinTapped(
        onGameFound: (String, String) -> Unit,
        onGameDone: (String) -> Unit
    ) {
        val pending = _uiState.value.pendingRegistration ?: return
        val savedNickname = (prefs.getString(AppConstants.PREF_USER_NICKNAME, "") ?: "").trim()
        val hunterName = savedNickname.ifEmpty { "Hunter" }
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(pending.gameId)
            if (game == null) {
                _uiState.update { it.copy(isShowingGameNotFound = true) }
                return@launch
            }
            clearPendingRegistration()
            _uiState.update { it.copy(pendingRegistration = null) }
            when (game.gameStatusEnum) {
                GameStatus.WAITING, GameStatus.IN_PROGRESS -> onGameFound(game.id, hunterName)
                GameStatus.DONE -> onGameDone(game.id)
            }
        }
    }

    fun onRulesTapped() {
        _uiState.update { it.copy(isShowingGameRules = true) }
    }

    fun onRulesDismissed() {
        _uiState.update { it.copy(isShowingGameRules = false) }
    }

    fun onGameNotFoundDismissed() {
        _uiState.update { it.copy(isShowingGameNotFound = false) }
    }

    fun onLocationRequiredDismissed() {
        _uiState.update { it.copy(isShowingLocationRequired = false) }
    }

    fun onCreatePartyTapped(): Boolean {
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
