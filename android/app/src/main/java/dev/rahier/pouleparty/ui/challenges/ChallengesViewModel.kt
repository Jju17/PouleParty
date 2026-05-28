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
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.ChallengeType
import dev.rahier.pouleparty.model.SubmissionMediaType
import dev.rahier.pouleparty.model.SubmissionStatus
import dev.rahier.pouleparty.ui.gamelogic.ChallengeProgress
import dev.rahier.pouleparty.ui.gamelogic.LevelProgress
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
    val registrations: Map<String, String> = emptyMap(),
    val currentHunterId: String = "",
    val currentTeamName: String = "",
    val submittingIds: Set<String> = emptySet(),
    /** Challenge whose camera capture sheet should currently be open.
     *  Single-tap flow: set on "Doing it" tap, cleared by capture or cancel. */
    val captureTargetChallengeId: String? = null,
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
        else -> ChallengeStatus.AVAILABLE
    }

    val challengesByLevel: List<Pair<Int, List<Challenge>>>
        get() = challenges
            .groupBy { it.level }
            .toSortedMap()
            .map { (level, list) ->
                level to list.sortedWith(
                    compareByDescending<Challenge> { it.points }.thenBy { it.id }
                )
            }

    fun isLevelLocked(level: Int): Boolean = !ChallengeProgress.isLevelUnlocked(
        level = level,
        challenges = challenges,
        validatedChallengeIds = completedIdsForCurrentHunter,
    )

    fun progressForLevel(level: Int): LevelProgress = ChallengeProgress.levelProgress(
        level = level,
        challenges = challenges,
        validatedChallengeIds = completedIdsForCurrentHunter,
    )

    val captureTargetChallenge: Challenge?
        get() = challenges.firstOrNull { it.id == captureTargetChallengeId }

    val leaderboardEntries: List<LeaderboardHunterEntry>
        get() {
            val completionByHunter = completions.associateBy { it.hunterId }
            return hunterIds.map { hunterId ->
                val completion = completionByHunter[hunterId]
                val team = registrations[hunterId]
                    ?: completion?.teamName?.takeIf { it.isNotBlank() }
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
        streamSubmissions()
        loadGameContext()
    }

    fun onIntent(intent: ChallengesIntent) {
        when (intent) {
            is ChallengesIntent.TabSelected -> onTabSelected(intent.tab)
            is ChallengesIntent.DoingItTapped -> onDoingItTapped(intent.challenge)
            is ChallengesIntent.MediaCaptured -> onMediaCaptured(intent.challengeId, intent.bytes, intent.mediaType)
            is ChallengesIntent.CaptureCancelled -> _uiState.update { it.copy(captureTargetChallengeId = null) }
            is ChallengesIntent.UploadErrorDismissed -> Unit
        }
    }

    private fun onTabSelected(tab: ChallengesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setClosedForSubmissions(closed: Boolean) {
        _uiState.update { it.copy(isClosedForSubmissions = closed) }
    }

    private fun onDoingItTapped(challenge: Challenge) {
        val state = _uiState.value
        if (state.isClosedForSubmissions) return
        if (state.isLevelLocked(challenge.level)) return
        if (state.status(challenge) != ChallengeStatus.AVAILABLE) return
        _uiState.update { it.copy(captureTargetChallengeId = challenge.id) }
    }

    private fun onMediaCaptured(challengeId: String, bytes: ByteArray, mediaType: SubmissionMediaType) {
        val state = _uiState.value
        val challenge = state.challenges.firstOrNull { it.id == challengeId } ?: return
        _uiState.update {
            it.copy(
                captureTargetChallengeId = null,
                submittingIds = it.submittingIds + challengeId
            )
        }
        viewModelScope.launch {
            try {
                val payload = if (mediaType == SubmissionMediaType.IMAGE) {
                    ImageCompression.compressJpeg(bytes)
                } else {
                    bytes
                }
                firestoreRepository.submitChallenge(
                    gameId = gameId,
                    challengeId = challengeId,
                    hunterId = state.currentHunterId,
                    type = challenge.typeEnum,
                    mediaBytes = payload,
                    mediaType = mediaType,
                )
                _uiState.update { it.copy(submittingIds = it.submittingIds - challengeId) }
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
                _uiState.update { it.copy(completions = completions) }
            }
        }
    }

    private fun streamSubmissions() {
        if (gameId.isEmpty() || hunterId.isEmpty()) return
        viewModelScope.launch {
            firestoreRepository.hunterSubmissionsFlow(gameId, hunterId).collect { submissions ->
                _uiState.update { it.copy(mySubmissions = submissions) }
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
                val currentTeamName = registrationsByUserId[hunterId] ?: ""
                _uiState.update {
                    it.copy(
                        hunterIds = hunterIds,
                        registrations = registrationsByUserId,
                        currentTeamName = currentTeamName
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load game context", e)
            }
        }
    }
}
