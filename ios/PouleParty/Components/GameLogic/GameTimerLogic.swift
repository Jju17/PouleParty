//
//  GameTimerLogic.swift
//  PouleParty
//
//  Shared pure functions for timer, countdown, radius, and winner logic
//  used by both ChickenMapFeature and HunterMapFeature.
//
//  Cross-platform parity: mirrors `android/.../ui/GameTimerHelper.kt`.
//  Any change here must be reflected on the Android side (and vice versa) —
//  both platforms must produce identical outputs for the same inputs.
//  See `CLAUDE.md` → "Cross-platform parity".
//

import CoreLocation
import FirebaseFirestore
import Foundation

// MARK: - Zone Check

enum GameRole: Equatable {
    case chicken
    case hunter
}

struct ZoneCheckResult: Equatable {
    let isOutsideZone: Bool
    let distanceToCenter: CLLocationDistance
}

/// Whether this role should be zone-checked under the given game mode.
func shouldCheckZone(role: GameRole, gameMod: Game.GameMode) -> Bool {
    switch gameMod {
    case .stayInTheZone:
        return true // both chicken and hunters are checked
    case .followTheChicken:
        return role == .hunter // chicken defines the zone center
    }
}

/// Pure check: is the user outside the zone?
func checkZoneStatus(
    userLocation: CLLocationCoordinate2D,
    zoneCenter: CLLocationCoordinate2D,
    zoneRadius: CLLocationDistance
) -> ZoneCheckResult {
    let distance = CLLocation(latitude: userLocation.latitude, longitude: userLocation.longitude)
        .distance(from: CLLocation(latitude: zoneCenter.latitude, longitude: zoneCenter.longitude))
    return ZoneCheckResult(isOutsideZone: distance > zoneRadius, distanceToCenter: distance)
}

// MARK: - Countdown

/// Describes one phase of the countdown sequence (e.g., "3, 2, 1, RUN!").
struct CountdownPhase {
    let targetDate: Date
    let completionText: String
    /// Show numeric countdown (3, 2, 1) before the completion text.
    let showNumericCountdown: Bool
    /// Gate: only evaluate this phase if true.
    let isEnabled: Bool
}

enum CountdownResult: Equatable {
    case noChange
    case updateNumber(Int)
    case showText(String)
}

/// Evaluates countdown phases in order, returning the first match.
func evaluateCountdown(
    phases: [CountdownPhase],
    now: Date = .now,
    currentCountdownNumber: Int?,
    currentCountdownText: String?
) -> CountdownResult {
    for phase in phases where phase.isEnabled {
        let timeToTarget = phase.targetDate.timeIntervalSince(now)

        if phase.showNumericCountdown,
           timeToTarget > 0,
           timeToTarget <= AppConstants.countdownThresholdSeconds {
            let number = Int(ceil(timeToTarget))
            if currentCountdownNumber != number {
                return .updateNumber(number)
            }
            return .noChange
        }

        if timeToTarget <= 0,
           timeToTarget > -1,
           currentCountdownText == nil {
            return .showText(phase.completionText)
        }
    }
    return .noChange
}

// MARK: - Game Over

/// Returns true if the game has ended by time.
func checkGameOverByTime(endDate: Date) -> Bool {
    Date.now >= endDate
}

// MARK: - Center Interpolation

/// Interpolates the zone center between `initialCenter` and `finalCenter`
/// based on how much the radius has shrunk.
///
/// When currentRadius == initialRadius → returns initialCenter
/// When currentRadius == 0             → returns finalCenter
///
/// If `finalCenter` is nil, returns `initialCenter` unchanged.
func interpolateZoneCenter(
    initialCenter: CLLocationCoordinate2D,
    finalCenter: CLLocationCoordinate2D?,
    initialRadius: Double,
    currentRadius: Double
) -> CLLocationCoordinate2D {
    guard let finalCenter else { return initialCenter }
    guard initialRadius.isFinite, initialRadius > 0 else { return initialCenter }
    guard currentRadius.isFinite else { return initialCenter }

    let rawProgress = (initialRadius - currentRadius) / initialRadius
    guard rawProgress.isFinite else { return initialCenter }
    let progress = min(max(rawProgress, 0), 1)

    let lat = initialCenter.latitude + progress * (finalCenter.latitude - initialCenter.latitude)
    let lng = initialCenter.longitude + progress * (finalCenter.longitude - initialCenter.longitude)

    guard lat.isFinite, lng.isFinite else { return initialCenter }
    return CLLocationCoordinate2D(latitude: lat, longitude: lng)
}

