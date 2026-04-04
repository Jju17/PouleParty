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
import dev.rahier.pouleparty.ui.PlayerRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class HomeUiState(
    val isShowingJoinDialog: Boolean = false,
    val isShowingGameRules: Boolean = false,
    val isShowingGameNotFound: Boolean = false,
    val isShowingLocationRequired: Boolean = false,
    val isMusicMuted: Boolean = false,
    val gameCode: String = "",
    val hunterName: String = "",
    val activeGame: Game? = null,
    val activeGameRole: PlayerRole? = null
)

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
            it.copy(isMusicMuted = prefs.getBoolean(AppConstants.PREF_IS_MUSIC_MUTED, false))
        }
        checkForActiveGame()
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

    fun onStartButtonTapped() {
        if (!locationRepository.hasFineLocationPermission()) {
            _uiState.update { it.copy(isShowingLocationRequired = true) }
            return
        }
        _uiState.update { it.copy(isShowingJoinDialog = true) }
    }


    fun onJoinDialogDismissed() {
        _uiState.update { it.copy(isShowingJoinDialog = false, gameCode = "", hunterName = "") }
    }

    fun onGameCodeChanged(code: String) {
        _uiState.update { it.copy(gameCode = code) }
    }

    fun onHunterNameChanged(name: String) {
        _uiState.update { it.copy(hunterName = name) }
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

    /**
     * Join a game by code. Routes based on game status:
     * - waiting / inProgress → onGameFound (join as hunter)
     * - done → onGameDone (spectator victory)
     */
    fun joinGame(onGameFound: (String, String) -> Unit, onGameDone: (String) -> Unit) {
        val code = _uiState.value.gameCode.trim()
        if (code.isEmpty()) return

        val savedNickname = (prefs.getString(AppConstants.PREF_USER_NICKNAME, "") ?: "").trim()
        val hunterName = savedNickname.ifEmpty { "Hunter" }
        _uiState.update { it.copy(isShowingJoinDialog = false, gameCode = "") }

        viewModelScope.launch {
            val game = firestoreRepository.findGameByCode(code)
            if (game == null) {
                _uiState.update { it.copy(isShowingGameNotFound = true) }
                return@launch
            }
            when (game.gameStatusEnum) {
                GameStatus.WAITING, GameStatus.IN_PROGRESS -> onGameFound(game.id, hunterName)
                GameStatus.DONE -> onGameDone(game.id)
            }
        }
    }
}
