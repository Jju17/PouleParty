package dev.rahier.pouleparty.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
                (page > 5 && s.nickname.trim().isEmpty())

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

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshNotificationPermission() }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                4 -> SlideNotifications(
                    hasPermission = state.hasNotificationPermission,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                5 -> SlideNickname(
                    nickname = state.nickname,
                    maxLength = OnboardingViewModel.NICKNAME_MAX_LENGTH,
                    onNicknameChanged = { viewModel.onNicknameChanged(it) }
                )
                6 -> SlideReady()
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
                                if (index == state.currentPage) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
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
                            stringResource(R.string.back),
                            style = bangerStyle(18),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                val isLocationPageBlocked = state.currentPage == 3 && !state.hasFineLocation
                val isNicknamePageEmpty = state.currentPage == 5 && state.nickname.trim().isEmpty()
                val isNextDisabled = isLocationPageBlocked || isNicknamePageEmpty
                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .widthIn(min = 120.dp)
                        .shadow(4.dp, RoundedCornerShape(50.dp))
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (!isNextDisabled) GradientFire
                            else Brush.linearGradient(listOf(CROrange.copy(alpha = 0.4f), CRPink.copy(alpha = 0.4f)))
                        )
                        .clickable(enabled = !isNextDisabled) {
                            if (viewModel.isLastPage) {
                                if (viewModel.canCompleteOnboarding()) {
                                    onOnboardingCompleted(state.nickname.trim())
                                }
                            } else {
                                viewModel.nextPage()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (viewModel.isLastPage) stringResource(R.string.lets_go) else stringResource(R.string.next),
                        style = bangerStyle(22),
                        color = Color.Black,
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
            title = { Text(stringResource(R.string.location_required)) },
            text = { Text(stringResource(R.string.location_required_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLocationAlert() }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    // Profanity alert
    if (state.showProfanityAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissProfanityAlert() },
            title = { Text(stringResource(R.string.inappropriate_nickname)) },
            text = { Text(stringResource(R.string.inappropriate_nickname_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissProfanityAlert() }) { Text(stringResource(R.string.ok)) }
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
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                subtitle,
                style = bangerStyle(18),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        extraContent()
    }
}

// MARK: - Slides

@Composable
private fun SlideWelcome() {
    OnboardingSlideLayout(
        title = stringResource(R.string.welcome_title),
        subtitle = stringResource(R.string.welcome_subtitle),
        titleSize = 36,
        icon = {
            Image(
                painter = painterResource(R.drawable.chicken),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(160.dp)
            )
        }
    )
}

@Composable
private fun SlideNominateChicken() {
    OnboardingSlideLayout(
        title = stringResource(R.string.nominate_chicken),
        subtitle = stringResource(R.string.nominate_subtitle),
        icon = { Text("\uD83D\uDC14", fontSize = 80.sp) }
    )
}

@Composable
private fun SlideHuntThemDown() {
    OnboardingSlideLayout(
        title = stringResource(R.string.hunt_them_down),
        subtitle = stringResource(R.string.hunt_subtitle),
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
        title = stringResource(R.string.need_location),
        icon = { Text(emoji, fontSize = 80.sp) }
    ) {
        Spacer(Modifier.height(16.dp))
        when {
            hasBackgroundLocation -> {
                Text(
                    stringResource(R.string.location_granted),
                    style = bangerStyle(18),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Text("\u2705 " + stringResource(R.string.always_allowed), style = bangerStyle(20), color = Success)
            }
            hasFineLocation -> {
                Text(
                    stringResource(R.string.location_partial),
                    style = bangerStyle(18),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .shadow(4.dp, RoundedCornerShape(50.dp))
                        .clip(RoundedCornerShape(50.dp))
                        .background(GradientFire)
                        .clickable { onRequestBackgroundLocation() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.allow_always),
                        style = bangerStyle(20),
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            else -> {
                Text(
                    stringResource(R.string.location_denied),
                    style = bangerStyle(18),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .height(50.dp)
                        .shadow(4.dp, RoundedCornerShape(50.dp))
                        .clip(RoundedCornerShape(50.dp))
                        .background(GradientFire)
                        .clickable { onRequestFineLocation() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.allow_location_access),
                        style = bangerStyle(20),
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideNotifications(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val emoji = if (hasPermission) "\uD83D\uDD14" else "\uD83D\uDD14"

    OnboardingSlideLayout(
        title = stringResource(R.string.notif_stay_in_loop),
        icon = { Text(emoji, fontSize = 80.sp) }
    ) {
        Spacer(Modifier.height(16.dp))
        if (hasPermission) {
            Text(
                stringResource(R.string.notif_enabled_description),
                style = bangerStyle(18),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "\u2705 " + stringResource(R.string.notif_enabled),
                style = bangerStyle(20),
                color = Success
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                stringResource(R.string.notif_request_description),
                style = bangerStyle(18),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .height(50.dp)
                    .shadow(4.dp, RoundedCornerShape(50.dp))
                    .clip(RoundedCornerShape(50.dp))
                    .background(GradientFire)
                    .clickable { onRequestPermission() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.notif_enable_button),
                    style = bangerStyle(20),
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            // Below Android 13, notifications are always enabled
            Text(
                stringResource(R.string.notif_enabled_description),
                style = bangerStyle(18),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "\u2705 " + stringResource(R.string.notif_enabled),
                style = bangerStyle(20),
                color = Success
            )
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
        title = stringResource(R.string.choose_nickname),
        subtitle = stringResource(R.string.nickname_subtitle),
        icon = { Text("\uD83C\uDFF7\uFE0F", fontSize = 80.sp) }
    ) {
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChanged,
            label = { Text(stringResource(R.string.your_nickname)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${nickname.length}/$maxLength",
            style = bangerStyle(14),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun SlideReady() {
    OnboardingSlideLayout(
        title = stringResource(R.string.the_endgame),
        subtitle = stringResource(R.string.endgame_subtitle),
        icon = { Text("\uD83C\uDF89", fontSize = 80.sp) }
    )
}