// MARK: - Deterministic Drift

/// Extra meters carved out of the drift budget so floating-point error
/// in the meters ↔ degrees conversion can never push `finalCenter`
/// outside the new circle. The conversion is consistent (same `cos(lat)`
/// used for the offset and for the distance check), so 1 m is plenty.
let finalCenterSafetyMeters: Double = 1.0

/// Computes a deterministic drifted center for stayInTheZone mode.
/// The result is offset from `basePoint`, always within:
///  - `newRadius * 0.5` of basePoint (so basePoint stays well inside)
///  - `(oldRadius − newRadius) * 0.5` so successive shrinks don't lurch
///  - (when `finalCenter` is provided) `newRadius − dist(base, finalCenter) − margin`
///    so the final collapse point is ALWAYS inside the drifted circle,
///    regardless of how close `finalCenter` was chosen to the edge of
///    the initial zone.
///
/// The third bound is what makes the geometric invariant
/// `finalCenter ∈ circle(center_i, radius_i)` hold by construction at
/// every shrink step — the first two alone don't guarantee it once
/// `|initialCenter − finalCenter|` grows past `initialRadius / 2`.
///
/// `driftSeed` is a random integer stored in the Game document so every
/// client produces the exact same circle at each shrink step while each
/// game session has a unique drift pattern.
func deterministicDriftCenter(
    basePoint: CLLocationCoordinate2D,
    oldRadius: Double,
    newRadius: Double,
    driftSeed: Int,
    finalCenter: CLLocationCoordinate2D? = nil
) -> CLLocationCoordinate2D {
    let metersPerDegreeLat = 111_320.0
    let metersPerDegreeLng = 111_320.0 * cos(basePoint.latitude * .pi / 180.0)

    let maxFromBase = newRadius * 0.5
    let maxFromPrev = max(0, oldRadius - newRadius) * 0.5

    var maxFromFinal = Double.infinity
    if let finalCenter {
        let dLatM = (finalCenter.latitude - basePoint.latitude) * metersPerDegreeLat
        let dLngM = (finalCenter.longitude - basePoint.longitude) * metersPerDegreeLng
        let distBaseFinal = (dLatM * dLatM + dLngM * dLngM).squareRoot()
        maxFromFinal = max(0, newRadius - distBaseFinal - finalCenterSafetyMeters)
    }

    let safeDrift = min(maxFromBase, maxFromPrev, maxFromFinal)

    guard safeDrift > 0 else { return basePoint }

    let angleSeed = abs(Int(truncatingIfNeeded: Int64(driftSeed) &* 31) ^ Int(newRadius))
    let distSeed = abs(Int(truncatingIfNeeded: Int64(driftSeed) &* 127) ^ Int(truncatingIfNeeded: Int64(Int(newRadius)) &* 37))

    let angle = Double(angleSeed % 36000) / 36000.0 * 2.0 * .pi
    let distFraction = Double(distSeed % 10000) / 10000.0
    let distance = safeDrift * distFraction.squareRoot()

    let dLat = (distance * cos(angle)) / metersPerDegreeLat
    let dLng = (distance * sin(angle)) / metersPerDegreeLng

    return CLLocationCoordinate2D(
        latitude: basePoint.latitude + dLat,
        longitude: basePoint.longitude + dLng
    )
}

// MARK: - Radius Update

struct RadiusUpdateResult: Equatable {
    let newRadius: Int
    let newNextUpdate: Date
    let newCircle: CircleOverlay?
    let isGameOver: Bool
    let gameOverMessage: String?
}

