//
//  PowerUpSpawnLogic.swift
//  PouleParty
//
//  Deterministic power-up spawn logic. Uses the game's driftSeed to produce
//  identical spawn positions on all clients.
//
//  Cross-platform parity: mirrors `android/.../ui/PowerUpSpawnHelper.kt`.
//  Any change here must be reflected on the Android side (and vice versa) —
//  both platforms must generate identical spawn positions for the same seed.
//  See `CLAUDE.md`.
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
    // Preserve the input order (matches the TS server reference); filtering
    // through `allCases` would re-sort into enum-declaration order, which
    // would pick a different type at the same `itemSeed % count` index than
    // the server ever spawned. Locked by `ParityGoldenTests`.
    let powerUpTypes = enabledTypes.compactMap(PowerUp.PowerUpType.init(rawValue:))
    guard !powerUpTypes.isEmpty else { return [] }

    var result: [PowerUp] = []
    let baseSeed = driftSeed ^ (batchIndex * 7919)

    for i in 0..<count {
        let itemSeed = abs(baseSeed * 31 + i * 127)

        // Position within the zone circle using polar coordinates
        let angleSeed = abs(Int64(itemSeed) * 53 ^ (Int64(i) * 97))
        let distSeed = abs(Int64(itemSeed) * 79 ^ (Int64(i) * 151))

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

