package dev.rahier.pouleparty

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp
import com.stripe.android.PaymentConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PoulePartyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        configureStripe()
        createNotificationChannels()
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
