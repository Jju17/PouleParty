package dev.rahier.pouleparty.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import dev.rahier.pouleparty.BuildConfig

@Composable
fun PaymentScreen(
    context: PaymentContext,
    onCreatorPaid: (gameId: String) -> Unit,
    onHunterPaid: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel<PaymentViewModel, PaymentViewModel.Factory>(
        key = when (context) {
            is PaymentContext.CreatorForfait -> "creator_${context.gameConfig.id}"
            is PaymentContext.HunterCaution -> "hunter_${context.gameId}"
        }
    ) { factory -> factory.create(context) },
) {
    val state by viewModel.state.collectAsState()

    val paymentSheet = rememberPaymentSheet { result ->
        when (result) {
            PaymentSheetResult.Completed -> viewModel.onSheetCompleted()
            PaymentSheetResult.Canceled -> viewModel.onSheetCancelled()
            is PaymentSheetResult.Failed -> viewModel.onSheetFailed(result.error.message)
        }
    }

    // Present sheet when the VM exposes a fresh config.
    LaunchedEffect(state.paymentConfig) {
        val config = state.paymentConfig ?: return@LaunchedEffect
        // Google Pay environment is derived from the publishable key: pk_test_* → Test,
        // pk_live_* → Production. Same key pattern drives our staging/production flavors.
        val googlePayEnv = if (BuildConfig.STRIPE_PUBLISHABLE_KEY.startsWith("pk_test_")) {
            PaymentSheet.GooglePayConfiguration.Environment.Test
        } else {
            PaymentSheet.GooglePayConfiguration.Environment.Production
        }
        val sheetConfig = PaymentSheet.Configuration.Builder("Poule Party")
            .customer(
                PaymentSheet.CustomerConfiguration(
                    id = config.customerId,
                    ephemeralKeySecret = config.ephemeralKeySecret,
                ),
            )
            .googlePay(
                PaymentSheet.GooglePayConfiguration(
                    environment = googlePayEnv,
                    countryCode = "BE",
                    currencyCode = "EUR",
                ),
            )
            // Force the Name field to appear — Bancontact needs `billing_details[name]`
            // and Stripe's `Automatic` mode was not surfacing the field in some
            // Customer scenarios → the user hit "name required" with no UI to fill.
            .billingDetailsCollectionConfiguration(
                PaymentSheet.BillingDetailsCollectionConfiguration(
                    name = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Always,
                ),
            )
            .allowsDelayedPaymentMethods(false)
            .build()
        paymentSheet.presentWithPaymentIntent(config.clientSecret, sheetConfig)
    }

    // Propagate terminal outcomes to navigation callbacks.
    LaunchedEffect(state.outcome) {
        when (val outcome = state.outcome) {
            is PaymentViewModel.Outcome.CreatorPaid -> onCreatorPaid(outcome.gameId)
            PaymentViewModel.Outcome.HunterPaid -> onHunterPaid()
            PaymentViewModel.Outcome.Cancelled -> onCancelled()
            null -> Unit
        }
    }

    if (state.completionError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissError() },
            title = { Text("Paiement échoué") },
            text = { Text(state.completionError!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDismissError() }) { Text("OK") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PaymentHeader(context = state.context)

        if (state.showsPromoCode) {
            PromoCodeSection(
                code = state.promoCodeInput,
                onCodeChange = viewModel::onPromoCodeInputChanged,
                appliedLabel = state.validatedDiscountLabel,
                isApplied = state.validatedPromoCodeId != null,
                isValidating = state.promoValidating,
                errorText = state.promoError,
                onApply = viewModel::onApplyPromoTapped,
                onClear = viewModel::onClearPromoTapped,
            )
        }

        TotalSection(
            amountCents = state.paymentConfig?.amountCents,
            isFree = state.freeOverride,
            prepareError = state.prepareError,
        )

        Button(
            onClick = { viewModel.onPayTapped() },
            enabled = !state.preparing,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE6A00)),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.preparing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.height(22.dp))
            } else {
                Text(
                    text = if (state.freeOverride) "Valider" else "Payer",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
            }
        }

        TextButton(
            onClick = { viewModel.onBackRequested() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Annuler")
        }
    }
}

@Composable
private fun PaymentHeader(context: PaymentContext) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Paiement",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFE6A00),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (context) {
                is PaymentContext.CreatorForfait -> "Finalise la création de ta partie en réglant le forfait."
                is PaymentContext.HunterCaution -> "Verse la caution pour rejoindre cette partie."
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun PromoCodeSection(
    code: String,
    onCodeChange: (String) -> Unit,
    appliedLabel: String?,
    isApplied: Boolean,
    isValidating: Boolean,
    errorText: String?,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Code promo (optionnel)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                enabled = !isApplied && !isValidating,
                singleLine = true,
                placeholder = { Text("Ex: POULE2026") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(56.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (isApplied) {
                TextButton(onClick = onClear) { Text("Retirer", color = Color.Red) }
            } else {
                Button(onClick = onApply, enabled = code.isNotBlank() && !isValidating) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.width(18.dp).height(18.dp),
                        )
                    } else {
                        Text("Appliquer")
                    }
                }
            }
        }

        appliedLabel?.let {
            Text("✓ $it appliqué", color = Color(0xFF16A34A), fontSize = 13.sp)
        }
        errorText?.let {
            Text(it, color = Color.Red, fontSize = 12.sp)
        }
        Text(
            "Les codes promo sont gérés par Stripe — crée/désactive-les dans le Stripe Dashboard.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun TotalSection(amountCents: Int?, isFree: Boolean, prepareError: String?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        when {
            isFree -> Text("GRATUIT", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
            amountCents != null -> Text(
                String.format("%.2f €", amountCents / 100.0),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFE6A00),
            )
            prepareError != null -> Text(prepareError, color = Color.Red, fontSize = 13.sp)
            else -> Text(
                "Le montant sera calculé à l'étape suivante",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }
    }
}
