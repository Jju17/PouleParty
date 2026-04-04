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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.step == PlanSelectionUiState.Step.CONFIGURE_PRICING) {
                            viewModel.backToPlans()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Create Party",
                style = bangerStyle(32),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            Text(
                text = if (uiState.step == PlanSelectionUiState.Step.CHOOSE_PLAN) "Choose your plan"
                       else uiState.selectedPlan?.title ?: "",
                style = gameboyStyle(10),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            AnimatedContent(targetState = uiState.step, label = "plan_step") { step ->
                when (step) {
                    PlanSelectionUiState.Step.CHOOSE_PLAN -> {
                        PlanTilesContent(
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

@Composable
private fun PlanTilesContent(
    onFreeTapped: () -> Unit,
    onFlatTapped: () -> Unit,
    onDepositTapped: () -> Unit
) {
    Column {
        PlanTile(
            title = "FREE",
            subtitle = "1 game per day, up to 5 players",
            emoji = "\uD83C\uDFAE",
            onClick = onFreeTapped
        )
        Spacer(modifier = Modifier.height(16.dp))
        PlanTile(
            title = "Forfait",
            subtitle = "Pay once for unlimited players",
            emoji = "\u2B50",
            gradient = GradientFire,
            isFeatured = true,
            onClick = onFlatTapped
        )
        Spacer(modifier = Modifier.height(16.dp))
        PlanTile(
            title = "Caution + %",
            subtitle = "Deposit + commission per player",
            emoji = "\uD83D\uDC8E",
            gradient = GradientDeposit,
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
            valueRange = 6f..50f,
            steps = 43,
            colors = SliderDefaults.colors(thumbColor = CROrange, activeTrackColor = CROrange)
        )
        PriceSummaryRow("Price per player", "${uiState.flatPricePerPlayer}€")
        PriceSummaryRow("Total", "${uiState.flatTotal}€", isTotal = true)
    }
}

@Composable
private fun DepositConfig(uiState: PlanSelectionUiState, viewModel: PlanSelectionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PriceField(
            label = "Deposit amount",
            placeholder = "10",
            value = uiState.depositAmount,
            onValueChange = { viewModel.updateDepositAmount(it) },
            suffix = "€"
        )
        PriceField(
            label = "Price per hunter",
            placeholder = "5",
            value = uiState.pricePerPlayer,
            onValueChange = { viewModel.updatePricePerPlayer(it) },
            suffix = "€"
        )

        val deposit = uiState.depositAmount.toIntOrNull()
        val price = uiState.pricePerPlayer.toIntOrNull()
        if (deposit != null && price != null && price > 0) {
            val commission = (price * 0.15).toInt()
            PriceSummaryRow("Your deposit", "${deposit}€")
            PriceSummaryRow("Commission (15%)", "${commission}€ / player")
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
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val hasGradient = gradient != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isFeatured) 8.dp else 4.dp, shape)
            .clip(shape)
            .then(
                if (hasGradient) {
                    Modifier.background(gradient!!)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), shape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
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
                color = if (hasGradient) Color.White else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = gameboyStyle(7),
                color = if (hasGradient) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (hasGradient) Color.White.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}
