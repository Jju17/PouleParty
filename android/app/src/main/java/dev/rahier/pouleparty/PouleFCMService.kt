package dev.rahier.pouleparty

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PouleFCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("PouleFCMService", "Message received: ${message.notification?.title}")
    }

    private fun saveTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("fcmTokens")
            .document(userId)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnFailureListener { e ->
                Log.e("PouleFCMService", "Failed to save FCM token", e)
            }
    }
}
