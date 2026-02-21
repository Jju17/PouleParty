package dev.rahier.pouleparty.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.GameStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class SelectionUiState(
    val isShowingPasswordDialog: Boolean = false,
    val isShowingJoinDialog: Boolean = false,
    val isShowingGameRules: Boolean = false,
    val isShowingGameNotFound: Boolean = false,
    val isShowingGameInProgress: Boolean = false,
    val password: String = "",
    val gameCode: String = "",
    val hunterName: String = ""
)

@HiltViewModel
class SelectionViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SelectionUiState())
    val uiState: StateFlow<SelectionUiState> = _uiState.asStateFlow()

    fun onStartButtonTapped() {
        _uiState.value = _uiState.value.copy(isShowingJoinDialog = true)
    }

    fun onPasswordDialogDismissed() {
        _uiState.value = _uiState.value.copy(isShowingPasswordDialog = false, password = "")
    }

    fun onJoinDialogDismissed() {
        _uiState.value = _uiState.value.copy(isShowingJoinDialog = false, gameCode = "", hunterName = "")
    }

    fun onGameCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(gameCode = code)
    }

    fun onHunterNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(hunterName = name)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onIAmLaPouleTapped() {
        _uiState.value = _uiState.value.copy(isShowingPasswordDialog = true)
    }

    fun onRulesTapped() {
        _uiState.value = _uiState.value.copy(isShowingGameRules = true)
    }

    fun onRulesDismissed() {
        _uiState.value = _uiState.value.copy(isShowingGameRules = false)
    }

    fun onGameNotFoundDismissed() {
        _uiState.value = _uiState.value.copy(isShowingGameNotFound = false)
    }

    fun onGameInProgressDismissed() {
        _uiState.value = _uiState.value.copy(isShowingGameInProgress = false)
    }

    /**
     * Validate chicken password — empty string is the correct password (matches iOS).
     * Returns a new gameId if password is correct, null otherwise.
     */
    fun validatePassword(): String? {
        val password = _uiState.value.password
        _uiState.value = _uiState.value.copy(isShowingPasswordDialog = false, password = "")
        return if (password.isEmpty()) UUID.randomUUID().toString() else null
    }

    /**
     * Join a game by code. Routes based on game status:
     * - waiting → onGameFound (join as hunter)
     * - inProgress → show "game in progress" alert
     * - done → onGameDone (spectator victory)
     */
    fun joinGame(onGameFound: (String, String) -> Unit, onGameDone: (String) -> Unit) {
        val code = _uiState.value.gameCode.trim()
        if (code.isEmpty()) return

        val hunterName = _uiState.value.hunterName.trim().ifEmpty { "Hunter" }
        _uiState.value = _uiState.value.copy(isShowingJoinDialog = false, gameCode = "", hunterName = "")

        viewModelScope.launch {
            val game = firestoreRepository.findGameByCode(code)
            if (game == null) {
                _uiState.value = _uiState.value.copy(isShowingGameNotFound = true)
                return@launch
            }
            when (game.gameStatusEnum) {
                GameStatus.WAITING -> onGameFound(game.id, hunterName)
                GameStatus.IN_PROGRESS -> _uiState.value = _uiState.value.copy(isShowingGameInProgress = true)
                GameStatus.DONE -> onGameDone(game.id)
            }
        }
    }
}
