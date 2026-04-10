package dev.rahier.pouleparty.ui.victory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.Registration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VictoryUiState(
    val game: Game = Game.mock,
    val hunterId: String = "",
    val hunterName: String = "",
    val isChicken: Boolean = false,
    val registrations: List<Registration> = emptyList()
)

@HiltViewModel
class VictoryViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val hunterName: String = savedStateHandle["hunterName"] ?: "Hunter"
    private val hunterId: String = savedStateHandle["hunterId"] ?: ""
    // Whether the current user is the chicken in this game.
    // Passed as a navigation argument from the screen of origin (ChickenMap = true,
    // HunterMap / Home spectator = false). Future-proof if chicken != creator.
    private val isChicken: Boolean = savedStateHandle["isChicken"] ?: false

    private val _uiState = MutableStateFlow(
        VictoryUiState(
            hunterId = hunterId,
            hunterName = hunterName,
            isChicken = isChicken
        )
    )
    val uiState: StateFlow<VictoryUiState> = _uiState.asStateFlow()

    init {
        loadInitialGame()
        loadRegistrations()
        startGameStream()
    }

    /** Fetch the game once immediately so winners are available right away */
    private fun loadInitialGame() {
        viewModelScope.launch {
            val game = firestoreRepository.getConfig(gameId) ?: return@launch
            _uiState.update { it.copy(game = game) }
        }
    }

    private fun loadRegistrations() {
        viewModelScope.launch {
            val registrations = firestoreRepository.fetchAllRegistrations(gameId)
            _uiState.update { it.copy(registrations = registrations) }
        }
    }

    private fun startGameStream() {
        viewModelScope.launch {
            firestoreRepository.gameConfigFlow(gameId).collect { game ->
                if (game != null) {
                    _uiState.update { it.copy(game = game) }
                }
            }
        }
    }
}

// MARK: - Leaderboard helpers

data class LeaderboardEntry(
    val id: String,
    val displayName: String,
    val teamName: String?,
    val foundTimestampMs: Long?,
    val isCurrentUser: Boolean
) {
    val hasFound: Boolean get() = foundTimestampMs != null
}

fun buildLeaderboardEntries(
    game: Game,
    registrations: List<Registration>,
    currentUserId: String
): List<LeaderboardEntry> {
    val registrationByUserId = registrations.associateBy { it.userId }
    val winnerById = game.winners.associateBy { it.hunterId }
    val allHunterIds = (game.hunterIds + game.winners.map { it.hunterId } + registrations.map { it.userId }).toSet()

    return allHunterIds.map { hunterId ->
        val registration = registrationByUserId[hunterId]
        val winner = winnerById[hunterId]
        val teamName = registration?.teamName
        val displayName = teamName ?: winner?.hunterName ?: "Hunter"
        LeaderboardEntry(
            id = hunterId,
            displayName = displayName,
            teamName = teamName,
            foundTimestampMs = winner?.timestamp?.toDate()?.time,
            isCurrentUser = hunterId == currentUserId
        )
    }
}
