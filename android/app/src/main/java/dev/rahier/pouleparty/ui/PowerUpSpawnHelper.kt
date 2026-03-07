package dev.rahier.pouleparty.ui

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType
import java.util.UUID
import kotlin.math.*

/**
 * Deterministic power-up spawn logic. Uses the game's driftSeed to produce
 * identical spawn positions on all clients.
 */

private val powerUpTypes = PowerUpType.entries.toList()

fun generatePowerUps(
    center: Point,
    radius: Double,
    count: Int,
    driftSeed: Int,
    batchIndex: Int
): List<PowerUp> {
    val result = mutableListOf<PowerUp>()
    val baseSeed = driftSeed xor (batchIndex * 7919)

    for (i in 0 until count) {
        val itemSeed = abs(baseSeed * 31 + i * 127)

        // Position within the zone circle using polar coordinates
        val angleSeed = abs(itemSeed * 53 xor (i * 97))
        val distSeed = abs(itemSeed * 79 xor (i * 151))

        val angle = (angleSeed % 36000) / 36000.0 * 2.0 * PI
        val distFraction = (distSeed % 10000) / 10000.0
        // sqrt for uniform area distribution, 0.85 factor to keep inside zone
        val distance = radius * 0.85 * sqrt(distFraction)

        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLng = 111_320.0 * cos(center.latitude() * PI / 180.0)

        val dLat = (distance * cos(angle)) / metersPerDegreeLat
        val dLng = (distance * sin(angle)) / metersPerDegreeLng

        val lat = center.latitude() + dLat
        val lng = center.longitude() + dLng

        // Alternate between hunter and chicken power-ups
        val typeIndex = (itemSeed % powerUpTypes.size)
        val type = powerUpTypes[typeIndex]

        // Deterministic ID based on seed for idempotency
        val id = "pu-${batchIndex}-${i}-${abs(itemSeed) % 100000}"

        result.add(
            PowerUp(
                id = id,
                type = type.firestoreValue,
                location = GeoPoint(lat, lng),
                spawnedAt = Timestamp.now()
            )
        )
    }

    return result
}
