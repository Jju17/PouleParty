//
//  GameTimerLogic.swift
//  PouleParty
//
//  Shared pure functions for timer, countdown, radius, and winner logic
//  used by both ChickenMapFeature and HunterMapFeature.
//

import CoreLocation
import Foundation

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
           currentCountdownText == nil,
           currentCountdownNumber == nil {
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
    currentCircle: CircleOverlay?
) -> RadiusUpdateResult? {
    guard let nextUpdate = nextRadiusUpdate, .now >= nextUpdate else { return nil }

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
        newCircle = CircleOverlay(center: initialCoordinates, radius: CLLocationDistance(newRadius))
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
    return "\(latest.hunterName) a trouvé la poule !"
}
