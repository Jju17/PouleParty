package dev.rahier.pouleparty.ui.paymentconfirmation

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which paid flow just completed. Shapes the title / subtitle / share copy. */
enum class ConfirmationKind {
    CREATOR_FORFAIT,
    HUNTER_CAUTION;

    companion object {
        fun fromArg(arg: String): ConfirmationKind = when (arg) {
            "creator_forfait" -> CREATOR_FORFAIT
            "hunter_caution" -> HUNTER_CAUTION
            else -> CREATOR_FORFAIT
        }
    }
}

data class PaymentConfirmationUiState(
    val game: Game? = null,
    val kind: ConfirmationKind = ConfirmationKind.CREATOR_FORFAIT,
    val nowMs: Long = System.currentTimeMillis(),
    val loadFailed: Boolean = false
)

/**
 * Surfaces the paid game to the user right after a successful PaymentSheet or
 * 100%-off promo redeem. Streams [Game] updates so the UI reflects the
 * `pending_payment` → `waiting` transition as soon as the Stripe webhook fires.
 */
@HiltViewModel
class PaymentConfirmationViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gameId: String = savedStateHandle["gameId"] ?: ""
    private val kind: ConfirmationKind = ConfirmationKind.fromArg(
        savedStateHandle["kind"] ?: "creator_forfait"
    )

    private val _uiState = MutableStateFlow(PaymentConfirmationUiState(kind = kind))
    val uiState: StateFlow<PaymentConfirmationUiState> = _uiState.asStateFlow()

    init {
        loadInitialGame()
        streamGame()
        startClock()
    }

    private fun loadInitialGame() {
        viewModelScope.launch {
            val game = runCatching { firestoreRepository.getConfig(gameId) }.getOrNull()
            if (game != null) {
                _uiState.update { it.copy(game = game) }
            } else {
                // Initial fetch may fail briefly while the webhook flips status
                // or Firestore indexes propagate. The stream will take over.
                _uiState.update { it.copy(loadFailed = true) }
            }
        }
    }

    private fun streamGame() {
        viewModelScope.launch {
            runCatching {
                firestoreRepository.gameConfigFlow(gameId).collect { game ->
                    if (game != null) {
                        _uiState.update { it.copy(game = game, loadFailed = false) }
                    }
                }
            }.onFailure { error ->
                Log.e("PaymentConfirmationVM", "gameConfigFlow failed", error)
            }
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(nowMs = System.currentTimeMillis()) }
                delay(1_000)
            }
        }
    }
}
