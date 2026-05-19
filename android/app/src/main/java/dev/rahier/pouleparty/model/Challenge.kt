package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * A global challenge that hunters can validate during a game.
 * Populated by the dev via the Firebase Console in `/challenges/{challengeId}`.
 *
 * `type` is stored as a raw String to keep Firestore `toObject()` happy
 * (same convention as `Game.gameMode` / `PowerUp.type`); use [typeEnum]
 * for typed access.
 */
data class Challenge(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val points: Int = 0,
    val lastUpdated: Timestamp = Timestamp.now(),
    val type: String = ChallengeType.ONE_SHOT.firestoreValue,
    val location: GeoPoint? = null,
    val proximityRadiusMeters: Int? = null,
    val partner: String? = null,
) {
    val typeEnum: ChallengeType
        get() = ChallengeType.fromFirestore(type)
}

enum class ChallengeType(val firestoreValue: String) {
    ONE_SHOT("oneShot"),
    REPEATABLE("repeatable");

    companion object {
        fun fromFirestore(value: String?): ChallengeType =
            values().firstOrNull { it.firestoreValue == value } ?: ONE_SHOT
    }
}
