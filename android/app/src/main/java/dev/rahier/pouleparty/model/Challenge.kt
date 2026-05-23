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
    val level: Int = 1,
    // `0` is a sentinel for "not yet numbered". `migrateChallengesV2`
    // backfills every doc missing or set to 0 with the next free
    // integer within its `level`; new docs created via the Console
    // must ship with `number > 0`.
    val number: Int = 0,
    val titleByLocale: Map<String, String> = emptyMap(),
    val bodyByLocale: Map<String, String> = emptyMap(),
) {
    val typeEnum: ChallengeType
        get() = ChallengeType.fromFirestore(type)

    /** Locale → text with a 3-level cascade: requested locale, then
     *  `"fr"` (the D-Day FR-first audience), then the legacy `title`.
     *  Empty strings count as missing so a partially-populated doc
     *  falls through to a usable value. */
    fun localizedTitle(locale: String): String =
        titleByLocale[locale]?.takeIf { it.isNotEmpty() }
            ?: titleByLocale["fr"]?.takeIf { it.isNotEmpty() }
            ?: title

    fun localizedBody(locale: String): String =
        bodyByLocale[locale]?.takeIf { it.isNotEmpty() }
            ?: bodyByLocale["fr"]?.takeIf { it.isNotEmpty() }
            ?: body
}

enum class ChallengeType(val firestoreValue: String) {
    ONE_SHOT("oneShot"),
    REPEATABLE("repeatable");

    companion object {
        fun fromFirestore(value: String?): ChallengeType =
            values().firstOrNull { it.firestoreValue == value } ?: ONE_SHOT
    }
}