/// Processes radius update logic. Returns nil if no update is due yet.
func processRadiusUpdate(
    nextRadiusUpdate: Date?,
    currentRadius: Int,
    radiusDeclinePerUpdate: Double,
    radiusIntervalUpdate: Double,
    gameMod: Game.GameMode,
    initialCoordinates: CLLocationCoordinate2D,
    currentCircle: CircleOverlay?,
    driftSeed: Int = 0,
    isZoneFrozen: Bool = false,
    finalCoordinates: CLLocationCoordinate2D? = nil,
    initialRadius: Double = 0
) -> RadiusUpdateResult? {
    let now = Date.now
    guard let nextUpdate = nextRadiusUpdate, now >= nextUpdate else { return nil }
    // Zone Freeze: skip the radius reduction for THIS scheduled shrink but
    // still advance `nextRadiusUpdate` to the following one. Previously we
    // returned `nil` here, which left `state.nextRadiusUpdate` stuck on a
    // past date — the "Map update in:" countdown then either showed `00:00`
    // indefinitely or, after freeze expired and one tick processed, jumped
    // to a date past `endDate` (one interval beyond the skipped shrink).
    // On a hunter watching a game that was already over on the chicken side,
    // this manifested as a countdown reading "Map update in: 3:00 / 4:59"
    // even though the game had ended — which is exactly what the live-test
    // caught. Advancing here keeps the countdown monotonic and in sync with
    // `findLastUpdate`, which always returns `lastUpdate + interval`.
    if isZoneFrozen {
        return RadiusUpdateResult(
            newRadius: currentRadius,
            newNextUpdate: nextUpdate.addingTimeInterval(TimeInterval(radiusIntervalUpdate * 60)),
            newCircle: currentCircle,
            isGameOver: false,
            gameOverMessage: nil
        )
    }

    let newRadius = currentRadius - Int(radiusDeclinePerUpdate)

    guard newRadius > 0 else {
        return RadiusUpdateResult(
            newRadius: currentRadius,
            newNextUpdate: nextUpdate,
            newCircle: currentCircle,
            isGameOver: true,
            gameOverMessage: "The zone has collapsed!"
        )
    }

    let newNextUpdate = nextUpdate.addingTimeInterval(TimeInterval(radiusIntervalUpdate * 60))

    let newCircle: CircleOverlay?
    if gameMod == .stayInTheZone {
        let interpolated = interpolateZoneCenter(
            initialCenter: initialCoordinates,
            finalCenter: finalCoordinates,
            initialRadius: initialRadius,
            currentRadius: Double(newRadius)
        )
        let driftedCenter = deterministicDriftCenter(
            basePoint: interpolated,
            oldRadius: Double(currentRadius),
            newRadius: Double(newRadius),
            driftSeed: driftSeed,
            finalCenter: finalCoordinates
        )
        newCircle = CircleOverlay(center: driftedCenter, radius: CLLocationDistance(newRadius))
    } else if let currentCircle {
        newCircle = CircleOverlay(center: currentCircle.center, radius: CLLocationDistance(newRadius))
    } else {
        newCircle = nil
    }

    return RadiusUpdateResult(
        newRadius: newRadius,
        newNextUpdate: newNextUpdate,
        newCircle: newCircle,
        isGameOver: false,
        gameOverMessage: nil
    )
}

// MARK: - Winner Detection

/// Detects new winners. When `ownHunterId` is provided, the latest winner
/// matching that ID is filtered out (hunter doesn't need self-notification).
func detectNewWinners(
    winners: [Winner],
    previousCount: Int,
    ownHunterId: String? = nil
) -> String? {
    guard winners.count > previousCount else { return nil }
    let newWinners = Array(winners.suffix(from: previousCount))
    guard let latest = newWinners.last else { return nil }
    if let ownId = ownHunterId, latest.hunterId == ownId { return nil }
    return "\(latest.hunterName) found the chicken! 🐔"
}

// MARK: - Power-Up Activation Detection

func detectActivatedPowerUp(
    oldGame: Game,
    newGame: Game,
    now: Date = .now
) -> (text: String, type: PowerUp.PowerUpType)? {
    let checks: [(KeyPath<Game, Timestamp?>, PowerUp.PowerUpType)] = [
        (\.powerUps.activeEffects.invisibility, .invisibility),
        (\.powerUps.activeEffects.zoneFreeze, .zoneFreeze),
        (\.powerUps.activeEffects.radarPing, .radarPing),
        (\.powerUps.activeEffects.decoy, .decoy),
        (\.powerUps.activeEffects.jammer, .jammer),
    ]

    for (keyPath, type) in checks {
        if let until = newGame[keyPath: keyPath]?.dateValue(), until > now,
           oldGame[keyPath: keyPath]?.dateValue() != until {
            return ("\(type.emoji) \(type.displayName) activated!", type)
        }
    }
    return nil
}

