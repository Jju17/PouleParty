package dev.rahier.pouleparty.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.firestore.FirebaseFirestore
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigScreen
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapScreen
import dev.rahier.pouleparty.ui.huntermap.HunterMapScreen
import dev.rahier.pouleparty.ui.onboarding.OnboardingScreen
import dev.rahier.pouleparty.ui.selection.SelectionScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val SELECTION = "selection"
    const val CHICKEN_CONFIG = "chicken_config/{gameId}"
    const val CHICKEN_MAP = "chicken_map/{gameId}"
    const val HUNTER_MAP = "hunter_map/{gameId}"

    fun chickenConfig(gameId: String) = "chicken_config/$gameId"
    fun chickenMap(gameId: String) = "chicken_map/$gameId"
    fun hunterMap(gameId: String) = "hunter_map/$gameId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("pouleparty", Context.MODE_PRIVATE)
    val hasCompletedOnboarding = prefs.getBoolean("hasCompletedOnboarding", false)

    val startDestination = if (hasCompletedOnboarding) Routes.SELECTION else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingCompleted = {
                    prefs.edit().putBoolean("hasCompletedOnboarding", true).apply()
                    navController.navigate(Routes.SELECTION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SELECTION) {
            SelectionScreen(
                onNavigateToChickenConfig = { gameId ->
                    navController.navigate(Routes.chickenConfig(gameId))
                },
                onNavigateToChickenMap = { gameId ->
                    navController.navigate(Routes.chickenMap(gameId)) {
                        popUpTo(Routes.SELECTION) { inclusive = false }
                    }
                },
                onNavigateToHunterMap = { gameId ->
                    navController.navigate(Routes.hunterMap(gameId)) {
                        popUpTo(Routes.SELECTION) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.CHICKEN_CONFIG,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            ChickenConfigScreen(
                onStartGame = { gameId ->
                    navController.navigate(Routes.chickenMap(gameId)) {
                        popUpTo(Routes.SELECTION) { inclusive = false }
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
                    navController.navigate(Routes.SELECTION) {
                        popUpTo(Routes.SELECTION) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.HUNTER_MAP,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            HunterMapScreen()
        }
    }
}
