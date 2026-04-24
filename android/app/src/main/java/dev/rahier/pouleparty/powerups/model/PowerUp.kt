package dev.rahier.pouleparty.powerups.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point

data class PowerUp(
    val id: String = "",
    val type: String = PowerUpType.ZONE_PREVIEW.firestoreValue,
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val spawnedAt: Timestamp = Timestamp.now(),
    val collectedBy: String? = null,
    val collectedAt: Timestamp? = null,
    val activatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null
) {
    @get:Exclude
    val typeEnum: PowerUpType
        get() = PowerUpType.fromFirestore(type)

    @get:Exclude
    val isCollected: Boolean
        get() = collectedBy != null

    @get:Exclude
    val isActivated: Boolean
        get() = activatedAt != null

    @get:Exclude
    val locationPoint: Point
        get() = Point.fromLngLat(location.longitude, location.latitude)

    companion object {
        val mock = PowerUp(
            id = "mock-powerup",
            type = PowerUpType.RADAR_PING.firestoreValue,
            location = GeoPoint(50.8466, 4.3528),
            spawnedAt = Timestamp.now()
        )
    }
}

enum class PowerUpType(
    val firestoreValue: String,
    val title: String,
    val durationSeconds: Long?,
    val isHunterPowerUp: Boolean,
    val description: String
) {
    ZONE_PREVIEW("zonePreview", "Zone Preview", null, true, "Shows the next zone boundary before it shrinks"),
    // 3 s is intentionally short: Radar Ping is a "glimpse" mechanic now —
    // the Chicken broadcasts position continuously so the Hunter just sees
    // where the Chicken is right now, not a tracking window. Longer would
    // turn this into a location stalker. Keep in lockstep with iOS
    // `PowerUpType.radarPing.durationSeconds`.
    RADAR_PING("radarPing", "Radar Ping", 3, true, "Reveals the chicken's position for 3 seconds"),
    INVISIBILITY("invisibility", "Invisibility", 30, false, "Hides the chicken from all hunters for 30 seconds"),
    ZONE_FREEZE("zoneFreeze", "Zone Freeze", 120, false, "Freezes the zone, preventing it from shrinking for 2 minutes"),
    DECOY("decoy", "Decoy", 20, false, "Places a fake chicken signal on hunter maps for 20 seconds"),
    JAMMER("jammer", "Jammer", 30, false, "Scrambles the chicken's position signal, adding noise for 30 seconds");

    val targetLabel: String get() = if (isHunterPowerUp) "Hunter" else "Chicken"
    val targetEmoji: String get() = if (isHunterPowerUp) "🎯" else "🐔"

    companion object {
        fun fromFirestore(value: String): PowerUpType =
            entries.firstOrNull { it.firestoreValue == value } ?: ZONE_PREVIEW
    }
}
