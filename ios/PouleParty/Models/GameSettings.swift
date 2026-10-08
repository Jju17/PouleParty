//
//  GameSettings.swift
//  PouleParty
//

import CoreLocation
import Foundation

func calculateNormalModeSettings(initialRadius: Double, gameDurationMinutes: Double) -> (interval: Double, decline: Double) {
    let numberOfShrinks = gameDurationMinutes / AppConstants.normalModeFixedInterval
    guard numberOfShrinks > 0 else { return (AppConstants.normalModeFixedInterval, 0) }
    let declinePerUpdate = (initialRadius - AppConstants.normalModeMinimumRadius) / numberOfShrinks
    return (AppConstants.normalModeFixedInterval, max(0, declinePerUpdate))
}

let zoneFinalRadiusMeters: Double = 50

let zoneInteriorMarginMeters: Double = 200

let zoneMinimumInitialRadiusMeters: Double = 800

func computeZoneRadius(
    start: CLLocationCoordinate2D,
    finalCenter: CLLocationCoordinate2D?,
    gameMode: Game.GameMode,
    radiusHint: Double?
) -> Double {
    switch gameMode {
    case .followTheChicken:
        guard let hint = radiusHint else { return 1000 }
        return [500.0, 1000.0, 2000.0].contains(hint) ? hint : 1000
    case .stayInTheZone:
        guard let finalCenter else { return zoneMinimumInitialRadiusMeters }
        let distance = distanceMeters(start, finalCenter)
        let candidate1 = distance * 1.5
        let candidate2 = distance + zoneFinalRadiusMeters + zoneInteriorMarginMeters
        return max(candidate1, candidate2, zoneMinimumInitialRadiusMeters)
    }
}

func generateDriftSeed() -> Int {
    var seed = 0
    while seed == 0 {
        seed = Int.random(in: 1...Int(Int32.max))
    }
    return seed
}

func pickInitialZoneCenter(
    startPin: CLLocationCoordinate2D,
    finalCenter: CLLocationCoordinate2D,
    radius: Double,
    seed: Int
) -> CLLocationCoordinate2D {
    let distance = distanceMeters(startPin, finalCenter)
    let midLat = (startPin.latitude + finalCenter.latitude) / 2
    let midLng = (startPin.longitude + finalCenter.longitude) / 2

    // Lens shrinks to a single point when pins exactly fill the disc;
    // clamp to a small positive maxOffset so we still randomize a bit.
    let maxOffset = max(0, radius - distance / 2)

    // Deterministic two-stream PRNG from the seed.
    var s = UInt64(bitPattern: Int64(seed))
    if s == 0 { s = 1 }
    let angle = Double(splitmix64Next(state: &s)) / Double(UInt64.max) * 2 * .pi
    let mag = sqrt(Double(splitmix64Next(state: &s)) / Double(UInt64.max)) * maxOffset

    let dxMeters = mag * cos(angle)
    let dyMeters = mag * sin(angle)

    // Meters → degrees. 1° latitude ≈ 111_111 m everywhere; 1°
    // longitude ≈ 111_111 × cos(lat) m at this latitude.
    let dLat = dyMeters / 111_111
    let cosLat = cos(midLat * .pi / 180)
    let dLng = cosLat == 0 ? 0 : dxMeters / (111_111 * cosLat)

    return CLLocationCoordinate2D(latitude: midLat + dLat, longitude: midLng + dLng)
}

private func splitmix64Next(state: inout UInt64) -> UInt64 {
    state &+= 0x9E3779B97F4A7C15
    var z = state
    z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
    z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
    return z ^ (z >> 31)
}
