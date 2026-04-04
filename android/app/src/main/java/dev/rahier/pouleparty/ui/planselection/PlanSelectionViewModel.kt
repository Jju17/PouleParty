package dev.rahier.pouleparty.ui.planselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.model.PartyPlansConfig
import dev.rahier.pouleparty.model.PricingModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanSelectionUiState(
    val plansConfig: PartyPlansConfig? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val selectedPlan: PricingModel? = null,
    // Flat
    val numberOfPlayers: Float = 10f,
    // Deposit
    val pricePerHunter: String = "",
    val hasMaxPlayers: Boolean = false,
    val maxPlayers: Float = 20f,
    val showDailyLimitAlert: Boolean = false
) {
    val step: Step get() = if (selectedPlan == null) Step.CHOOSE_PLAN else Step.CONFIGURE_PRICING

    enum class Step { CHOOSE_PLAN, CONFIGURE_PRICING }
}

@HiltViewModel
class PlanSelectionViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanSelectionUiState())
    val uiState: StateFlow<PlanSelectionUiState> = _uiState.asStateFlow()

    init {
        loadPlansConfig()
    }

    fun loadPlansConfig() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val config = firestoreRepository.fetchPartyPlansConfig()
                _uiState.update {
                    it.copy(
                        plansConfig = config,
                        numberOfPlayers = config.flat.minPlayers.toFloat(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    suspend fun canCreateFreeGame(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val count = firestoreRepository.countFreeGamesToday(userId)
        return count < 1
    }

    fun selectPlan(plan: PricingModel) {
        _uiState.update {
            when (plan) {
                PricingModel.FLAT -> it.copy(
                    selectedPlan = plan,
                    numberOfPlayers = (it.plansConfig?.flat?.minPlayers ?: 6).toFloat()
                )
                PricingModel.DEPOSIT -> it.copy(
                    selectedPlan = plan,
                    pricePerHunter = "",
                    hasMaxPlayers = false,
                    maxPlayers = 20f
                )
                else -> it.copy(selectedPlan = plan)
            }
        }
    }

    fun backToPlans() {
        _uiState.update { it.copy(selectedPlan = null) }
    }

    fun updateNumberOfPlayers(value: Float) {
        _uiState.update { it.copy(numberOfPlayers = value) }
    }

    fun updatePricePerHunter(value: String) {
        _uiState.update { it.copy(pricePerHunter = value) }
    }

    fun updateHasMaxPlayers(value: Boolean) {
        _uiState.update { it.copy(hasMaxPlayers = value) }
    }

    fun updateMaxPlayers(value: Float) {
        _uiState.update { it.copy(maxPlayers = value) }
    }

    fun dismissDailyLimitAlert() {
        _uiState.update { it.copy(showDailyLimitAlert = false) }
    }

    fun showDailyLimitAlert() {
        _uiState.update { it.copy(showDailyLimitAlert = true) }
    }

    fun buildNavigationParams(): PricingParams {
        val state = _uiState.value
        val config = state.plansConfig ?: return PricingParams("free", 5, 0, 0)
        val plan = state.selectedPlan ?: PricingModel.FREE
        return when (plan) {
            PricingModel.FREE -> PricingParams(
                pricingModel = "free",
                numberOfPlayers = config.free.maxPlayers,
                pricePerPlayerCents = 0,
                depositAmountCents = 0
            )
            PricingModel.FLAT -> PricingParams(
                pricingModel = "flat",
                numberOfPlayers = state.numberOfPlayers.toInt(),
                pricePerPlayerCents = config.flat.pricePerPlayerCents,
                depositAmountCents = 0
            )
            PricingModel.DEPOSIT -> PricingParams(
                pricingModel = "deposit",
                numberOfPlayers = if (state.hasMaxPlayers) state.maxPlayers.toInt() else 50,
                pricePerPlayerCents = (state.pricePerHunter.toIntOrNull() ?: 0) * 100,
                depositAmountCents = config.deposit.depositAmountCents
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
