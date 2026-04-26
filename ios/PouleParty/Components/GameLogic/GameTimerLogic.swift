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

/// Radius of the "final zone" the chicken sees as a green glow on the
/// map — the whole disk, not just its center, must stay inside every
/// drifted circle. Matches the hardcoded 50 m used by
/// `finalZoneGlowContent` in `MapOverlays.swift` and the Android
/// equivalent in `ChickenMapScreen`. Kept alongside the other drift
/// constants so the drift algo and the UI can never disagree on what
/// "final zone" means.
let finalZoneRadiusMeters: Double = 50.0

/// How many rejection-sampling attempts before falling back to the
/// deterministic "pull toward finalCenter by `delta`" point. Each
/// attempt costs one splitmix64 evaluation; 32 is plenty — the
/// rejection rate only gets high near game end where
/// `disk(C, delta)` sticks out past `disk(F, r)`, and even at 50 %
/// rejection 32 attempts succeed with probability > 99.99 %.
private let maxDriftAttempts = 32

/// Computes a deterministic drifted center for stayInTheZone mode.
/// Picks the zone center for one shrink step as a pseudo-random
/// point that simultaneously satisfies:
///  A. `|candidate − basePoint| ≤ oldRadius − newRadius` → the new
///     circle fits entirely inside `disk(basePoint, oldRadius)`.
///  B. when `finalCenter` is provided, `|candidate − finalCenter| ≤
///     newRadius − FINAL_ZONE_RADIUS − safety` → the final-zone
///     disk (50 m glow) fits entirely inside the drifted circle.
///
/// Caller contract: `basePoint` is the **initial** zone center and
/// `oldRadius` is the **initial** zone radius — NOT the previous
/// drifted center. Every shrink's candidate is drawn independently
/// from `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − FINAL −
/// safety)`, so successive circles can overlap each other freely as
/// long as both constraints hold. This matches the product rules:
/// no circle escapes the start zone, every circle contains the
/// final zone, intermediate circles have no nesting constraint
/// between them.
///
/// Strategy: sample uniformly inside the *smaller* of disk A / disk
/// B and reject against the larger. When one disk contains the
/// other, the first sample is always valid. When they partially
/// overlap, the lens/smaller-disk ratio is usually > 10 %, so 32
/// splitmix64-seeded attempts succeed with overwhelming probability.
/// When rejection exhausts, fall back to a deterministic point on
/// the base→final line — always in the intersection whenever the
/// disks overlap (caller invariant: final zone fits in start zone).
func deterministicDriftCenter(
    basePoint: CLLocationCoordinate2D,
    oldRadius: Double,
    newRadius: Double,
    driftSeed: Int,
    finalCenter: CLLocationCoordinate2D? = nil
) -> CLLocationCoordinate2D {
    // No shrink → no drift. Frozen zones round-trip. Collapsed radius
    // skips drift too.
    guard newRadius < oldRadius, newRadius > 0 else { return basePoint }

    let rA = oldRadius - newRadius
    let rB: Double = finalCenter != nil
        ? max(0, newRadius - finalZoneRadiusMeters - finalCenterSafetyMeters)
        : .infinity

    let metersPerDegreeLat = 111_320.0
    let metersPerDegreeLng = 111_320.0 * cos(basePoint.latitude * .pi / 180.0)

    let stepSeed = driftSeed ^ Int(newRadius)

    // No final constraint → sample uniformly in disk A.
    guard let finalCenter else {
        let angle = seededRandom(seed: stepSeed, index: 0) * 2.0 * .pi
        let dist = rA * seededRandom(seed: stepSeed, index: 1).squareRoot()
        return CLLocationCoordinate2D(
            latitude: basePoint.latitude + (dist * sin(angle)) / metersPerDegreeLat,
            longitude: basePoint.longitude + (dist * cos(angle)) / metersPerDegreeLng
        )
    }

    // v = finalCenter − basePoint, in local flat-earth meters.
    let vx = (finalCenter.longitude - basePoint.longitude) * metersPerDegreeLng
    let vy = (finalCenter.latitude - basePoint.latitude) * metersPerDegreeLat
    let vLen = (vx * vx + vy * vy).squareRoot()

    // Sample from the smaller disk, reject against the larger.
    let sampleFromA = rA <= rB
    let sampleR = min(rA, rB)

    for k in 0..<maxDriftAttempts {
        let angle = seededRandom(seed: stepSeed, index: 2 * k) * 2.0 * .pi
        let dist = sampleR * seededRandom(seed: stepSeed, index: 2 * k + 1).squareRoot()
        let ox = dist * cos(angle)
        let oy = dist * sin(angle)
        // Candidate offset from basePoint (caller's frame).
        let cdx = sampleFromA ? ox : vx + ox
        let cdy = sampleFromA ? oy : vy + oy
        // Check: is candidate in the *other* disk?
        let checkDx = sampleFromA ? cdx - vx : cdx
        let checkDy = sampleFromA ? cdy - vy : cdy
        let checkR = sampleFromA ? rB : rA
        if (checkDx * checkDx + checkDy * checkDy).squareRoot() <= checkR {
            return CLLocationCoordinate2D(
                latitude: basePoint.latitude + cdy / metersPerDegreeLat,
                longitude: basePoint.longitude + cdx / metersPerDegreeLng
            )
        }
    }

    // Deterministic fallback on the base→final line.
    if vLen > 0 {
        let pull = min(rA, vLen)
        return CLLocationCoordinate2D(
            latitude: basePoint.latitude + ((vy / vLen) * pull) / metersPerDegreeLat,
            longitude: basePoint.longitude + ((vx / vLen) * pull) / metersPerDegreeLng
        )
    }
    return basePoint
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
        // Drift is independent per shrink: candidate sampled from
        // `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − FINAL −
        // safety)`. That enforces both product rules directly — new
        // circle inside start zone, final zone inside new circle —
        // while leaving successive intermediate circles free to
        // overlap each other.
        let driftedCenter = deterministicDriftCenter(
            basePoint: initialCoordinates,
            oldRadius: initialRadius,
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

// MARK: - Debug Preview (all shifted circles at once)

/// A single preview circle entry returned by
/// [`computeDebugShiftedCircles`] — the center and radius the zone will
/// hold at each scheduled shrink, in order.
struct DebugShrinkCircle: Equatable {
    let center: CLLocationCoordinate2D
    let radius: Double
}

/// Walks the zone shrink schedule forward from `hunterStartDate` through
/// `endDate` using the same `interpolateZoneCenter` +
/// `deterministicDriftCenter` the live timer invokes. Returns one entry
/// per scheduled shrink, ordered from first to last, stopping early
/// when the radius would collapse to zero.
///
/// Pure function — only used by the long-press debug preview on the
/// chicken map to render every future circle simultaneously. Mirrors
/// the Android `computeDebugShiftedCircles` sibling.
func computeDebugShiftedCircles(game: Game) -> [DebugShrinkCircle] {
    guard game.gameMode == .stayInTheZone else { return [] }
    let initialRadius = game.zone.radius
    guard initialRadius > 0, game.zone.shrinkIntervalMinutes > 0 else { return [] }

    let initialCenter = game.initialLocation
    let finalCenter = game.finalLocation
    let driftSeed = game.zone.driftSeed
    let declinePerUpdate = game.zone.shrinkMetersPerUpdate
    let intervalSeconds = game.zone.shrinkIntervalMinutes * 60
    let duration = game.endDate.timeIntervalSince(game.hunterStartDate)
    guard duration > 0, intervalSeconds > 0 else { return [] }

    let maxShrinks = Int(floor(duration / intervalSeconds))
    var result: [DebugShrinkCircle] = []
    var radius = initialRadius
    // Drift is independent per shrink — every call uses the initial
    // center/radius, no state between iterations.
    for _ in 0..<maxShrinks {
        let newRadius = radius - declinePerUpdate
        if newRadius <= 0 { break }
        let drifted = deterministicDriftCenter(
            basePoint: initialCenter,
            oldRadius: initialRadius,
            newRadius: newRadius,
            driftSeed: driftSeed,
            finalCenter: finalCenter
        )
        result.append(DebugShrinkCircle(center: drifted, radius: newRadius))
        radius = newRadius
    }
    return result
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
