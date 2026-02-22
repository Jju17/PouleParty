package dev.rahier.pouleparty.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.theme.*

@Composable
fun OnboardingScreen(
    onOnboardingCompleted: (String) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { OnboardingViewModel.TOTAL_PAGES })

    // Sync pager with viewmodel
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        val s = viewModel.uiState.value

        val isBlocked = (page > 3 && !s.hasFineLocation) ||
                (page > 4 && s.nickname.trim().isEmpty())

        if (isBlocked) {
            pagerState.scrollToPage(s.currentPage)
        } else {
            viewModel.setPage(page)
        }
    }

    // Permission launchers
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CRBeige)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> SlideWelcome()
                1 -> SlideNominateChicken()
                2 -> SlideHuntThemDown()
                3 -> SlideLocation(
                    hasFineLocation = state.hasFineLocation,
                    hasBackgroundLocation = state.hasBackgroundLocation,
                    onRequestFineLocation = {
                        fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    onRequestBackgroundLocation = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                )
                4 -> SlideNickname(
                    nickname = state.nickname,
                    maxLength = OnboardingViewModel.NICKNAME_MAX_LENGTH,
                    onNicknameChanged = { viewModel.onNicknameChanged(it) }
                )
                5 -> SlideReady()
            }
        }

        // Bottom navigation overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(OnboardingViewModel.TOTAL_PAGES) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == state.currentPage) Color.Black
                                else Color.Black.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.currentPage > 0) {
                    TextButton(onClick = { viewModel.previousPage() }) {
                        Text(
                            "Back",
                            style = bangerStyle(18),
                            color = Color.Black.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                val isLocationPageBlocked = state.currentPage == 3 && !state.hasFineLocation
                val isNicknamePageEmpty = state.currentPage == 4 && state.nickname.trim().isEmpty()
                val isNextDisabled = isLocationPageBlocked || isNicknamePageEmpty
                Button(
                    onClick = {
                        if (viewModel.isLastPage) {
                            if (viewModel.canCompleteOnboarding()) {
                                onOnboardingCompleted(state.nickname.trim())
                            }
                        } else {
                            viewModel.nextPage()
                        }
                    },
                    enabled = !isNextDisabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CROrange,
                        disabledContainerColor = CROrange.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (viewModel.isLastPage) "Let's go!" else "Next",
                        style = bangerStyle(22),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // Location required alert
    if (state.showLocationAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLocationAlert() },
            title = { Text("Location Required") },
            text = { Text("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLocationAlert() }) { Text("OK") }
            }
        )
    }
}

// MARK: - Generic Slide Layout

@Composable
private fun OnboardingSlideLayout(
    title: String,
    subtitle: String? = null,
    titleSize: Int = 32,
    icon: @Composable () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = bangerStyle(titleSize),
            textAlign = TextAlign.Center,
            color = Color.Black
        )
        if (subtitle != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                subtitle,
                style = bangerStyle(18),
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
        extraContent()
    }
}

// MARK: - Slides

@Composable
private fun SlideWelcome() {
    OnboardingSlideLayout(
        title = "Welcome to\nPouleParty!",
        subtitle = "The ultimate hide-and-seek\npub crawl game",
        titleSize = 36,
        icon = {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.size(160.dp)
            )
        }
    )
}

@Composable
private fun SlideNominateChicken() {
    OnboardingSlideLayout(
        title = "Nominate a Chicken",
        subtitle = "Pick the Stag, the Birthday Girl, or whoever just really wants to be a Chicken.\n\nTheir job is to hide.",
        icon = { Text("\uD83D\uDC14", fontSize = 80.sp) }
    )
}

@Composable
private fun SlideHuntThemDown() {
    OnboardingSlideLayout(
        title = "Hunt Them Down",
        subtitle = "Split into squads. Use the map to track them.\n\nThe Chicken could be hiding in any pub or bar.",
        icon = { Text("\uD83D\uDDFA\uFE0F", fontSize = 80.sp) }
    )
}

@Composable
private fun SlideLocation(
    hasFineLocation: Boolean,
    hasBackgroundLocation: Boolean,
    onRequestFineLocation: () -> Unit,
    onRequestBackgroundLocation: () -> Unit
) {
    val emoji = when {
        hasBackgroundLocation -> "\uD83D\uDCCD"
        hasFineLocation -> "\uD83D\uDC40"
        else -> "\uD83D\uDE22"
    }
    OnboardingSlideLayout(
        title = "We Need Your Location",
        icon = { Text(emoji, fontSize = 80.sp) }
    ) {
        Spacer(Modifier.height(16.dp))
        when {
            hasBackgroundLocation -> {
                Text(
                    "You're all set! The game will track location even in the background.\n\nMaximum fun guaranteed!",
                    style = bangerStyle(18),
                    textAlign = TextAlign.Center,
                    color = Color.Black.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Text("\u2705 Always allowed!", style = bangerStyle(20), color = Color(0xFF4CAF50))
            }
            hasFineLocation -> {
                Text(
                    "Almost there! The game needs to track the Chicken even when the app is in the background.\n\nPlease allow background location.",
                    style = bangerStyle(18),
                    textAlign = TextAlign.Center,
                    color = Color.Black.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRequestBackgroundLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = CROrange),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Allow Always", style = bangerStyle(20), color = Color.White)
                }
            }
            else -> {
                Text(
                    "Without it, the game can't work.\nNo location = no map = no fun.\n\nWe promise we only use it during the game!",
                    style = bangerStyle(18),
                    textAlign = TextAlign.Center,
                    color = Color.Black.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRequestFineLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = CROrange),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Allow Location Access", style = bangerStyle(20), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SlideNickname(
    nickname: String,
    maxLength: Int,
    onNicknameChanged: (String) -> Unit
) {
    OnboardingSlideLayout(
        title = "Choose Your Nickname",
        subtitle = "This is how other players\nwill see you in the game.",
        icon = { Text("\uD83C\uDFF7\uFE0F", fontSize = 80.sp) }
    ) {
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChanged,
            label = { Text("Your nickname") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${nickname.length}/$maxLength",
            style = bangerStyle(14),
            color = Color.Black.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun SlideReady() {
    OnboardingSlideLayout(
        title = "The Endgame",
        subtitle = "Close in on the Chicken. Complete challenges for points. Unleash weapons.\n\nIt's Mario Kart rules \u2014 anything goes.",
        icon = { Text("\uD83C\uDF89", fontSize = 80.sp) }
    )
}