// MARK: - Power-Up Proximity

/// Finds all available power-ups within collection radius of the user's location.
func findNearbyPowerUps(
    userLocation: CLLocationCoordinate2D?,
    availablePowerUps: [PowerUp],
    collectionRadius: Double = AppConstants.powerUpCollectionRadiusMeters
) -> [PowerUp] {
    guard let userLoc = userLocation else { return [] }
    let userCLLocation = CLLocation(latitude: userLoc.latitude, longitude: userLoc.longitude)
    return availablePowerUps.filter { powerUp in
        userCLLocation
            .distance(from: CLLocation(latitude: powerUp.coordinate.latitude, longitude: powerUp.coordinate.longitude))
            <= collectionRadius
    }
}

// MARK: - Live Activity Update

struct LiveActivityUpdate: Equatable {
    let newState: PoulePartyAttributes.ContentState
    let didChange: Bool
}

/// Compares current Live Activity state against the last known state.
/// Returns an update only when the state has meaningfully changed.
func checkLiveActivityUpdate(
    currentState: PoulePartyAttributes.ContentState,
    lastState: PoulePartyAttributes.ContentState?
) -> LiveActivityUpdate? {
    guard currentState != lastState else { return nil }
    return LiveActivityUpdate(newState: currentState, didChange: true)
}

// MARK: - Radar Ping Broadcast

/// Decides whether the chicken should force-broadcast its location while a
/// Radar Ping is active in stayInTheZone mode. Pure function — all time-based
/// inputs are explicit so the caller drives the clock in tests.
///
/// Returns `true` only when radar ping is live **and** the chicken is not
/// invisible (safety net — invisibility isn't spawned in stayInTheZone today,
/// but if it ever leaks through, it wins over radar ping, matching the
/// followTheChicken behavior).
func shouldBroadcastDuringRadarPing(
    now: Date,
    radarPingUntil: Date?,
    invisibilityUntil: Date?
) -> Bool {
    let isRadarPinged = radarPingUntil.map { now < $0 } ?? false
    guard isRadarPinged else { return false }
    let isInvisible = invisibilityUntil.map { now < $0 } ?? false
    return !isInvisible
}

// MARK: - Jammer Noise

/// Adds deterministic ±halfNoise ° of latitude/longitude jitter to `coordinate`.
///
/// The noise is a pure function of `(driftSeed, now)` bucketed to 1 s, so iOS
/// and Android produce the same value for the same inputs (used by parity
/// tests) and the bruit shifts once per second, too fast for a hunter to
/// average out, too slow to burn battery re-computing inside a single write.
func applyJammerNoise(
    to coordinate: CLLocationCoordinate2D,
    driftSeed: Int,
    now: Date = .now
) -> CLLocationCoordinate2D {
    // Explicit Int64 rather than relying on Swift's platform-dependent Int
    // width. Android's bucket is `Long` — forcing Int64 here removes the
    // remote chance of a mismatch on any hypothetical 32-bit build and
    // documents the intent.
    let bucket = Int64(now.timeIntervalSince1970)
    let seed = Int(Int64(driftSeed) ^ bucket)
    let halfNoise = AppConstants.jammerNoiseDegrees / 2.0
    // seededRandom returns [0, 1). Shift to [-halfNoise, halfNoise).
    let latNoise = (seededRandom(seed: seed, index: 0) * 2.0 - 1.0) * halfNoise
    let lonNoise = (seededRandom(seed: seed, index: 1) * 2.0 - 1.0) * halfNoise
    return CLLocationCoordinate2D(
        latitude: coordinate.latitude + latNoise,
        longitude: coordinate.longitude + lonNoise
    )
}

// MARK: - Seeded Random (splitmix64 — unsigned shifts to match Android's `ushr`)

func seededRandom(seed: Int, index: Int) -> Double {
    var z = UInt64(bitPattern: Int64(seed)) &+ UInt64(bitPattern: Int64(index)) &* 0x9e3779b97f4a7c15
    z = (z ^ (z >> 30)) &* 0xbf58476d1ce4e5b9
    z = (z ^ (z >> 27)) &* 0x94d049bb133111eb
    z = z ^ (z >> 31)
    return Double(z >> 1) / Double(Int64.max)
}
