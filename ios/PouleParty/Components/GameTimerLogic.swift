//
//  GameTimerLogic.swift
//  PouleParty
//
//  Shared pure functions for timer, countdown, radius, and winner logic
//  used by both ChickenMapFeature and HunterMapFeature.
//

import CoreLocation
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
func shouldCheckZone(role: GameRole, gameMod: Game.GameMod) -> Bool {
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
    currentCountdownNumber: Int?,
    currentCountdownText: String?
) -> CountdownResult {
    for phase in phases where phase.isEnabled {
        let timeToTarget = phase.targetDate.timeIntervalSinceNow

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
    .now >= endDate
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
    guard initialRadius > 0 else { return initialCenter }

    let progress = min(max((initialRadius - currentRadius) / initialRadius, 0), 1)

    let lat = initialCenter.latitude + progress * (finalCenter.latitude - initialCenter.latitude)
    let lng = initialCenter.longitude + progress * (finalCenter.longitude - initialCenter.longitude)

    return CLLocationCoordinate2D(latitude: lat, longitude: lng)
}

// MARK: - Deterministic Drift

/// Computes a deterministic drifted center for stayInTheZone mode.
/// The result is offset from `basePoint`, always within both:
///  - `newRadius * 0.5` of basePoint (so basePoint stays well inside)
///  - `(oldRadius - newRadius)` of any previous center that was also computed this way
///
/// `driftSeed` is a random integer stored in the Game document so every
/// client produces the exact same circle at each shrink step while each
/// game session has a unique drift pattern.
func deterministicDriftCenter(
    basePoint: CLLocationCoordinate2D,
    oldRadius: Double,
    newRadius: Double,
    driftSeed: Int
) -> CLLocationCoordinate2D {
    let maxFromBase = newRadius * 0.5
    let maxFromPrev = max(0, oldRadius - newRadius) * 0.5
    let safeDrift = min(maxFromBase, maxFromPrev)

    guard safeDrift > 0 else { return basePoint }

    let angleSeed = abs(driftSeed &* 31 ^ Int(newRadius))
    let distSeed = abs(driftSeed &* 127 ^ (Int(newRadius) &* 37))

    let angle = Double(angleSeed % 36000) / 36000.0 * 2.0 * .pi
    let distFraction = Double(distSeed % 10000) / 10000.0
    let distance = safeDrift * distFraction.squareRoot()

    let metersPerDegreeLat = 111_320.0
    let metersPerDegreeLng = 111_320.0 * cos(basePoint.latitude * .pi / 180.0)

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
    gameMod: Game.GameMod,
    initialCoordinates: CLLocationCoordinate2D,
    currentCircle: CircleOverlay?,
    driftSeed: Int = 0,
    isZoneFrozen: Bool = false,
    finalCoordinates: CLLocationCoordinate2D? = nil,
    initialRadius: Double = 0
) -> RadiusUpdateResult? {
    guard let nextUpdate = nextRadiusUpdate, .now >= nextUpdate else { return nil }
    if isZoneFrozen { return nil }

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
            driftSeed: driftSeed
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
        (\.activeInvisibilityUntil, .invisibility),
        (\.activeZoneFreezeUntil, .zoneFreeze),
        (\.activeRadarPingUntil, .radarPing),
        (\.activeDecoyUntil, .decoy),
        (\.activeJammerUntil, .jammer),
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

// MARK: - Jammer Noise

func applyJammerNoise(to coordinate: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
    let latNoise = Double.random(in: -0.0018...0.0018) // ~200m
    let lonNoise = Double.random(in: -0.0018...0.0018)
    return CLLocationCoordinate2D(
        latitude: coordinate.latitude + latNoise,
        longitude: coordinate.longitude + lonNoise
    )
}

// MARK: - Seeded Random (stateless hash, matches Android)

func seededRandom(seed: Int, index: Int) -> Double {
    var z = Int64(seed) &+ Int64(index) &* 0x9e3779b97f4a7c15
    z = (z ^ (z >> 30)) &* -4658895280553007687 // 0xbf58476d1ce4e5b9
    z = (z ^ (z >> 27)) &* -7723592293110705685 // 0x94d049bb133111eb
    z = z ^ (z >> 31)
    return Double(z &>> 1) / Double(Int64.max)
}
