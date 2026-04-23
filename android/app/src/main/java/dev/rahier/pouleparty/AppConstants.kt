package dev.rahier.pouleparty

/**
 * Centralizes all magic numbers and string keys used across the app.
 */
object AppConstants {
    // Preferences
    const val PREFS_NAME = "pouleparty"
    const val PREF_ONBOARDING_COMPLETED = "hasCompletedOnboarding"
    const val PREF_USER_NICKNAME = "userNickname"
    const val PREF_IS_MUSIC_MUTED = "isMusicMuted"
    const val PREF_LAST_MIGRATED_VERSION = "lastMigratedVersion"

    // Firestore Collections
    const val COLLECTION_GAMES = "games"
    const val SUBCOLLECTION_CHICKEN_LOCATIONS = "chickenLocations"
    const val SUBCOLLECTION_HUNTER_LOCATIONS = "hunterLocations"
    const val SUBCOLLECTION_POWER_UPS = "powerUps"
    const val SUBCOLLECTION_REGISTRATIONS = "registrations"
    const val SUBCOLLECTION_CHALLENGE_COMPLETIONS = "challengeCompletions"
    const val COLLECTION_USERS = "users"
    const val COLLECTION_CHALLENGES = "challenges"

    // Pending registration banner
    const val PREF_PENDING_REGISTRATION_GAME_ID = "pendingRegistrationGameId"
    const val PREF_PENDING_REGISTRATION_GAME_CODE = "pendingRegistrationGameCode"
    const val PREF_PENDING_REGISTRATION_TEAM_NAME = "pendingRegistrationTeamName"
    const val PREF_PENDING_REGISTRATION_START_MS = "pendingRegistrationStartMs"
    const val PREF_PENDING_REGISTRATION_IS_FINISHED = "pendingRegistrationIsFinished"
    /** Last game id the user explicitly dismissed from the "active game" Home
     *  banner. Skipped in findActiveGame so the banner doesn't reappear on the
     *  next onResume for a game the user actively hid. (Legacy single-value;
     *  superseded by `PREF_DISMISSED_ACTIVE_GAME_IDS` — kept to avoid breaking
     *  existing installs that still write here from older app versions.) */
    const val PREF_DISMISSED_ACTIVE_GAME_ID = "dismissedActiveGameId"
    /** Set of game ids the user explicitly dismissed from the Home banner.
     *  Persisted cross-session so a dismiss sticks until the game ends or the
     *  phase flips (e.g. upcoming → inProgress resurfaces the banner since we
     *  clear on manual "Reprendre" tap). */
    const val PREF_DISMISSED_ACTIVE_GAME_IDS = "dismissedActiveGameIds"

    // Time Intervals
    const val LOCATION_THROTTLE_MS = 5_000L
    const val LOCATION_UPDATE_INTERVAL_MS = 5_000L
    const val COUNTDOWN_THRESHOLD_SECONDS = 3.0
    const val COUNTDOWN_DISPLAY_MS = 1_500L
    const val WINNER_NOTIFICATION_MS = 4_000L
    const val CODE_COPY_FEEDBACK_MS = 1_000L
    const val POWER_UP_NOTIFICATION_MS = 2_000L
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

    // Pricing (Admin-defined)
    const val FLAT_PRICE_PER_PLAYER_CENTS = 300 // 3€ per player
    const val FLAT_MIN_PLAYERS = 6
    const val FLAT_MAX_PLAYERS = 50
    const val DEPOSIT_AMOUNT_CENTS = 1000 // 10€ deposit
    const val COMMISSION_PERCENT = 15.0

    // Location
    const val LOCATION_MIN_DISTANCE_METERS = 10f

    // Game Codes
    const val GAME_CODE_LENGTH = 6
    const val FOUND_CODE_MAX_VALUE = 9999
    const val FOUND_CODE_DIGITS = 4

    // Found Code Cooldown
    const val CODE_MAX_WRONG_ATTEMPTS = 3
    const val CODE_COOLDOWN_MS = 10_000L

    // Zone — grace period disabled, kept for future game mode
    // const val OUTSIDE_ZONE_GRACE_PERIOD_SECONDS = 30

    // Power-Ups
    const val JAMMER_NOISE_DEGREES = 0.0036 // ~200m noise for jammer power-up
    const val POWER_UP_COLLECTION_RADIUS_METERS = 30.0
    const val POWER_UP_INITIAL_BATCH_SIZE = 5
    const val POWER_UP_PERIODIC_BATCH_SIZE = 2

    // Map
    const val MAP_CAMERA_ZOOM = 14f

    // Confetti
    const val CONFETTI_PARTICLE_COUNT = 80
}
