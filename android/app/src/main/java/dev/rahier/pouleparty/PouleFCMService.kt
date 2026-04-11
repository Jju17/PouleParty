package dev.rahier.pouleparty

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PouleFCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PouleFCMService"
        private const val CHANNEL_ID = "game_events"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = resolveString(
            message.notification?.titleLocalizationKey,
            message.notification?.titleLocalizationArgs,
            fallback = message.notification?.title
        )
        val body = resolveString(
            message.notification?.bodyLocalizationKey,
            message.notification?.bodyLocalizationArgs,
            fallback = message.notification?.body
        )
        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            Log.w(TAG, "Received notification with no title or body")
            return
        }
        showNotification(title, body, message.data)
    }

    /**
     * Resolves a localized string from a string resource key + args, falling back to [fallback]
     * if the key is missing or unknown.
     */
    private fun resolveString(key: String?, args: Array<String>?, fallback: String?): String? {
        if (key.isNullOrBlank()) return fallback
        val resId = resources.getIdentifier(key, "string", packageName)
        if (resId == 0) {
            Log.w(TAG, "String resource not found for key: $key")
            return fallback
        }
        return if (args != null && args.isNotEmpty()) {
            try {
                getString(resId, *args)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to format string $key with args ${args.toList()}", e)
                getString(resId)
            }
        } else {
            getString(resId)
        }
    }

    private fun showNotification(title: String?, body: String?, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission", e)
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection(AppConstants.COLLECTION_FCM_TOKENS)
            .document(userId)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save FCM token", e)
            }
    }
}
