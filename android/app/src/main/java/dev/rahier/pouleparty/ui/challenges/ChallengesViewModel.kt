package dev.rahier.pouleparty.ui.challenges

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeCompletion
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.ChallengeType
import dev.rahier.pouleparty.model.SubmissionStatus
import dev.rahier.pouleparty.util.ImageCompression
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengesUiState(
    val selectedTab: ChallengesTab = ChallengesTab.CHALLENGES,
    val challenges: List<Challenge> = emptyList(),
    val completions: List<ChallengeCompletion> = emptyList(),
    val mySubmissions: List<ChallengeSubmission> = emptyList(),
    val hunterIds: List<String> = emptyList(),
    val nicknames: Map<String, String> = emptyMap(),
    val registrations: Map<String, String> = emptyMap(),
    val currentHunterId: String = "",
    val currentTeamName: String = "",
    val pendingLocalIds: Set<String> = emptySet(),
    val submittingIds: Set<String> = emptySet(),
    val photoTargetChallengeId: String? = null,
    val isClosedForSubmissions: Boolean = false,
) {
    val completedIdsForCurrentHunter: Set<String>
        get() = completions.firstOrNull { it.hunterId == currentHunterId }
            ?.validatedChallengeIds
            ?.toSet()
            ?: emptySet()

    val awaitingValidationIds: Set<String>
        get() = mySubmissions.filter { it.statusEnum == SubmissionStatus.PENDING }
            .map { it.challengeId }.toSet()

    val rejectedIds: Set<String>
        get() = mySubmissions.filter { it.statusEnum == SubmissionStatus.REJECTED }
            .map { it.challengeId }.toSet()

    fun status(of: Challenge): ChallengeStatus = when {
        completedIdsForCurrentHunter.contains(of.id) -> ChallengeStatus.VALIDATED
        submittingIds.contains(of.id) -> ChallengeStatus.SUBMITTING
        awaitingValidationIds.contains(of.id) -> ChallengeStatus.AWAITING_VALIDATION
        of.typeEnum == ChallengeType.ONE_SHOT && rejectedIds.contains(of.id) -> ChallengeStatus.REJECTED
        pendingLocalIds.contains(of.id) -> ChallengeStatus.PENDING_LOCAL
        else -> ChallengeStatus.AVAILABLE
    }

    val photoTargetChallenge: Challenge?
        get() = challenges.firstOrNull { it.id == photoTargetChallengeId }

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

data class LeaderboardHunterEntry(
    val hunterId: String,
    val displayName: String,
    val totalPoints: Int,
    val isCurrentHunter: Boolean
)

@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    prefs: SharedPreferences,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChallengesViewModel"
    }

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val hunterId: String = auth.currentUser?.uid ?: ""
    private val pendingStore = PendingChallengeStore(prefs)

    private val _uiState = MutableStateFlow(
        ChallengesUiState(
            currentHunterId = hunterId,
            pendingLocalIds = if (gameId.isNotEmpty()) pendingStore.ids(gameId) else emptySet(),
        )
    )
    val uiState: StateFlow<ChallengesUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChallengesEffect>(Channel.BUFFERED)
    val effects: Flow<ChallengesEffect> = _effects.receiveAsFlow()

    init {
        streamChallenges()
        streamCompletions()
        streamSubmissions()
        loadGameContext()
    }

    fun onIntent(intent: ChallengesIntent) {
        when (intent) {
            is ChallengesIntent.TabSelected -> onTabSelected(intent.tab)
            is ChallengesIntent.MarkAsDoneTapped -> onMarkAsDoneTapped(intent.challenge)
            is ChallengesIntent.SubmitForValidationTapped -> onSubmitForValidationTapped(intent.challenge)
            is ChallengesIntent.PhotoPicked -> onPhotoPicked(intent.challengeId, intent.bytes)
            is ChallengesIntent.PhotoSourceCancelled -> _uiState.update { it.copy(photoTargetChallengeId = null) }
            is ChallengesIntent.UploadErrorDismissed -> Unit
        }
    }

    private fun onTabSelected(tab: ChallengesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    private fun onMarkAsDoneTapped(challenge: Challenge) {
        val state = _uiState.value
        if (state.isClosedForSubmissions) return
        if (state.status(challenge) != ChallengeStatus.AVAILABLE) return
        if (gameId.isNotEmpty()) pendingStore.add(challenge.id, gameId)
        _uiState.update { it.copy(pendingLocalIds = it.pendingLocalIds + challenge.id) }
    }

    fun setClosedForSubmissions(closed: Boolean) {
        _uiState.update { it.copy(isClosedForSubmissions = closed) }
    }

    private fun onSubmitForValidationTapped(challenge: Challenge) {
        val state = _uiState.value
        if (state.isClosedForSubmissions) return
        if (state.status(challenge) != ChallengeStatus.PENDING_LOCAL) return
        _uiState.update { it.copy(photoTargetChallengeId = challenge.id) }
    }

    private fun onPhotoPicked(challengeId: String, bytes: ByteArray) {
        val state = _uiState.value
        val challenge = state.challenges.firstOrNull { it.id == challengeId } ?: return
        _uiState.update {
            it.copy(
                photoTargetChallengeId = null,
                submittingIds = it.submittingIds + challengeId
            )
        }
        viewModelScope.launch {
            try {
                val compressed = ImageCompression.compressJpeg(bytes)
                firestoreRepository.submitChallenge(
                    gameId = gameId,
                    challengeId = challengeId,
                    hunterId = state.currentHunterId,
                    type = challenge.typeEnum,
                    photoBytes = compressed,
                )
                if (gameId.isNotEmpty()) pendingStore.remove(challengeId, gameId)
                _uiState.update {
                    it.copy(
                        submittingIds = it.submittingIds - challengeId,
                        pendingLocalIds = it.pendingLocalIds - challengeId,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit challenge", e)
                _uiState.update { it.copy(submittingIds = it.submittingIds - challengeId) }
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
                _uiState.update { state ->
                    val justCompleted = completions
                        .firstOrNull { it.hunterId == state.currentHunterId }
                        ?.validatedChallengeIds
                        ?.toSet()
                        ?: emptySet()
                    val newPending = state.pendingLocalIds - justCompleted
                    val removed = state.pendingLocalIds - newPending
                    removed.forEach { pendingStore.remove(it, gameId) }
                    state.copy(completions = completions, pendingLocalIds = newPending)
                }
            }
        }
    }

    private fun streamSubmissions() {
        if (gameId.isEmpty() || hunterId.isEmpty()) return
        viewModelScope.launch {
            firestoreRepository.hunterSubmissionsFlow(gameId, hunterId).collect { submissions ->
                _uiState.update { state ->
                    val pendingOrValidated = submissions
                        .filter { it.statusEnum != SubmissionStatus.REJECTED }
                        .map { it.challengeId }
                        .toSet()
                    val newPending = state.pendingLocalIds - pendingOrValidated
                    val removed = state.pendingLocalIds - newPending
                    removed.forEach { pendingStore.remove(it, gameId) }
                    state.copy(mySubmissions = submissions, pendingLocalIds = newPending)
                }
            }
        }
    }

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
