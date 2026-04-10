package dev.rahier.pouleparty.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.PricingModel
import dev.rahier.pouleparty.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Nickname section
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            stringResource(R.string.nickname),
                            style = bangerStyle(18),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    OutlinedTextField(
                        value = state.nickname,
                        onValueChange = { viewModel.onNicknameChanged(it) },
                        singleLine = true,
                        textStyle = bangerStyle(22).copy(textAlign = TextAlign.Center),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${state.nickname.length}/${SettingsViewModel.NICKNAME_MAX_LENGTH}",
                            style = bangerStyle(14),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )

                        Button(
                            onClick = { viewModel.saveNickname() },
                            enabled = state.nickname.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                stringResource(R.string.save),
                                style = bangerStyle(16)
                            )
                        }
                    }
                }
            }

            // My Games section
            MyGamesSection(state, viewModel)

            // Links section
            SettingsCard {
                Column {
                    SettingsRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = stringResource(R.string.privacy_policy),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pouleparty.be/privacy")))
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    )
                    SettingsRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.terms_of_use),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pouleparty.be/terms")))
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    )
                    SettingsRow(
                        icon = Icons.Filled.Email,
                        title = stringResource(R.string.contact_support),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:julien@rahier.dev")))
                        }
                    )
                }
            }

            // Danger section
            SettingsCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.onDeleteDataTapped() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Danger.copy(alpha = 0.85f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(R.string.delete_my_data),
                                style = bangerStyle(18)
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.delete_data_footer),
                        style = bangerStyle(13),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }

            // Version section
            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    val packageInfo = try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (_: Exception) { null }
                    val versionName = packageInfo?.versionName ?: "—"
                    @Suppress("DEPRECATION")
                    val buildNumber = packageInfo?.versionCode?.toString() ?: "—"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.version),
                            style = bangerStyle(16),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            versionName,
                            style = bangerStyle(16),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Build",
                            style = bangerStyle(14),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                        Text(
                            buildNumber,
                            style = bangerStyle(14),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (state.isShowingNicknameSaved) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNicknameSaved() },
            title = { Text(stringResource(R.string.nickname_saved)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNicknameSaved() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    if (state.isShowingProfanityAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissProfanityAlert() },
            title = { Text(stringResource(R.string.inappropriate_nickname)) },
            text = { Text(stringResource(R.string.inappropriate_nickname_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProfanityAlert() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    if (state.isShowingDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onDeleteDismissed() },
            title = { Text(stringResource(R.string.delete_data_title)) },
            text = { Text(stringResource(R.string.delete_data_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDeleteDismissed() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (state.isShowingDeleteSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.onDeleteSuccessDismissed() },
            title = { Text(stringResource(R.string.data_deleted)) },
            text = { Text(stringResource(R.string.data_deleted_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteSuccessDismissed() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    if (state.isShowingDeleteError) {
        AlertDialog(
            onDismissRequest = { viewModel.onDeleteErrorDismissed() },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(stringResource(R.string.delete_error_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteErrorDismissed() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

// MARK: - Components

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun MyGamesSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🎮", modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.my_games), style = bangerStyle(18), color = MaterialTheme.colorScheme.onBackground)
            }

            when {
                state.isLoadingGames -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CROrange, modifier = Modifier.size(24.dp))
                    }
                }
                state.myGames.isEmpty() -> {
                    Text(
                        stringResource(R.string.no_games_yet),
                        style = gameboyStyle(8),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Column {
                        state.myGames.forEachIndexed { index, game ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                                )
                            }
                            GameRow(game = game, dateFormat = dateFormat) {
                                viewModel.selectGame(game)
                            }
                        }
                    }
                }
            }
        }
    }

    val selectedGame = state.selectedGame
    if (selectedGame != null) {
        GameDetailDialog(game = selectedGame, dateFormat = dateFormat, onDismiss = { viewModel.dismissGameDetail() })
    }
}

@Composable
private fun GameRow(game: Game, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN) "🐔" else "📍",
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                game.name.ifEmpty { "Game ${game.gameCode}" },
                style = bangerStyle(16),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                dateFormat.format(game.startDate),
                style = gameboyStyle(7),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        GameStatusBadge(game.gameStatusEnum)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun GameStatusBadge(status: GameStatus) {
    val (label, color) = when (status) {
        GameStatus.WAITING -> "Waiting" to CROrange
        GameStatus.IN_PROGRESS -> "Live" to Success
        GameStatus.DONE -> "Done" to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
    }
    Text(
        label,
        style = gameboyStyle(6),
        color = Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailDialog(game: Game, dateFormat: SimpleDateFormat, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if (game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN) "🐔" else "📍", style = bangerStyle(48))
                Text(game.gameModEnum.title, style = bangerStyle(22), color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                GameStatusBadge(game.gameStatusEnum)
            }

            HorizontalDivider()

            // Info
            DetailRow("Game Code", game.gameCode)
            DetailRow("Found Code", game.foundCode)
            DetailRow("Pricing", game.pricingModelEnum.title)
            if (game.isPaid) {
                if (game.pricing.pricePerPlayer > 0) DetailRow("Price/Player", "${game.pricing.pricePerPlayer / 100}€")
                if (game.pricing.deposit > 0) DetailRow("Deposit", "${game.pricing.deposit / 100}€")
            }

            HorizontalDivider()

            // Players
            DetailRow("Max Players", "${game.maxPlayers}")
            DetailRow("Hunters Joined", "${game.hunterIds.size}")
            DetailRow("Winners", "${game.winners.size}")
            if (game.chickenCanSeeHunters) DetailRow("Chicken Sees Hunters", "Yes")

            HorizontalDivider()

            // Timing
            DetailRow("Start", dateFormat.format(game.startDate))
            DetailRow("End", dateFormat.format(game.endDate))
            if (game.timing.headStartMinutes > 0) DetailRow("Head Start", "${game.timing.headStartMinutes.toInt()} min")
            DetailRow("Power-ups", if (game.powerUps.enabled) "On" else "Off")

            HorizontalDivider()

            // Zone
            DetailRow("Initial Radius", "${game.zone.radius.toInt()}m")
            DetailRow("Shrink Interval", "${game.zone.shrinkIntervalMinutes.toInt()} min")
            DetailRow("Shrink Amount", "${game.zone.shrinkMetersPerUpdate.toInt()}m")

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = gameboyStyle(8), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(value, style = bangerStyle(16), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = bangerStyle(18),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        )
    }
}
