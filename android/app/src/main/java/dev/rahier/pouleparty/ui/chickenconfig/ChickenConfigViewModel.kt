package dev.rahier.pouleparty.ui.chickenconfig

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class ChickenConfigUiState(
    val game: Game = Game.mock,
    val codeCopied: Boolean = false,
    val showAlert: Boolean = false,
    val alertMessage: String = ""
)

@HiltViewModel
class ChickenConfigViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""

    private val _uiState = MutableStateFlow(
        ChickenConfigUiState(
            game = Game(
                id = gameId,
                name = "",
                numberOfPlayers = 10,
                radiusIntervalUpdate = 5.0,
                initialRadius = 1500.0,
                radiusDeclinePerUpdate = 100.0,
                gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue
            )
        )
    )
    val uiState: StateFlow<ChickenConfigUiState> = _uiState.asStateFlow()

    fun updateStartDate(date: Date) {
        _uiState.value = _uiState.value.copy(game = _uiState.value.game.withStartDate(date))
    }

    fun updateEndDate(date: Date) {
        _uiState.value = _uiState.value.copy(game = _uiState.value.game.withEndDate(date))
    }

    fun updateGameMod(mod: GameMod) {
        _uiState.value = _uiState.value.copy(
            game = _uiState.value.game.copy(gameMod = mod.firestoreValue)
        )
    }

    fun updateRadiusIntervalUpdate(value: Double) {
        _uiState.value = _uiState.value.copy(
            game = _uiState.value.game.copy(radiusIntervalUpdate = value)
        )
    }

    fun updateRadiusDecline(value: Double) {
        _uiState.value = _uiState.value.copy(
            game = _uiState.value.game.copy(radiusDeclinePerUpdate = value)
        )
    }

    fun updateInitialRadius(value: Double) {
        _uiState.value = _uiState.value.copy(
            game = _uiState.value.game.copy(initialRadius = value)
        )
    }

    fun updateGame(game: Game) {
        _uiState.value = _uiState.value.copy(game = game)
    }

    fun onCodeCopied() {
        _uiState.value = _uiState.value.copy(codeCopied = true)
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _uiState.value = _uiState.value.copy(codeCopied = false)
        }
    }

    fun dismissAlert() {
        _uiState.value = _uiState.value.copy(showAlert = false)
    }

    fun startGame(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                firestoreRepository.setConfig(_uiState.value.game)
                onSuccess(_uiState.value.game.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showAlert = true,
                    alertMessage = "Could not create the game. Please check your connection and try again."
                )
            }
        }
    }
}
