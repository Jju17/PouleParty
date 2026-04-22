package dev.rahier.pouleparty.powerups.logic

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import java.util.UUID
import kotlin.math.*

/**
 * Deterministic power-up spawn logic. Uses the game's driftSeed to produce
 * identical spawn positions on all clients.
 *
 * Cross-platform parity: mirrors `ios/PouleParty/Components/PowerUpSpawnLogic.swift`.
 * Any change here must be reflected on the iOS side (and vice versa) — both platforms
 * must generate identical spawn positions for the same seed. See `CLAUDE.md`.
 */

fun generatePowerUps(
    center: Point,
    radius: Double,
    count: Int,
    driftSeed: Int,
    batchIndex: Int,
    enabledTypes: List<String> = PowerUpType.entries.map { it.firestoreValue }
): List<PowerUp> {
    // Preserve the input order (matches the TS server reference); filtering
    // through `entries` would re-sort into enum-declaration order, which
    // would pick a different type at the same `itemSeed % count` index than
    // the server ever spawned. Locked by `ParityGoldenTest`.
    val powerUpTypes = enabledTypes.mapNotNull { fv ->
        PowerUpType.entries.firstOrNull { it.firestoreValue == fv }
    }
    if (powerUpTypes.isEmpty()) return emptyList()

    val result = mutableListOf<PowerUp>()
    val baseSeed = driftSeed xor (batchIndex * 7919)

    for (i in 0 until count) {
        val itemSeed = abs(baseSeed * 31 + i * 127)

        // Position within the zone circle using polar coordinates (Long to match iOS Int64)
        val angleSeed = abs(itemSeed.toLong() * 53 xor (i.toLong() * 97))
        val distSeed = abs(itemSeed.toLong() * 79 xor (i.toLong() * 151))

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
        val id = "pu-${batchIndex}-${i}-${abs(itemSeed)}"

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
