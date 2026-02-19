package dev.rahier.pouleparty.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
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
    val password: String = "",
    val gameCode: String = ""
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
        _uiState.value = _uiState.value.copy(isShowingJoinDialog = false, gameCode = "")
    }

    fun onGameCodeChanged(code: String) {
        _uiState.value = _uiState.value.copy(gameCode = code)
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
     * Join a game by code. Returns the gameId if found, or shows error.
     */
    fun joinGame(onGameFound: (String) -> Unit) {
        val code = _uiState.value.gameCode.trim()
        if (code.isEmpty()) return

        _uiState.value = _uiState.value.copy(isShowingJoinDialog = false, gameCode = "")

        viewModelScope.launch {
            val game = firestoreRepository.findGameByCode(code)
            if (game != null && game.endDate.after(Date())) {
                onGameFound(game.id)
            } else {
                _uiState.value = _uiState.value.copy(isShowingGameNotFound = true)
            }
        }
    }
}
