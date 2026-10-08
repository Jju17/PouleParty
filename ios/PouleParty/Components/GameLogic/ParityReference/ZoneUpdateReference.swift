import CoreLocation
import Foundation

// Reference mirrors of the server zone schedule, kept only for the parity tests.

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
    gameMode: Game.GameMode,
    initialCoordinates: CLLocationCoordinate2D,
    currentCircle: CircleOverlay?,
    driftSeed: Int = 0,
    isZoneFrozen: Bool = false,
    finalCoordinates: CLLocationCoordinate2D? = nil,
    initialRadius: Double = 0,
    now: Date = .now
) -> RadiusUpdateResult? {
    guard let nextUpdate = nextRadiusUpdate, now >= nextUpdate else { return nil }
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
    if gameMode == .stayInTheZone {
        // Drift is independent per shrink: candidate sampled from
        // `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − FINAL −
        // safety)`. That enforces both product rules directly, new
        // circle inside start zone, final zone inside new circle,
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

extension Game {
    func findLastUpdate(now: Date = .now) -> (Date, Int) {
        var lastUpdate: Date = self.hunterStartDate
        var lastRadius: Int = Int(self.zone.radius)

        guard zone.shrinkIntervalMinutes > 0 else {
            return (lastUpdate, lastRadius)
        }

        // Zone freeze window: skip radius reductions for shrinks inside [freezeStart, freezeEnd)
        let freezeEnd = powerUps.activeEffects.zoneFreeze?.dateValue()
        let freezeDuration = PowerUp.PowerUpType.zoneFreeze.durationSeconds ?? 0
        let freezeStart = freezeEnd?.addingTimeInterval(-freezeDuration)

        let maxIterations = 10_000
        let interval = TimeInterval(self.zone.shrinkIntervalMinutes * 60)
        var iterations = 0
        while lastUpdate.addingTimeInterval(interval) < now && iterations < maxIterations {
            lastUpdate.addTimeInterval(interval)
            let isFrozen: Bool
            if let fs = freezeStart, let fe = freezeEnd {
                isFrozen = lastUpdate >= fs && lastUpdate < fe
            } else {
                isFrozen = false
            }
            if !isFrozen {
                lastRadius -= Int(self.zone.shrinkMetersPerUpdate)
            }
            // Once the radius is at floor, every later iteration is a
            // no-op, skip them.
            if lastRadius <= 0 { break }
            iterations += 1
        }

        lastRadius = max(0, lastRadius)
        let nextUpdate = lastUpdate.addingTimeInterval(interval)
        return (nextUpdate, lastRadius)
    }
}
