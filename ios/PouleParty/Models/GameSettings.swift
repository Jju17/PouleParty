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

// MARK: - Zone radius computation (PP-13 phase 1, mirrors PP-69)

/// `stayInTheZone` final-zone disc rendered on the recap preview + at
/// runtime when the zone collapses. Constant of 50 m matches the
/// Cloud Function spec (PP-69) and the iOS / Android runtime helpers.
let zoneFinalRadiusMeters: Double = 50

/// Minimum buffer between the shrinking start zone and the eventual
/// final disc so that the zone never collapses earlier than the
/// scheduled `endDate`. 200 m matches the PP-69 backend spec.
let zoneInteriorMarginMeters: Double = 200

/// Floor for the initial radius in `stayInTheZone`: even when the start
/// and final pins are very close, the game starts with at least 800 m
/// of breathing room. 800 m matches the PP-69 backend spec.
let zoneMinimumInitialRadiusMeters: Double = 800

/// Computes the initial zone radius for the recap step (PP-13 phase 1).
/// Mirrors the PP-69 Cloud Function formula bit-for-bit so the
/// client-side phase 1 produces the exact same radius the backend will
/// once phase 2 lands.
///
/// - `stayInTheZone`: `max(D × 1.5, D + final + margin, minimumInitial)`
///   with `D = haversine(start, final)`.
/// - `followTheChicken`: the user-picked `radiusHint` (500 / 1000 /
///   2000); falls back to 1000 m if no hint is set yet.
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
        let startLoc = CLLocation(latitude: start.latitude, longitude: start.longitude)
        let finalLoc = CLLocation(latitude: finalCenter.latitude, longitude: finalCenter.longitude)
        let distance = startLoc.distance(from: finalLoc)
        let candidate1 = distance * 1.5
        let candidate2 = distance + zoneFinalRadiusMeters + zoneInteriorMarginMeters
        return max(candidate1, candidate2, zoneMinimumInitialRadiusMeters)
    }
}

/// PP-14 phase 1 — fresh client-side drift seed for the Shuffle button.
/// Must be `> 0` (the runtime PRNG treats 0 as "no drift"). Once
/// PP-69 lands the seed comes from the backend; this helper is dropped
/// at the same time as the helpers above per PP-13 phase 2.
func generateDriftSeed() -> Int {
    var seed = 0
    while seed == 0 {
        seed = Int.random(in: 1...Int.max)
    }
    return seed
}

/// PP-13 — picks a center for the initial zone disc such that the
/// disc contains BOTH `startPin` and `finalCenter` without being
/// centered on either. The user-placed pins then live inside the
/// disc as markers rather than at its origin, which gives a more
/// "fair" feel (the chicken's spawn is not obvious from the disc
/// center alone).
///
/// Algorithm (deterministic via `seed`):
///   1. M = midpoint(startPin, finalCenter).  `dist(M, startPin) =
///      dist(M, finalCenter) = D/2` where D = haversine(startPin, finalCenter).
///   2. The valid centers form a lens (intersection of two discs
///      of radius `R` centered on the two pins). The lens always
///      contains M because `R ≥ D × 1.5` ≥ `D/2`.
///   3. Pick a random offset from M with magnitude ≤ `R - D/2`
///      using a splitmix64-derived PRNG seeded by `seed`. Anywhere
///      in that disc satisfies the containment constraint.
///   4. Project the meter-offset back to lat/lng.
///
/// Mirrors the Kotlin `pickInitialZoneCenter` so iOS and Android
/// agree on the chosen center for the same `seed`.
func pickInitialZoneCenter(
    startPin: CLLocationCoordinate2D,
    finalCenter: CLLocationCoordinate2D,
    radius: Double,
    seed: Int
) -> CLLocationCoordinate2D {
    let startLoc = CLLocation(latitude: startPin.latitude, longitude: startPin.longitude)
    let finalLoc = CLLocation(latitude: finalCenter.latitude, longitude: finalCenter.longitude)
    let distance = startLoc.distance(from: finalLoc)
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
