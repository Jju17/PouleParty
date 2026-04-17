package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp

/**
 * A global challenge that hunters can validate during a game.
 * Populated by the dev via the Firebase Console in `/challenges/{challengeId}`.
 */
data class Challenge(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val points: Int = 0,
    val lastUpdated: Timestamp = Timestamp.now()
)
