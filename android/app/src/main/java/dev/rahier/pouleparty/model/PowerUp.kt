package dev.rahier.pouleparty.model

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
    val isHunterPowerUp: Boolean
) {
    ZONE_PREVIEW("zonePreview", "Zone Preview", null, true),
    RADAR_PING("radarPing", "Radar Ping", 10, true),
    INVISIBILITY("invisibility", "Invisibility", 30, false),
    ZONE_FREEZE("zoneFreeze", "Zone Freeze", 120, false);

    companion object {
        fun fromFirestore(value: String): PowerUpType =
            entries.firstOrNull { it.firestoreValue == value } ?: ZONE_PREVIEW
    }
}
