package dev.rahier.pouleparty.ui.victory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VictoryUiState(
    val game: Game = Game.mock,
    val hunterId: String = "",
    val hunterName: String = ""
)

@HiltViewModel
class VictoryViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val hunterName: String = savedStateHandle["hunterName"] ?: "Hunter"
    private val hunterId: String = savedStateHandle["hunterId"] ?: ""

    private val _uiState = MutableStateFlow(
        VictoryUiState(
            hunterId = hunterId,
            hunterName = hunterName
        )
    )
    val uiState: StateFlow<VictoryUiState> = _uiState.asStateFlow()

    init {
        loadInitialGame()
        startGameStream()
    }

    /** Fetch the game once immediately so winners are available right away */
    private fun loadInitialGame() {
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
            _uiState.value = _uiState.value.copy(game = game)
        }
    }

    private fun startGameStream() {
        viewModelScope.launch {
            firestoreRepository.gameConfigFlow(gameId).collect { game ->
                if (game != null) {
                    _uiState.value = _uiState.value.copy(game = game)
                }
            }
        }
    }
}
