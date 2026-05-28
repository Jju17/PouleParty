package dev.rahier.pouleparty.ui.validation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.ChallengeSubmission
import dev.rahier.pouleparty.model.Registration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ValidationQueueUiState(
    val submissions: List<ChallengeSubmission> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
    val registrations: List<Registration> = emptyList(),
    val selected: ChallengeSubmission? = null,
    val busyIds: Set<String> = emptySet(),
    val error: String? = null,
) {
    fun challenge(of: ChallengeSubmission): Challenge? =
        challenges.firstOrNull { it.id == of.challengeId }

    fun teamName(forHunterId: String): String {
        val reg = registrations.firstOrNull { it.userId == forHunterId }
        if (reg != null && reg.teamName.isNotBlank()) return reg.teamName
        return "Hunter"
    }
}

@HiltViewModel
class ValidationQueueViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "ValidationQueueVM"
    }

    private val gameId: String = savedStateHandle["gameId"] ?: ""

    private val _uiState = MutableStateFlow(ValidationQueueUiState())
    val uiState: StateFlow<ValidationQueueUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ValidationQueueEffect>(Channel.BUFFERED)
    val effects: Flow<ValidationQueueEffect> = _effects.receiveAsFlow()

    init {
        if (gameId.isNotEmpty()) {
            streamSubmissions()
            streamChallenges()
            streamRegistrations()
        }
    }

    fun onIntent(intent: ValidationQueueIntent) {
        when (intent) {
            ValidationQueueIntent.CloseTapped -> viewModelScope.launch {
                _effects.send(ValidationQueueEffect.Dismiss)
            }
            is ValidationQueueIntent.SubmissionTapped ->
                _uiState.update { it.copy(selected = intent.submission) }
            ValidationQueueIntent.DetailDismissed ->
                _uiState.update { it.copy(selected = null) }
            ValidationQueueIntent.ErrorDismissed ->
                _uiState.update { it.copy(error = null) }
            is ValidationQueueIntent.ValidateTapped ->
                validate(intent.submission, accept = true)
            is ValidationQueueIntent.RejectTapped ->
                validate(intent.submission, accept = false)
        }
    }

    private fun validate(submission: ChallengeSubmission, accept: Boolean) {
        val id = submission.id
        if (id.isEmpty()) return
        if (_uiState.value.busyIds.contains(id)) return
        _uiState.update { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            try {
                firestoreRepository.validateChallengeSubmission(gameId, id, accept)
                _uiState.update {
                    it.copy(
                        busyIds = it.busyIds - id,
                        selected = null,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "validate failed", e)
                _uiState.update {
                    it.copy(
                        busyIds = it.busyIds - id,
                        error = e.message ?: "Validation failed",
                    )
                }
            }
        }
    }

    private fun streamSubmissions() {
        viewModelScope.launch {
            firestoreRepository.pendingSubmissionsFlow(gameId).collect { subs ->
                _uiState.update { state ->
                    val stillVisible = state.selected?.let { sel ->
                        if (subs.any { it.id == sel.id }) sel else null
                    }
                    state.copy(submissions = subs, selected = stillVisible)
                }
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

    private fun streamRegistrations() {
        viewModelScope.launch {
            firestoreRepository.registrationsFlow(gameId).collect { regs ->
                _uiState.update { it.copy(registrations = regs) }
            }
        }
    }

}
