package dev.rahier.pouleparty.navigation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import dev.rahier.pouleparty.MigrationManager
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.ui.gamecreation.GameCreationScreen
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapScreen
import dev.rahier.pouleparty.ui.huntermap.HunterMapScreen
import dev.rahier.pouleparty.ui.onboarding.OnboardingScreen
import dev.rahier.pouleparty.ui.home.HomeScreen
import dev.rahier.pouleparty.ui.planselection.PlanSelectionScreen
import dev.rahier.pouleparty.ui.planselection.PricingParams
import dev.rahier.pouleparty.ui.settings.SettingsScreen
import dev.rahier.pouleparty.ui.victory.VictoryScreen
import dev.rahier.pouleparty.util.getTrimmedString

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PLAN_SELECTION = "plan_selection"
    const val GAME_CREATION = "game_creation/{gameId}/{pricingModel}/{numberOfPlayers}/{pricePerPlayerCents}/{depositAmountCents}"
    const val CHICKEN_MAP = "chicken_map/{gameId}"
    const val HUNTER_MAP = "hunter_map/{gameId}/{hunterName}"
    const val VICTORY = "victory/{gameId}/{hunterName}/{hunterId}/{isChicken}"
    const val SETTINGS = "settings"

    fun gameCreation(gameId: String, pricingModel: String = "free", numberOfPlayers: Int = 5, pricePerPlayerCents: Int = 0, depositAmountCents: Int = 0) =
        "game_creation/$gameId/$pricingModel/$numberOfPlayers/$pricePerPlayerCents/$depositAmountCents"
    fun chickenMap(gameId: String) = "chicken_map/$gameId"
    fun hunterMap(gameId: String, hunterName: String) = "hunter_map/$gameId/${Uri.encode(hunterName)}"
    fun victory(gameId: String, hunterName: String, hunterId: String, isChicken: Boolean = false) =
        "victory/$gameId/${Uri.encode(hunterName)}/${Uri.encode(hunterId)}/$isChicken"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

    var authAttemptComplete by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }

    LaunchedEffect(Unit) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            try {
                val result = FirebaseAuth.getInstance().signInAnonymously().await()
                if (result.additionalUserInfo?.isNewUser == true) {
                    // Fresh Firebase user was created (first install, or cached uid became invalid
                    // after server-side deletion). Force the user back through onboarding so they
                    // re-enter a nickname tied to the new uid.
                    prefs.edit().putBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, false).apply()
                }
            } catch (e: Exception) {
                Log.e("AppNavigation", "Anonymous sign-in failed", e)
                authAttemptComplete = true
                return@LaunchedEffect
            }
        }
        authAttemptComplete = true
        // Save FCM token + run migration after auth is ready
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val data = mutableMapOf<String, Any>(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to Timestamp.now()
                )
                // Migration: include nickname from local prefs if not yet in Firestore
                val packageVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                val lastMigrated = prefs.getString(AppConstants.PREF_LAST_MIGRATED_VERSION, "0.0.0") ?: "0.0.0"
                if (MigrationManager.compareVersions(lastMigrated, "1.4.0") < 0) {
                    val nickname = prefs.getTrimmedString(AppConstants.PREF_USER_NICKNAME)
                    if (nickname.isNotEmpty()) {
                        data["nickname"] = nickname
                    }
                    prefs.edit().putString(AppConstants.PREF_LAST_MIGRATED_VERSION, packageVersion).apply()
                }
                FirebaseFirestore.getInstance()
                    .collection(AppConstants.COLLECTION_USERS)
                    .document(userId)
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e("AppNavigation", "Failed to save user profile", e)
            }
        }
    }

    if (!authAttemptComplete) return

    val hasCompletedOnboarding = prefs.getBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, false)
    val startDestination = if (hasCompletedOnboarding) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingCompleted = { nickname ->
                    prefs.edit()
                        .putBoolean(AppConstants.PREF_ONBOARDING_COMPLETED, true)
                        .putString(AppConstants.PREF_USER_NICKNAME, nickname)
                        .apply()
                    FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .set(
                                mapOf("nickname" to nickname, "updatedAt" to Timestamp.now()),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                    }
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PLAN_SELECTION) {
            PlanSelectionScreen(
                onPlanSelected = { params ->
                    val gameId = java.util.UUID.randomUUID().toString()
                    navController.navigate(
                        Routes.gameCreation(
                            gameId,
                            params.pricingModel,
                            params.numberOfPlayers,
                            params.pricePerPlayerCents,
                            params.depositAmountCents
                        )
                    ) {
                        popUpTo(Routes.PLAN_SELECTION) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToPlanSelection = {
                    navController.navigate(Routes.PLAN_SELECTION)
                },
                onNavigateToGameCreation = { gameId, pricingModel, numberOfPlayers, pricePerPlayerCents, depositAmountCents ->
                    navController.navigate(
                        Routes.gameCreation(gameId, pricingModel, numberOfPlayers, pricePerPlayerCents, depositAmountCents)
                    ) {
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
                onNavigateToVictory = { gameId ->
                    navController.navigate(Routes.victory(gameId, "", "", isChicken = false)) {
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
            route = Routes.GAME_CREATION,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("pricingModel") { type = NavType.StringType; defaultValue = "free" },
                navArgument("numberOfPlayers") { type = NavType.IntType; defaultValue = 5 },
                navArgument("pricePerPlayerCents") { type = NavType.IntType; defaultValue = 0 },
                navArgument("depositAmountCents") { type = NavType.IntType; defaultValue = 0 }
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
            arguments = listOf(navArgument("gameId") { type = NavType.StringType })
        ) {
            ChickenMapScreen(
                onGoToMenu = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onVictory = { gameId ->
                    navController.navigate(Routes.victory(gameId, "", "", isChicken = true)) {
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
                    navController.navigate(Routes.victory(gameId, hunterName, hunterId, isChicken = false)) {
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
