package dev.rahier.pouleparty

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.mapbox.common.MapboxOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PoulePartyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        configureAppCheck()
        configureMapbox()
        createNotificationChannels()
    }

    /**
     * CRIT-4 (audit 2026-05-17): App Check protects `createPendingRegistration`
     * (Stripe payment endpoint) against bot spam. Without enforcement on the
     * Function side, the SDK still ships tokens that Firebase tracks on the
     * App Check monitoring dashboard — useful to verify token coverage before
     * flipping `enforceAppCheck: true` on the server.
     *
     * - Debug builds (BuildConfig.DEBUG = true): Debug Provider. Generates a
     *   per-device token at first launch, logged to Logcat. The token must be
     *   registered in Firebase Console > App Check > Android app > « Gérer
     *   les jetons de débogage ». Used for staging, instrumented tests, and
     *   on-device development.
     * - Release builds: Play Integrity. Attestation comes from the
     *   Google-signed Play Store binary running on a genuine Android device.
     *   No manual setup per device; just requires Play Integrity linked to
     *   the Firebase project's Cloud project on Play Console (see
     *   project_appcheck_pending.md note).
     */
    private fun configureAppCheck() {
        val factory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
    }

    /**
     * Mapbox normally picks up `@string/mapbox_access_token` automatically, but
     * R8 resource-shrinking in release builds strips that string because no
     * Kotlin code references it, Mapbox's reflection-style lookup is invisible
     * to the shrinker. Result: `MapboxConfigurationException` on every map
     * render in release builds (1.8.1 build 24 crash).
     * Setting the token programmatically in onCreate fixes both:
     *  - the token is applied before any map composable runs,
     *  - `getString(R.string.mapbox_access_token)` is a tracked reference that
     *    keeps the resource alive through resource shrinking.
     */
    private fun configureMapbox() {
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
    }

    /**
     * HIGH-12 (audit 2026-05-17): centralized notification-channel
     * registration. The location FGS channel used to be created lazily
     * by `LocationForegroundService.ensureChannel` on every
     * `startForeground` call, which works today but breaks the moment a
     * refactor calls `startForeground` before that lazy registration —
     * Android 12+ then kills the FGS within 5 s with
     * `ForegroundServiceDidNotStartInTimeException`. Registering both
     * channels at app start removes the timing dependency entirely.
     */
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "game_events",
                "Game Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for game starts, zone shrinks, and player events"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                dev.rahier.pouleparty.service.LocationForegroundService.CHANNEL_ID,
                getString(R.string.location_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.location_service_channel_description)
                setShowBadge(false)
            }
        )
    }
}
