package dev.rahier.pouleparty.ui.planselection

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.PricingModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PlanSelectionUiState(
    val selectedPlan: PricingModel? = null,
    val numberOfPlayers: Float = 10f,
    val pricePerPlayer: String = "",
    val depositAmount: String = "",
    val showDailyLimitAlert: Boolean = false
) {
    val step: Step get() = if (selectedPlan == null) Step.CHOOSE_PLAN else Step.CONFIGURE_PRICING

    val flatPricePerPlayer: Int get() {
        val n = numberOfPlayers.toInt()
        return when {
            n <= 10 -> 3
            n <= 20 -> 2
            else -> 1
        }
    }

    val flatTotal: Int get() = numberOfPlayers.toInt() * flatPricePerPlayer

    enum class Step { CHOOSE_PLAN, CONFIGURE_PRICING }
}

@HiltViewModel
class PlanSelectionViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanSelectionUiState())
    val uiState: StateFlow<PlanSelectionUiState> = _uiState.asStateFlow()

    suspend fun canCreateFreeGame(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val count = firestoreRepository.countFreeGamesToday(userId)
        return count < 1
    }

    fun selectPlan(plan: PricingModel) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    fun backToPlans() {
        _uiState.update { it.copy(selectedPlan = null) }
    }

    fun updateNumberOfPlayers(value: Float) {
        _uiState.update { it.copy(numberOfPlayers = value) }
    }

    fun updatePricePerPlayer(value: String) {
        _uiState.update { it.copy(pricePerPlayer = value) }
    }

    fun updateDepositAmount(value: String) {
        _uiState.update { it.copy(depositAmount = value) }
    }

    fun dismissDailyLimitAlert() {
        _uiState.update { it.copy(showDailyLimitAlert = false) }
    }

    fun showDailyLimitAlert() {
        _uiState.update { it.copy(showDailyLimitAlert = true) }
    }

    fun buildNavigationParams(): PricingParams {
        val state = _uiState.value
        val plan = state.selectedPlan ?: PricingModel.FREE
        return when (plan) {
            PricingModel.FREE -> PricingParams(
                pricingModel = "free",
                numberOfPlayers = 5,
                pricePerPlayerCents = 0,
                depositAmountCents = 0
            )
            PricingModel.FLAT -> PricingParams(
                pricingModel = "flat",
                numberOfPlayers = state.numberOfPlayers.toInt(),
                pricePerPlayerCents = state.flatPricePerPlayer * 100,
                depositAmountCents = 0
            )
            PricingModel.DEPOSIT -> PricingParams(
                pricingModel = "deposit",
                numberOfPlayers = 50,
                pricePerPlayerCents = (state.pricePerPlayer.toIntOrNull() ?: 0) * 100,
                depositAmountCents = (state.depositAmount.toIntOrNull() ?: 0) * 100
            )
        }
    }
}

data class PricingParams(
    val pricingModel: String,
    val numberOfPlayers: Int,
    val pricePerPlayerCents: Int,
    val depositAmountCents: Int
)
