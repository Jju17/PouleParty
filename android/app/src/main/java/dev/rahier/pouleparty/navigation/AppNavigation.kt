package dev.rahier.pouleparty.navigation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigScreen
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapScreen
import dev.rahier.pouleparty.ui.huntermap.HunterMapScreen
import dev.rahier.pouleparty.ui.onboarding.OnboardingScreen
import dev.rahier.pouleparty.ui.home.HomeScreen
import dev.rahier.pouleparty.ui.settings.SettingsScreen
import dev.rahier.pouleparty.ui.victory.VictoryScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHICKEN_CONFIG = "chicken_config/{gameId}"
    const val CHICKEN_MAP = "chicken_map/{gameId}"
    const val HUNTER_MAP = "hunter_map/{gameId}/{hunterName}"
    const val VICTORY = "victory/{gameId}/{hunterName}/{hunterId}"
    const val SETTINGS = "settings"

    fun chickenConfig(gameId: String) = "chicken_config/$gameId"
    fun chickenMap(gameId: String) = "chicken_map/$gameId"
    fun hunterMap(gameId: String, hunterName: String) = "hunter_map/$gameId/${Uri.encode(hunterName)}"
    fun victory(gameId: String, hunterName: String, hunterId: String) =
        "victory/$gameId/${Uri.encode(hunterName)}/${Uri.encode(hunterId)}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val hasCompletedOnboarding = prefs.getBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, false)

    var isAuthReady by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var authError by remember { mutableStateOf(false) }
    var authRetryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(authRetryTrigger) {
        if (!isAuthReady) {
            authError = false
            try {
                FirebaseAuth.getInstance().signInAnonymously().await()
                isAuthReady = true
            } catch (e: Exception) {
                Log.e("AppNavigation", "Anonymous sign-in failed", e)
                authError = true
                return@LaunchedEffect
            }
        }
        // Save FCM token after auth
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                FirebaseFirestore.getInstance()
                    .collection("fcmTokens")
                    .document(userId)
                    .set(
                        mapOf(
                            "token" to token,
                            "platform" to "android",
                            "updatedAt" to Timestamp.now()
                        )
                    )
            } catch (e: Exception) {
                Log.e("AppNavigation", "Failed to save FCM token", e)
            }
        }
    }

    if (authError) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Connection failed. Please check your internet and try again.")
                Button(onClick = { authRetryTrigger++ }) {
                    Text("Retry")
                }
            }
        }
        return
    }

    if (!isAuthReady) return

    val startDestination = if (hasCompletedOnboarding) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingCompleted = { nickname ->
                    prefs.edit()
                        .putBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, true)
                        .putString(AppConstants.PREF_USER_NICKNAME, nickname)
                        .apply()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToChickenConfig = { gameId ->
                    navController.navigate(Routes.chickenConfig(gameId))
                },
                onNavigateToChickenMap = { gameId ->
                    navController.navigate(Routes.chickenMap(gameId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToHunterMap = { gameId, hunterName ->
                    navController.navigate(Routes.hunterMap(gameId, hunterName)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToVictory = { gameId ->
                    navController.navigate(Routes.victory(gameId, "", "")) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CHICKEN_CONFIG,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            ChickenConfigScreen(
                onStartGame = { gameId ->
                    navController.navigate(Routes.chickenMap(gameId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.CHICKEN_MAP,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            ChickenMapScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onVictory = { gameId ->
                    navController.navigate(Routes.victory(gameId, "", "")) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.HUNTER_MAP,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("hunterName") { type = NavType.StringType }
            )
        ) {
            HunterMapScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onVictory = { gameId, hunterName, hunterId ->
                    navController.navigate(Routes.victory(gameId, hunterName, hunterId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.VICTORY,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("hunterName") { type = NavType.StringType },
                navArgument("hunterId") { type = NavType.StringType }
            )
        ) {
            VictoryScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}
