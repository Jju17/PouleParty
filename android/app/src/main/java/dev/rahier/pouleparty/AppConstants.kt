package dev.rahier.pouleparty

/**
 * Centralizes all magic numbers and string keys used across the app.
 */
object AppConstants {
    // Preferences
    const val PREFS_NAME = "pouleparty"
    const val PREF_ONBOARDING_COMPLETED = "hasCompletedOnboarding"
    const val PREF_USER_NICKNAME = "userNickname"

    // Firestore Collections
    const val COLLECTION_GAMES = "games"
    const val SUBCOLLECTION_CHICKEN_LOCATIONS = "chickenLocations"
    const val SUBCOLLECTION_HUNTER_LOCATIONS = "hunterLocations"

    // Time Intervals
    const val LOCATION_THROTTLE_MS = 5_000L
    const val LOCATION_UPDATE_INTERVAL_MS = 5_000L
    const val COUNTDOWN_THRESHOLD_SECONDS = 3.0
    const val COUNTDOWN_DISPLAY_MS = 1_500L
    const val WINNER_NOTIFICATION_MS = 4_000L
    const val CODE_COPY_FEEDBACK_MS = 1_000L
    const val CONFETTI_DURATION_MS = 10_000L

    // Game Defaults
    const val DEFAULT_START_DELAY_MS = 300_000L       // 5 minutes
    const val DEFAULT_GAME_DURATION_MS = 3_900_000L   // 65 minutes
    const val DEFAULT_LATITUDE = 50.8466
    const val DEFAULT_LONGITUDE = 4.3528
    const val DEFAULT_INITIAL_RADIUS = 1500.0
    const val DEFAULT_RADIUS_DECLINE = 100.0
    const val DEFAULT_RADIUS_INTERVAL = 5.0   // in minutes
    const val DEFAULT_NUMBER_OF_PLAYERS = 10

    // Location
    const val LOCATION_MIN_DISTANCE_METERS = 10f

    // Game Codes
    const val GAME_CODE_LENGTH = 6
    const val FOUND_CODE_MAX_VALUE = 9999
    const val FOUND_CODE_DIGITS = 4

    // Found Code Cooldown
    const val CODE_MAX_WRONG_ATTEMPTS = 3
    const val CODE_COOLDOWN_MS = 10_000L

    // Zone
    const val OUTSIDE_ZONE_GRACE_PERIOD_SECONDS = 30

    // Map
    const val MAP_CAMERA_ZOOM = 14f

    // Confetti
    const val CONFETTI_PARTICLE_COUNT = 80
}
