import CoreLocation
import FirebaseFirestore
import Foundation

// MARK: - Zone Check

enum GameRole: Equatable {
    case chicken
    case hunter
    case gameMaster
}

struct ZoneCheckResult: Equatable {
    let isOutsideZone: Bool
    let distanceToCenter: CLLocationDistance
}

func shouldCheckZone(role: GameRole, gameMode: Game.GameMode) -> Bool {
    if role == .gameMaster { return false }
    switch gameMode {
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
    let distance = distanceMeters(userLocation, zoneCenter)
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
func checkGameOverByTime(endDate: Date, now: Date = .now) -> Bool {
    now >= endDate
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

// MARK: - Stored-circles selection (PP-zone-stored)

/// Which stored circle is active now + when the next shrink is due.
struct ActiveCircleResult: Equatable {
    let circleIndex: Int
    let nextUpdate: Date
}

/// Resolved render state for the active circle: the Int radius and (for
/// stayInTheZone) the stored center, plus the next shrink time. In
/// followTheChicken `center` is nil (caller keeps the live chicken GPS).
struct ZoneRenderState: Equatable {
    let radius: Int
    let center: CLLocationCoordinate2D?
    let nextUpdate: Date?
}

/// PP-zone-stored: pick the active shrink index at `now` from the timing
/// alone (freeze-aware); the caller looks up `circles[circleIndex]`.
/// Mirrors the freeze-skip logic of the former `findLastUpdate`. Index 0 =
/// the initial circle; each elapsed non-frozen interval advances by one,
/// clamped to the last (50 m) circle. Mirrors Android `selectActiveCircle`.
func selectActiveCircle(
    hunterStartDate: Date,
    shrinkIntervalMinutes: Double,
    circleCount: Int,
    freezeEnd: Date?,
    freezeDuration: TimeInterval,
    now: Date = .now
) -> ActiveCircleResult {
    let lastIndex = max(0, circleCount - 1)
    guard shrinkIntervalMinutes > 0, circleCount > 1 else {
        return ActiveCircleResult(circleIndex: 0, nextUpdate: .distantFuture)
    }
    let interval = TimeInterval(shrinkIntervalMinutes * 60)
    let freezeStart = freezeEnd?.addingTimeInterval(-freezeDuration)
    var index = 0
    var lastUpdate = hunterStartDate
    var iterations = 0
    while lastUpdate.addingTimeInterval(interval) < now && index < lastIndex && iterations < 10_000 {
        lastUpdate.addTimeInterval(interval)
        let isFrozen: Bool
        if let fs = freezeStart, let fe = freezeEnd {
            isFrozen = lastUpdate >= fs && lastUpdate < fe
        } else {
            isFrozen = false
        }
        if !isFrozen { index += 1 }
        iterations += 1
    }
    let nextUpdate = lastUpdate.addingTimeInterval(interval)
    return ActiveCircleResult(circleIndex: min(index, lastIndex), nextUpdate: nextUpdate)
}

/// PP-zone-stored: the single shared selector used by every map feature so
/// geometry is resolved identically. Picks `circles[selectActiveCircle(...)]`;
/// the Int radius floors the SAME stored Double on every device, so parity
/// holds by construction. Mirrors Android `zoneRenderStateFromCircles`.
/// The zone to draw for a game at `now`, from its stored circle schedule.
func zoneRenderState(for game: Game, circles: [ZoneCircle], now: Date) -> ZoneRenderState {
    zoneRenderState(
        gameMode: game.gameMode,
        hunterStartDate: game.hunterStartDate,
        shrinkIntervalMinutes: game.zone.shrinkIntervalMinutes,
        fallbackRadius: game.zone.radius,
        circles: circles,
        freezeEnd: game.powerUps.activeEffects.zoneFreeze?.dateValue(),
        freezeDuration: PowerUp.PowerUpType.zoneFreeze.durationSeconds ?? 0,
        now: now
    )
}

func zoneRenderState(
    gameMode: Game.GameMode,
    hunterStartDate: Date,
    shrinkIntervalMinutes: Double,
    fallbackRadius: Double,
    circles: [ZoneCircle],
    freezeEnd: Date?,
    freezeDuration: TimeInterval,
    now: Date = .now
) -> ZoneRenderState {
    guard !circles.isEmpty else {
        return ZoneRenderState(radius: Int(fallbackRadius), center: nil, nextUpdate: nil)
    }
    let active = selectActiveCircle(
        hunterStartDate: hunterStartDate,
        shrinkIntervalMinutes: shrinkIntervalMinutes,
        circleCount: circles.count,
        freezeEnd: freezeEnd,
        freezeDuration: freezeDuration,
        now: now
    )
    let circle = circles[active.circleIndex]
    let center = gameMode == .stayInTheZone ? circle.center : nil
    return ZoneRenderState(radius: Int(circle.radiusMeters), center: center, nextUpdate: active.nextUpdate)
}

// MARK: - Deterministic Drift

/// Extra meters carved out of the drift budget so floating-point error
/// in the meters ↔ degrees conversion can never push `finalCenter`
/// outside the new circle. The conversion is consistent (same `cos(lat)`
/// used for the offset and for the distance check), so 1 m is plenty.
let finalCenterSafetyMeters: Double = 1.0

/// Radius of the "final zone" the chicken sees as a green glow on the
/// map, the whole disk, not just its center, must stay inside every
/// drifted circle. Matches the hardcoded 50 m used by
/// `finalZoneGlowContent` in `MapOverlays.swift` and the Android
/// equivalent in `ChickenMapScreen`. Kept alongside the other drift
/// constants so the drift algo and the UI can never disagree on what
/// "final zone" means.
let finalZoneRadiusMeters: Double = 50.0

/// How many rejection-sampling attempts before falling back to the
/// deterministic "pull toward finalCenter by `delta`" point. Each
/// attempt costs one splitmix64 evaluation; 32 is plenty, the
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
/// `oldRadius` is the **initial** zone radius, NOT the previous
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
/// the base→final line, always in the intersection whenever the
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

// MARK: - Debug Preview (all shifted circles at once)

/// A single preview circle entry returned by
/// [`computeDebugShiftedCircles`], the center and radius the zone will
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
/// Pure function, only used by the long-press debug preview on the
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
    // Drift is independent per shrink, every call uses the initial
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
    return String(localized: "\(latest.hunterName) found the chicken! 🐔")
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
            return (String(localized: "\(type.emoji) \(type.displayName) activated!"), type)
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
    return availablePowerUps.filter { distanceMeters(userLoc, $0.coordinate) <= collectionRadius }
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
    // width. Android's bucket is `Long`, forcing Int64 here removes the
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

// MARK: - Seeded Random (splitmix64, unsigned shifts to match Android's `ushr`)

func seededRandom(seed: Int, index: Int) -> Double {
    var z = UInt64(bitPattern: Int64(seed)) &+ UInt64(bitPattern: Int64(index)) &* 0x9e3779b97f4a7c15
    z = (z ^ (z >> 30)) &* 0xbf58476d1ce4e5b9
    z = (z ^ (z >> 27)) &* 0x94d049bb133111eb
    z = z ^ (z >> 31)
    return Double(z >> 1) / Double(Int64.max)
}

/// Renders the `+MM:SS` (or `+HH:MM:SS` past one hour) overtime
/// delta shown on the gameOver countdown bar. Negative deltas
/// (i.e. `now < endDate`) clamp to `+00:00`. Mirrors the Kotlin
/// `formatOvertime` so the two platforms produce identical strings.
func formatOvertime(now: Date, endDate: Date) -> String {
    let seconds = max(0, Int(now.timeIntervalSince(endDate)))
    let hours = seconds / 3600
    let minutes = (seconds % 3600) / 60
    let secs = seconds % 60
    if hours > 0 {
        return String(format: "+%02d:%02d:%02d", hours, minutes, secs)
    }
    return String(format: "+%02d:%02d", minutes, secs)
}
