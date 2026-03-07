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

private let powerUpTypes: [PowerUp.PowerUpType] = PowerUp.PowerUpType.allCases

func generatePowerUps(
    center: CLLocationCoordinate2D,
    radius: Double,
    count: Int,
    driftSeed: Int,
    batchIndex: Int
) -> [PowerUp] {
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
        let id = "pu-\(batchIndex)-\(i)-\(abs(itemSeed) % 100000)"

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
