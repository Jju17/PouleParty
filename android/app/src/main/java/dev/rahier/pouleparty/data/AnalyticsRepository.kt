package dev.rahier.pouleparty.data

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around FirebaseAnalytics exposing typed convenience methods
 * for the app's custom events. Mirrors iOS AnalyticsClient.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    fun gameCreated(gameMode: String, maxPlayers: Int, pricingModel: String, powerUpsEnabled: Boolean) {
        firebaseAnalytics.logEvent("game_created") {
            param("game_mode", gameMode)
            param("max_players", maxPlayers.toLong())
            param("pricing_model", pricingModel)
            param("power_ups_enabled", if (powerUpsEnabled) 1L else 0L)
        }
    }

    fun gameJoined(gameMode: String, gameCode: String) {
        firebaseAnalytics.logEvent("game_joined") {
            param("game_mode", gameMode)
            param("game_code", gameCode)
        }
    }

    fun gameStarted(gameMode: String) {
        firebaseAnalytics.logEvent("game_started") {
            param("game_mode", gameMode)
        }
    }

    fun gameEnded(reason: String, winnersCount: Int) {
        firebaseAnalytics.logEvent("game_ended") {
            param("reason", reason)
            param("winners_count", winnersCount.toLong())
        }
    }

    fun hunterFoundChicken(attempts: Int) {
        firebaseAnalytics.logEvent("hunter_found_chicken") {
            param("attempts", attempts.toLong())
        }
    }

    fun hunterWrongCode(attemptNumber: Int) {
        firebaseAnalytics.logEvent("hunter_wrong_code") {
            param("attempt_number", attemptNumber.toLong())
        }
    }

    fun powerUpCollected(type: String, role: String) {
        firebaseAnalytics.logEvent("power_up_collected") {
            param("type", type)
            param("role", role)
        }
    }

    fun powerUpActivated(type: String, role: String) {
        firebaseAnalytics.logEvent("power_up_activated") {
            param("type", type)
            param("role", role)
        }
    }

    fun registrationCompleted(pricingModel: String) {
        firebaseAnalytics.logEvent("registration_completed") {
            param("pricing_model", pricingModel)
        }
    }

    fun onboardingCompleted() {
        firebaseAnalytics.logEvent("onboarding_completed", null)
    }
}
