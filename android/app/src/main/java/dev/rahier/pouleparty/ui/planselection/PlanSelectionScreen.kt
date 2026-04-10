package dev.rahier.pouleparty.ui.planselection

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.model.PartyPlansConfig
import dev.rahier.pouleparty.model.PricingModel
import dev.rahier.pouleparty.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSelectionScreen(
    onPlanSelected: (PricingParams) -> Unit,
    onBack: () -> Unit,
    viewModel: PlanSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    if (uiState.showDailyLimitAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDailyLimitAlert() },
            title = { Text("Daily limit reached", style = bangerStyle(22)) },
            text = { Text("You can only create 1 free game per day. Upgrade to a paid plan for unlimited games.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDailyLimitAlert() }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.step == PlanSelectionUiState.Step.CONFIGURE_PRICING) {
            TextButton(
                onClick = { viewModel.backToPlans() },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back", style = gameboyStyle(9))
            }
        }

        Text(
            text = "Create Party",
            style = bangerStyle(32),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

            when {
                uiState.isLoading -> {
                    Spacer(modifier = Modifier.weight(1f))
                    CircularProgressIndicator(color = CROrange)
                    Spacer(modifier = Modifier.weight(1f))
                }
                uiState.loadFailed -> {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Failed to load pricing",
                        style = gameboyStyle(9),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadPlansConfig() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CROrange)
                    ) {
                        Text("Retry", style = bangerStyle(20), color = Color.White)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                else -> {
                    Text(
                        text = if (uiState.step == PlanSelectionUiState.Step.CHOOSE_PLAN) "Choose your plan"
                               else uiState.selectedPlan?.title ?: "",
                        style = gameboyStyle(10),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    val config = uiState.plansConfig ?: return@Column
                    AnimatedContent(targetState = uiState.step, label = "plan_step") { step ->
                        when (step) {
                            PlanSelectionUiState.Step.CHOOSE_PLAN -> {
                                PlanTilesContent(
                                    config = config,
                                    onFreeTapped = {
                                        scope.launch {
                                            if (viewModel.canCreateFreeGame()) {
                                                onPlanSelected(PricingParams("free", 5, 0, 0))
                                            } else {
                                                viewModel.showDailyLimitAlert()
                                            }
                                        }
                                    },
                                    onFlatTapped = { viewModel.selectPlan(PricingModel.FLAT) },
                                    onDepositTapped = { viewModel.selectPlan(PricingModel.DEPOSIT) }
                                )
                            }
                            PlanSelectionUiState.Step.CONFIGURE_PRICING -> {
                                PricingConfigContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onConfirm = { onPlanSelected(viewModel.buildNavigationParams()) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

@Composable
private fun PlanTilesContent(
    config: PartyPlansConfig,
    onFreeTapped: () -> Unit,
    onFlatTapped: () -> Unit,
    onDepositTapped: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlanTile(
            title = "FREE",
            subtitle = "1 game per day, up to ${config.free.maxPlayers} players",
            emoji = "\uD83C\uDFAE",
            isEnabled = config.free.enabled,
            onClick = onFreeTapped
        )
        PlanTile(
            title = "Forfait",
            subtitle = "${config.flat.pricePerPlayerCents / 100}€ per player",
            emoji = "\u2B50",
            gradient = GradientFire,
            isFeatured = true,
            isEnabled = config.flat.enabled,
            onClick = onFlatTapped
        )
        PlanTile(
            title = "Caution + %",
            subtitle = "${config.deposit.depositAmountCents / 100}€ deposit + ${config.deposit.commissionPercent.toInt()}% commission",
            emoji = "\uD83D\uDC8E",
            gradient = GradientDeposit,
            isEnabled = config.deposit.enabled,
            onClick = onDepositTapped
        )
    }
}

@Composable
private fun PricingConfigContent(
    uiState: PlanSelectionUiState,
    viewModel: PlanSelectionViewModel,
    onConfirm: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (uiState.selectedPlan) {
            PricingModel.FLAT -> FlatConfig(uiState, viewModel)
            PricingModel.DEPOSIT -> DepositConfig(uiState, viewModel)
            else -> {}
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GradientFire, RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Continue", style = bangerStyle(22), color = Color.White)
            }
        }

        TextButton(
            onClick = { viewModel.backToPlans() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                "Back",
                style = gameboyStyle(9),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FlatConfig(uiState: PlanSelectionUiState, viewModel: PlanSelectionViewModel) {
    val config = uiState.plansConfig?.flat ?: return
    val pricePerPlayer = config.pricePerPlayerCents / 100
    val total = uiState.numberOfPlayers.toInt() * pricePerPlayer

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Number of players",
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            "${uiState.numberOfPlayers.toInt()}",
            style = bangerStyle(48),
            color = MaterialTheme.colorScheme.onBackground
        )
        Slider(
            value = uiState.numberOfPlayers,
            onValueChange = { viewModel.updateNumberOfPlayers(it) },
            valueRange = config.minPlayers.toFloat()..config.maxPlayers.toFloat(),
            steps = config.maxPlayers - config.minPlayers - 1,
            colors = SliderDefaults.colors(thumbColor = CROrange, activeTrackColor = CROrange)
        )
        PriceSummaryRow("Price per player", "${pricePerPlayer}€")
        PriceSummaryRow("Total", "${total}€", isTotal = true)
    }
}

@Composable
private fun DepositConfig(uiState: PlanSelectionUiState, viewModel: PlanSelectionViewModel) {
    val config = uiState.plansConfig?.deposit ?: return
    val depositEuros = config.depositAmountCents / 100
    val commissionPct = config.commissionPercent.toInt()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Admin-defined info
        PriceSummaryRow("Deposit", "${depositEuros}€")
        PriceSummaryRow("Commission", "${commissionPct}%")

        HorizontalDivider()

        // User sets price per hunter
        PriceField(
            label = "Price per hunter",
            placeholder = "5",
            value = uiState.pricePerHunter,
            onValueChange = { viewModel.updatePricePerHunter(it) },
            suffix = "€"
        )

        // Optional max players toggle + slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Limit number of players",
                style = gameboyStyle(8),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Switch(
                checked = uiState.hasMaxPlayers,
                onCheckedChange = { viewModel.updateHasMaxPlayers(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = CROrange)
            )
        }

        if (uiState.hasMaxPlayers) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${uiState.maxPlayers.toInt()} players max",
                    style = bangerStyle(22),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = uiState.maxPlayers,
                    onValueChange = { viewModel.updateMaxPlayers(it) },
                    valueRange = 2f..50f,
                    steps = 47,
                    colors = SliderDefaults.colors(thumbColor = CROrange, activeTrackColor = CROrange)
                )
            }
        }

        // Summary
        val price = uiState.pricePerHunter.toIntOrNull()
        if (price != null && price > 0) {
            HorizontalDivider()
            val commission = price.toDouble() * commissionPct / 100.0
            PriceSummaryRow("You earn per hunter", String.format("%.2f€", price - commission))
            PriceSummaryRow("Hunter pays", "${price}€", isTotal = true)
        }
    }
}

@Composable
private fun PriceSummaryRow(label: String, value: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = gameboyStyle(if (isTotal) 9 else 8),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isTotal) 1f else 0.6f)
        )
        Text(
            value,
            style = bangerStyle(if (isTotal) 24 else 18),
            color = if (isTotal) CROrange else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun PriceField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String
) {
    Column {
        Text(
            label,
            style = gameboyStyle(8),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        val shape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = { newVal -> onValueChange(newVal.filter { it.isDigit() }) },
                placeholder = { Text(placeholder, style = bangerStyle(24), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)) },
                textStyle = bangerStyle(24).copy(color = MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            Text(suffix, style = bangerStyle(24), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun PlanTile(
    title: String,
    subtitle: String,
    emoji: String,
    gradient: Brush? = null,
    isFeatured: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isFeatured) 8.dp else 4.dp, shape)
            .clip(shape)
            .then(
                if (gradient != null) {
                    Modifier.background(gradient)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), shape)
                }
            )
            .then(
                if (isEnabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .alpha(if (isEnabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontSize = 32.sp,
            modifier = Modifier.padding(end = 16.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = bangerStyle(22),
                color = if (gradient != null) Color.White else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = gameboyStyle(7),
                color = if (gradient != null) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (gradient != null) Color.White.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
