//
//  PowerUpSpawnLogic.swift
//  PouleParty
//
//  Deterministic power-up spawn logic. Uses the game's driftSeed to produce
//  identical spawn positions on all clients.
//

import CoreLocation
import Foundation
import FirebaseFirestore

func generatePowerUps(
    center: CLLocationCoordinate2D,
    radius: Double,
    count: Int,
    driftSeed: Int,
    batchIndex: Int,
    enabledTypes: [String] = PowerUp.PowerUpType.allCases.map(\.rawValue)
) -> [PowerUp] {
    let powerUpTypes = PowerUp.PowerUpType.allCases.filter { enabledTypes.contains($0.rawValue) }
    guard !powerUpTypes.isEmpty else { return [] }

    var result: [PowerUp] = []
    let baseSeed = driftSeed ^ (batchIndex &* 7919)

    for i in 0..<count {
        let itemSeed = abs(baseSeed &* 31 &+ i &* 127)

        // Position within the zone circle using polar coordinates
        let angleSeed = abs(itemSeed &* 53 ^ (i &* 97))
        let distSeed = abs(itemSeed &* 79 ^ (i &* 151))

        let angle = Double(angleSeed % 36000) / 36000.0 * 2.0 * .pi
        let distFraction = Double(distSeed % 10000) / 10000.0
        // sqrt for uniform area distribution, 0.85 factor to keep inside zone
        let distance = radius * 0.85 * distFraction.squareRoot()

        let metersPerDegreeLat = 111_320.0
        let metersPerDegreeLng = 111_320.0 * cos(center.latitude * .pi / 180.0)

        let dLat = (distance * cos(angle)) / metersPerDegreeLat
        let dLng = (distance * sin(angle)) / metersPerDegreeLng

        let lat = center.latitude + dLat
        let lng = center.longitude + dLng

        // Alternate between power-up types
        let typeIndex = itemSeed % powerUpTypes.count
        let type = powerUpTypes[typeIndex]

        // Deterministic ID based on seed for idempotency
        let id = "pu-\(batchIndex)-\(i)-\(abs(itemSeed))"

        result.append(
            PowerUp(
                id: id,
                type: type,
                location: GeoPoint(latitude: lat, longitude: lng),
                spawnedAt: Timestamp(date: .now)
            )
        )
    }

    return result
}

/// Snaps power-up locations to the nearest walkable road.
/// Falls back to original locations if the API call fails.
func snapPowerUpsToRoads(_ powerUps: [PowerUp]) async -> [PowerUp] {
    guard !powerUps.isEmpty else { return powerUps }

    let coordinates = powerUps.map { $0.coordinate }
    let snapped = await RoadSnapService.snapToRoads(coordinates)

    return zip(powerUps, snapped).map { original, snappedCoord in
        PowerUp(
            id: original.id,
            type: original.type,
            location: GeoPoint(latitude: snappedCoord.latitude, longitude: snappedCoord.longitude),
            spawnedAt: original.spawnedAt
        )
    }
}
