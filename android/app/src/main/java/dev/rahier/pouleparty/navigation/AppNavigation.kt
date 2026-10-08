package dev.rahier.pouleparty.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.gamecreation.GameCreationScreen
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapScreen
import dev.rahier.pouleparty.ui.huntermap.HunterMapScreen
import dev.rahier.pouleparty.ui.gamemastermap.GameMasterMapScreen
import dev.rahier.pouleparty.ui.onboarding.OnboardingScreen
import dev.rahier.pouleparty.ui.demo.DemoModeScreen
import dev.rahier.pouleparty.ui.home.HomeScreen
import dev.rahier.pouleparty.ui.settings.SettingsScreen
import dev.rahier.pouleparty.ui.validation.ValidationQueueScreen
import dev.rahier.pouleparty.ui.victory.VictoryScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val GAME_CREATION = "game_creation/{gameId}?isAdminCreation={isAdminCreation}&isDebugGame={isDebugGame}"
    const val CHICKEN_MAP = "chicken_map/{gameId}?becameChicken={becameChicken}"
    const val HUNTER_MAP = "hunter_map/{gameId}/{hunterName}"
    const val GAME_MASTER_MAP = "game_master_map/{gameId}"
    const val VICTORY = "victory/{gameId}/{hunterName}/{hunterId}/{isChicken}"
    const val SETTINGS = "settings"
    const val VALIDATION_QUEUE = "validation_queue/{gameId}"
    const val DEMO = "demo"
    fun gameCreation(gameId: String, isAdminCreation: Boolean = false, isDebugGame: Boolean = false) =
        "game_creation/$gameId?isAdminCreation=$isAdminCreation&isDebugGame=$isDebugGame"
    fun chickenMap(gameId: String, becameChicken: Boolean = false) =
        "chicken_map/$gameId?becameChicken=$becameChicken"
    fun hunterMap(gameId: String, hunterName: String) = "hunter_map/$gameId/${Uri.encode(hunterName)}"
    fun gameMasterMap(gameId: String) = "game_master_map/$gameId"
    fun victory(gameId: String, hunterName: String, hunterId: String, isChicken: Boolean = false) =
        "victory/$gameId/${Uri.encode(hunterName)}/${Uri.encode(hunterId)}/$isChicken"
    fun validationQueue(gameId: String) = "validation_queue/$gameId"
}

@Composable
fun AppNavigation(session: AppSessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val sessionState by session.state.collectAsStateWithLifecycle()

    if (!sessionState.isReady) return

    val startDestination = remember { if (sessionState.hasCompletedOnboarding) Routes.HOME else Routes.ONBOARDING }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingCompleted = { nickname ->
                    session.onOnboardingCompleted(nickname)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToCreateParty = { gameId, isAdminCreation, isDebugGame ->
                    navController.navigate(Routes.gameCreation(gameId, isAdminCreation, isDebugGame)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
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
                onNavigateToGameMasterMap = { gameId ->
                    navController.navigate(Routes.gameMasterMap(gameId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToVictory = { gameId ->
                    navController.navigate(Routes.victory(gameId, "", "", isChicken = false)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToDemoMode = {
                    navController.navigate(Routes.DEMO)
                }
            )
        }

        composable(Routes.DEMO) {
            DemoModeScreen(
                onExit = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.GAME_CREATION,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("isAdminCreation") { type = NavType.BoolType; defaultValue = false },
                navArgument("isDebugGame") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            GameCreationScreen(
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
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("becameChicken") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            val gameId = it.arguments?.getString("gameId") ?: ""
            ChickenMapScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onVictory = { gid ->
                    navController.navigate(Routes.victory(gid, "", "", isChicken = true)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onOpenValidationQueue = {
                    navController.navigate(Routes.validationQueue(gameId))
                },
                // PP-107: chicken was swapped to a plain hunter mid-`waiting`.
                onBecameHunter = { gid, teamName ->
                    navController.navigate(Routes.hunterMap(gid, teamName)) {
                        popUpTo(Routes.chickenMap(gameId)) { inclusive = true }
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
            val gameId = it.arguments?.getString("gameId") ?: ""
            val hunterName = it.arguments?.getString("hunterName") ?: ""
            HunterMapScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onVictory = { gid, name, hunterId ->
                    navController.navigate(Routes.victory(gid, name, hunterId, isChicken = false)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                // PP-107: this hunter was re-designated chicken mid-`waiting`.
                onBecameChicken = { gid ->
                    navController.navigate(Routes.chickenMap(gid, becameChicken = true)) {
                        popUpTo(Routes.hunterMap(gameId, hunterName)) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.GAME_MASTER_MAP,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            val gameId = it.arguments?.getString("gameId") ?: ""
            GameMasterMapScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOpenValidationQueue = {
                    navController.navigate(Routes.validationQueue(gameId))
                },
                onVictory = { gid ->
                    navController.navigate(Routes.victory(gid, "", "", isChicken = false)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }

        composable(
            route = Routes.VALIDATION_QUEUE,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            ValidationQueueScreen(
                onDismiss = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.VICTORY,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("hunterName") { type = NavType.StringType },
                navArgument("hunterId") { type = NavType.StringType },
                navArgument("isChicken") { type = NavType.BoolType }
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
