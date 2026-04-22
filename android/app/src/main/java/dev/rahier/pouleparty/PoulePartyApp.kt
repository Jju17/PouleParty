package dev.rahier.pouleparty

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.mapbox.common.MapboxOptions
import com.stripe.android.PaymentConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PoulePartyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        configureMapbox()
        configureStripe()
        createNotificationChannels()
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

    private fun configureStripe() {
        val key = BuildConfig.STRIPE_PUBLISHABLE_KEY
        require(key.startsWith("pk_")) {
            "STRIPE_PUBLISHABLE_KEY missing — set it per product flavor in app/build.gradle.kts"
        }
        PaymentConfiguration.init(applicationContext, key)
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            "game_events",
            "Game Events",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for game starts, zone shrinks, and player events"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
