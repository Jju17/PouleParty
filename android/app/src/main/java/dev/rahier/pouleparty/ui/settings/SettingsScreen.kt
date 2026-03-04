package dev.rahier.pouleparty.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R

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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Privacy & Terms
            ListItem(
                headlineContent = { Text(stringResource(R.string.privacy_policy)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pouleparty.be/privacy")))
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.terms_of_use)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pouleparty.be/terms")))
                }
            )
            HorizontalDivider()

            Spacer(Modifier.height(16.dp))

            // Contact
            ListItem(
                headlineContent = { Text(stringResource(R.string.contact_support)) },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:julien@rahier.dev")))
                }
            )
            HorizontalDivider()

            Spacer(Modifier.height(16.dp))

            // Delete data
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.delete_my_data),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { viewModel.onDeleteDataTapped() }
            )
            Text(
                stringResource(R.string.delete_data_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(Modifier.padding(top = 8.dp))

            Spacer(Modifier.height(16.dp))

            // Version
            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
            } catch (_: Exception) { "—" }
            ListItem(
                headlineContent = { Text(stringResource(R.string.version)) },
                trailingContent = {
                    Text(
                        versionName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }

    // Delete confirmation dialog
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

    // Delete success dialog
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
}
