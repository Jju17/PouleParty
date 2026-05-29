package dev.rahier.pouleparty.config

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.AdminCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime-tunable game values backed by Firebase Remote Config.
 *
 * Getters are synchronous and return the currently-activated value, falling
 * back to the compiled defaults (registered via [FirebaseRemoteConfig.setDefaultsAsync])
 * until the first fetch activates. Remote Config only ever *overrides* the
 * compiled defaults, so the app behaves correctly offline or before the first
 * fetch. The Remote Config keys and default values mirror the iOS
 * `RemoteConfigClient` exactly.
 */
@Singleton
class RemoteConfigProvider @Inject constructor() {
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                KEY_ADMIN_CODE to AdminCode.VALUE,
                KEY_CODE_MAX_WRONG_ATTEMPTS to AppConstants.CODE_MAX_WRONG_ATTEMPTS.toLong(),
                // Stored in seconds for cross-platform parity with iOS.
                KEY_CODE_COOLDOWN_SECONDS to (AppConstants.CODE_COOLDOWN_MS / 1000L),
                KEY_DEFAULT_INITIAL_RADIUS to AppConstants.DEFAULT_INITIAL_RADIUS,
            )
        )
        remoteConfig.fetchAndActivate()
    }

    val adminCode: String
        get() = remoteConfig.getString(KEY_ADMIN_CODE).ifEmpty { AdminCode.VALUE }

    val codeMaxWrongAttempts: Int
        get() = remoteConfig.getLong(KEY_CODE_MAX_WRONG_ATTEMPTS).toInt()

    val codeCooldownMs: Long
        get() = remoteConfig.getLong(KEY_CODE_COOLDOWN_SECONDS) * 1000L

    val defaultInitialRadius: Double
        get() = remoteConfig.getDouble(KEY_DEFAULT_INITIAL_RADIUS)

    companion object {
        private const val KEY_ADMIN_CODE = "admin_code"
        private const val KEY_CODE_MAX_WRONG_ATTEMPTS = "found_code_max_wrong_attempts"
        private const val KEY_CODE_COOLDOWN_SECONDS = "found_code_cooldown_seconds"
        private const val KEY_DEFAULT_INITIAL_RADIUS = "default_initial_radius_meters"
    }
}
