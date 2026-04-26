package dev.rahier.pouleparty.ui.debugpreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.ui.chickenmapconfig.ChickenMapConfigScreen
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.GameBoyFont

/**
 * Long-press-on-Create-Party easter egg wrapper. Hosts the same
 * [ChickenMapConfigScreen] the GameCreation wizard uses so the user
 * can tap to place start + final pins and drag the radius slider,
 * then a single "Launch Preview" button commits the game and routes
 * to the chicken map in debug mode with every shrunk circle
 * pre-rendered.
 */
@Composable
fun DebugMapSetupScreen(
    onCancel: () -> Unit,
    onLaunched: (gameId: String) -> Unit,
    viewModel: DebugMapSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DebugMapSetupEffect.NavigateToChickenMapDebug -> onLaunched(effect.gameId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChickenMapConfigScreen(
            initialRadius = state.radius,
            finalMarker = state.finalLocation,
            onLocationSelected = viewModel::onLocationSelected,
            onFinalLocationSelected = viewModel::onFinalLocationSelected,
            onRadiusChanged = viewModel::onRadiusChanged,
            isFollowMode = false,
        )

        // Top-right close button: dismiss the setup without creating a
        // game. Placed here so it sits above the map config's search
        // overlay rather than competing with the existing chrome.
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
        ) {
            Text(
                "Cancel",
                fontFamily = GameBoyFont,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // Bottom-right Launch button sits above the map config's bottom
        // bar (radius slider + pin picker) thanks to the ZStack ordering.
        // Tapping it triggers the Firestore write and navigation.
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = CROrange,
        ) {
            Text(
                "Launch Preview",
                fontFamily = GameBoyFont,
                fontSize = 9.sp,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .clickable { viewModel.onLaunchTapped() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
