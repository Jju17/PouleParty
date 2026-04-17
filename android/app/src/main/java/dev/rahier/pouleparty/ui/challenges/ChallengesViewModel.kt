package dev.rahier.pouleparty.ui.challenges

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeCompletion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the hunter Challenges / Leaderboard sheet.
 */
data class ChallengesUiState(
    val selectedTab: ChallengesTab = ChallengesTab.CHALLENGES,
    val challenges: List<Challenge> = emptyList(),
    val completions: List<ChallengeCompletion> = emptyList(),
    val hunterIds: List<String> = emptyList(),
    val nicknames: Map<String, String> = emptyMap(),
    val registrations: Map<String, String> = emptyMap(),
    val currentHunterId: String = "",
    val currentTeamName: String = "",
    val pendingChallenge: Challenge? = null,
    val isSubmitting: Boolean = false
) {
    /** Set of challenge ids the current hunter has already validated. */
    val completedIdsForCurrentHunter: Set<String>
        get() = completions.firstOrNull { it.hunterId == currentHunterId }
            ?.completedChallengeIds
            ?.toSet()
            ?: emptySet()

    /** Leaderboard entries for every hunter in the game, sorted by total points descending. */
    val leaderboardEntries: List<LeaderboardHunterEntry>
        get() {
            val completionByHunter = completions.associateBy { it.hunterId }
            return hunterIds.map { hunterId ->
                val completion = completionByHunter[hunterId]
                val team = registrations[hunterId]
                    ?: completion?.teamName?.takeIf { it.isNotBlank() }
                    ?: nicknames[hunterId]
                    ?: "Hunter"
                LeaderboardHunterEntry(
                    hunterId = hunterId,
                    displayName = team,
                    totalPoints = completion?.totalPoints ?: 0,
                    isCurrentHunter = hunterId == currentHunterId
                )
            }.sortedWith(
                compareByDescending<LeaderboardHunterEntry> { it.totalPoints }
                    .thenBy { it.displayName.lowercase() }
            )
        }
}

/** A single hunter row on the challenges leaderboard. */
data class LeaderboardHunterEntry(
    val hunterId: String,
    val displayName: String,
    val totalPoints: Int,
    val isCurrentHunter: Boolean
)

@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChallengesViewModel"
    }

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val hunterId: String = auth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(
        ChallengesUiState(currentHunterId = hunterId)
    )
    val uiState: StateFlow<ChallengesUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChallengesEffect>(Channel.BUFFERED)
    val effects: Flow<ChallengesEffect> = _effects.receiveAsFlow()

    init {
        streamChallenges()
        streamCompletions()
        loadGameContext()
    }

    fun onIntent(intent: ChallengesIntent) {
        when (intent) {
            is ChallengesIntent.TabSelected -> onTabSelected(intent.tab)
            is ChallengesIntent.ValidateTapped -> onValidateTapped(intent.challenge)
            ChallengesIntent.ConfirmValidation -> confirmValidation()
            ChallengesIntent.DismissConfirmation -> dismissConfirmation()
        }
    }

    private fun onTabSelected(tab: ChallengesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    private fun onValidateTapped(challenge: Challenge) {
        // Don't re-open the dialog if the challenge is already completed.
        if (_uiState.value.completedIdsForCurrentHunter.contains(challenge.id)) return
        _uiState.update { it.copy(pendingChallenge = challenge) }
    }

    private fun dismissConfirmation() {
        _uiState.update { it.copy(pendingChallenge = null) }
    }

    private fun confirmValidation() {
        val state = _uiState.value
        val challenge = state.pendingChallenge ?: return
        if (state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                firestoreRepository.markChallengeCompleted(
                    gameId = gameId,
                    hunterId = state.currentHunterId,
                    teamName = state.currentTeamName,
                    challengeId = challenge.id,
                    points = challenge.points
                )
                _uiState.update { it.copy(pendingChallenge = null, isSubmitting = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark challenge completed", e)
                _uiState.update { it.copy(isSubmitting = false) }
                _effects.send(ChallengesEffect.ShowError(e.message ?: "Unknown error"))
            }
        }
    }

    private fun streamChallenges() {
        viewModelScope.launch {
            firestoreRepository.challengesStream().collect { challenges ->
                _uiState.update { it.copy(challenges = challenges) }
            }
        }
    }

    private fun streamCompletions() {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            firestoreRepository.challengeCompletionsStream(gameId).collect { completions ->
                _uiState.update { it.copy(completions = completions) }
            }
        }
    }

    /**
     * Loads per-game context that doesn't need to be a live stream: the game's
     * hunter list, hunter registrations (for team names) and user nicknames
     * (fallback display names).
     */
    private fun loadGameContext() {
        if (gameId.isEmpty()) return
        viewModelScope.launch {
            try {
                val game = firestoreRepository.getConfig(gameId)
                if (game == null) {
                    Log.w(TAG, "loadGameContext: game $gameId not found")
                    return@launch
                }
                val hunterIds = game.hunterIds
                val registrations = firestoreRepository.fetchAllRegistrations(gameId)
                val registrationsByUserId = registrations
                    .filter { it.teamName.isNotBlank() }
                    .associate { it.userId to it.teamName }
                val nicknames = firestoreRepository.fetchNicknames(hunterIds)
                val currentTeamName = registrationsByUserId[hunterId]
                    ?: nicknames[hunterId]
                    ?: ""
                _uiState.update {
                    it.copy(
                        hunterIds = hunterIds,
                        registrations = registrationsByUserId,
                        nicknames = nicknames,
                        currentTeamName = currentTeamName
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load game context", e)
            }
        }
    }
}
