package dev.rahier.pouleparty.ui.payment

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.StripeRepository
import dev.rahier.pouleparty.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives the Stripe PaymentSheet for creator Forfait and hunter Caution flows.
 *
 * Uses `AssistedInject` because the context (CreatorForfait with a game config,
 * or HunterCaution with a gameId) is instance-scoped.
 */
@HiltViewModel(assistedFactory = PaymentViewModel.Factory::class)
class PaymentViewModel @AssistedInject constructor(
    @Assisted private val context: PaymentContext,
    private val stripeRepository: StripeRepository,
    private val firestoreRepository: FirestoreRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(context: PaymentContext): PaymentViewModel
    }

    data class UiState(
        val context: PaymentContext,
        val promoCodeInput: String = "",
        val validatedPromoCodeId: String? = null,
        val validatedDiscountLabel: String? = null,
        val freeOverride: Boolean = false,
        val promoValidating: Boolean = false,
        val promoError: String? = null,
        val preparing: Boolean = false,
        val prepareError: String? = null,
        val paymentConfig: PaymentSheetConfig? = null,
        val completionError: String? = null,
        val outcome: Outcome? = null,
    ) {
        val showsPromoCode: Boolean get() = context is PaymentContext.CreatorForfait
    }

    data class PaymentSheetConfig(
        val clientSecret: String,
        val ephemeralKeySecret: String,
        val customerId: String,
        val amountCents: Int,
        /** Non-null only on creator flow — server pre-created the game doc. */
        val gameId: String? = null,
    )

    /** Terminal outcomes that the screen consumes to navigate back / fire callbacks. */
    sealed interface Outcome {
        data class CreatorPaid(val gameId: String) : Outcome
        data object HunterPaid : Outcome
        data object Cancelled : Outcome
    }

    private val _state = MutableStateFlow(UiState(context = context))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onPromoCodeInputChanged(value: String) {
        _state.update { it.copy(promoCodeInput = value, promoError = null) }
    }

    fun onApplyPromoTapped() {
        val ctx = _state.value.context
        if (ctx !is PaymentContext.CreatorForfait) return
        val code = _state.value.promoCodeInput.trim()
        if (code.isEmpty()) return
        _state.update { it.copy(promoValidating = true, promoError = null) }
        viewModelScope.launch {
            runCatching { stripeRepository.validatePromoCode(code) }
                .onSuccess { result ->
                    if (!result.valid) {
                        _state.update { it.copy(promoValidating = false, promoError = "Code invalide ou expiré") }
                        return@onSuccess
                    }
                    val label = when {
                        result.percentOff != null -> "-${result.percentOff.toInt()}%"
                        result.amountOffCents != null -> "-${result.amountOffCents / 100}€"
                        else -> "Appliqué"
                    }
                    _state.update {
                        it.copy(
                            promoValidating = false,
                            validatedPromoCodeId = result.promoCodeId,
                            validatedDiscountLabel = label,
                            freeOverride = result.freeOverride,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(promoValidating = false, promoError = err.message) }
                }
        }
    }

    fun onClearPromoTapped() {
        _state.update {
            it.copy(
                promoCodeInput = "",
                validatedPromoCodeId = null,
                validatedDiscountLabel = null,
                freeOverride = false,
                promoError = null,
            )
        }
    }

    fun onPayTapped() {
        val current = _state.value
        _state.update { it.copy(preparing = true, prepareError = null) }

        viewModelScope.launch {
            // Cap the prepare step so a stalled Stripe call doesn't leave the
            // user staring at a spinner forever. 30 s is generous; in practice
            // the call returns in < 2 s.
            val timedOut = withTimeoutOrNull(PAY_PREPARE_TIMEOUT_MS) {
                when (val ctx = current.context) {
                    is PaymentContext.CreatorForfait -> {
                        val promoId = current.validatedPromoCodeId
                        if (current.freeOverride && promoId != null) {
                            runCatching { stripeRepository.redeemFreeCreation(ctx.gameConfig, promoId) }
                                .onSuccess { gameId ->
                                    _state.update { it.copy(preparing = false, outcome = Outcome.CreatorPaid(gameId)) }
                                }
                                .onFailure { err ->
                                    _state.update { it.copy(preparing = false, prepareError = err.message) }
                                }
                        } else {
                            runCatching { stripeRepository.createCreatorPaymentSheet(ctx.gameConfig, promoId) }
                                .onSuccess { params ->
                                    _state.update {
                                        it.copy(
                                            preparing = false,
                                            paymentConfig = PaymentSheetConfig(
                                                clientSecret = params.paymentIntentClientSecret,
                                                ephemeralKeySecret = params.ephemeralKeySecret,
                                                customerId = params.customerId,
                                                amountCents = params.amountCents,
                                                gameId = params.gameId,
                                            ),
                                        )
                                    }
                                }
                                .onFailure { err ->
                                    _state.update { it.copy(preparing = false, prepareError = err.message) }
                                }
                        }
                    }
                    is PaymentContext.HunterCaution -> {
                        runCatching { stripeRepository.createHunterPaymentSheet(ctx.gameId) }
                            .onSuccess { params ->
                                _state.update {
                                    it.copy(
                                        preparing = false,
                                        paymentConfig = PaymentSheetConfig(
                                            clientSecret = params.paymentIntentClientSecret,
                                            ephemeralKeySecret = params.ephemeralKeySecret,
                                            customerId = params.customerId,
                                            amountCents = params.amountCents,
                                        ),
                                    )
                                }
                            }
                            .onFailure { err ->
                                _state.update { it.copy(preparing = false, prepareError = err.message) }
                            }
                    }
                }
                Unit
            }
            if (timedOut == null) {
                _state.update { it.copy(preparing = false, prepareError = "Stripe took too long, please retry") }
            }
        }
    }

    companion object {
        private const val PAY_PREPARE_TIMEOUT_MS = 30_000L
    }

    /** Called by the screen once the PaymentSheet finishes (completed / cancelled / failed). */
    fun onSheetCompleted() {
        val config = _state.value.paymentConfig ?: return
        val outcome = when (val ctx = _state.value.context) {
            is PaymentContext.CreatorForfait -> config.gameId?.let { Outcome.CreatorPaid(it) }
                ?: run {
                    _state.update { it.copy(completionError = "Paiement confirmé mais ID de partie manquant.") }
                    return
                }
            is PaymentContext.HunterCaution -> Outcome.HunterPaid
        }
        _state.update { it.copy(outcome = outcome) }
    }

    fun onSheetCancelled() {
        // Creator Forfait pre-creates the game doc in `pending_payment` before
        // the sheet opens. On cancel (including swipe-down), delete the orphan
        // so it doesn't linger in My Games as a "Paiement" ghost.
        deleteOrphanCreatorGame(_state.value.paymentConfig?.gameId)
        _state.update { it.copy(paymentConfig = null) }
    }

    fun onSheetFailed(message: String?) {
        deleteOrphanCreatorGame(_state.value.paymentConfig?.gameId)
        _state.update { it.copy(paymentConfig = null, completionError = message) }
    }

    private fun deleteOrphanCreatorGame(gameId: String?) {
        if (gameId.isNullOrEmpty()) return
        if (_state.value.context !is PaymentContext.CreatorForfait) return
        viewModelScope.launch {
            runCatching { firestoreRepository.deleteConfig(gameId) }
                .onFailure { Log.w("PaymentViewModel", "Failed to delete orphan pending_payment game $gameId", it) }
        }
    }

    fun onDismissError() {
        _state.update { it.copy(completionError = null) }
    }

    fun onBackRequested() {
        _state.update { it.copy(outcome = Outcome.Cancelled) }
    }
}

sealed interface PaymentContext {
    data class CreatorForfait(val gameConfig: Game) : PaymentContext
    data class HunterCaution(val gameId: String) : PaymentContext
}
