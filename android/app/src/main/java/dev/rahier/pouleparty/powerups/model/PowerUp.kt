package dev.rahier.pouleparty.powerups.model

import dev.rahier.pouleparty.R
import androidx.annotation.StringRes
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
    @param:StringRes val titleRes: Int,
    val durationSeconds: Long?,
    val isHunterPowerUp: Boolean,
    @param:StringRes val descriptionRes: Int,
) {
    ZONE_PREVIEW("zonePreview", R.string.powerup_zone_preview, null, true, R.string.powerup_zone_preview_desc),
    // Radar Ping is a glimpse: longer would turn it into tracking. Keep in lockstep with iOS.
    RADAR_PING("radarPing", R.string.powerup_radar_ping, 3, true, R.string.powerup_radar_ping_desc),
    INVISIBILITY("invisibility", R.string.powerup_invisibility, 30, false, R.string.powerup_invisibility_desc),
    ZONE_FREEZE("zoneFreeze", R.string.powerup_zone_freeze, 120, false, R.string.powerup_zone_freeze_desc),
    DECOY("decoy", R.string.powerup_decoy, 20, false, R.string.powerup_decoy_desc),
    JAMMER("jammer", R.string.powerup_jammer, 30, false, R.string.powerup_jammer_desc);

    @get:StringRes
    val targetLabelRes: Int get() = if (isHunterPowerUp) R.string.power_up_target_hunter else R.string.power_up_target_chicken
    val targetEmoji: String get() = if (isHunterPowerUp) "🎯" else "🐔"

    companion object {
        fun fromFirestore(value: String): PowerUpType =
            entries.firstOrNull { it.firestoreValue == value } ?: ZONE_PREVIEW
    }
}
