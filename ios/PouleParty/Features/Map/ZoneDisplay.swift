import ComposableArchitecture
import CoreLocation
import Foundation
import os

private let logger = Logger(category: "Zone")

/// The zone geometry every map shows, refreshed from the stored schedule.
protocol ZoneDisplayState {
    var countdownNumber: Int? { get set }
    var countdownText: String? { get set }
    var radius: Int { get set }
    var nextRadiusUpdate: Date? { get set }
    var mapCircle: CircleOverlay? { get set }
}

extension ZoneDisplayState {
    /// Keeps the current center when the schedule has none (the zone follows the chicken).
    mutating func applyZone(_ zone: ZoneRenderState, fallbackCenter: CLLocationCoordinate2D? = nil) {
        radius = zone.radius
        if let next = zone.nextUpdate { nextRadiusUpdate = next }
        if let center = zone.center ?? mapCircle?.center ?? fallbackCenter {
            mapCircle = CircleOverlay(center: center, radius: CLLocationDistance(zone.radius))
        }
    }
}

extension ZoneDisplayState {
    /// Returns true when a completion text appeared and must be dismissed later.
    mutating func applyCountdown(_ result: CountdownResult) -> Bool {
        switch result {
        case .noChange:
            return false
        case let .updateNumber(number):
            countdownNumber = number
            countdownText = nil
            return false
        case let .showText(text):
            countdownNumber = nil
            countdownText = text
            return true
        }
    }
}

extension ChickenMapFeature.State: ZoneDisplayState {}
extension HunterMapFeature.State: ZoneDisplayState {}
extension GameMasterMapFeature.State: ZoneDisplayState {}

/// Loads the stored schedule with retries and reports the outcome as an action.
func zoneScheduleEffect<Action>(
    gameId: String,
    apiClient: ApiClient,
    clock: any Clock<Duration>,
    loaded: @escaping ([ZoneCircle]) -> Action,
    failed: @escaping (String) -> Action
) -> Effect<Action> {
    .run { send in
        switch await loadZoneSchedule(gameId, fetch: apiClient.fetchZoneSchedule, sleep: { try await clock.sleep(for: $0) }) {
        case let .success(circles):
            await send(loaded(circles))
        case let .failure(error):
            logger.warning("[zone] schedule unavailable: \(error.localizedDescription)")
            await send(failed(error.userMessage))
        }
    }
}

/// What the Live Activity shows once the game is over.
func gameOverLiveActivityState(game: Game, radius: Int) -> PoulePartyAttributes.ContentState {
    PoulePartyAttributes.ContentState(
        radiusMeters: radius,
        nextShrinkDate: nil,
        activeHunters: max(0, game.hunterIds.count - game.winners.count),
        winnersCount: game.winners.count,
        isOutsideZone: false,
        gamePhase: .gameOver
    )
}

/// The start countdowns each role sees; nothing counts down before a manual launch.
func countdownPhases(for role: GameRole, game: Game) -> [CountdownPhase] {
    let hasLaunched = !game.manualStartEnabled || game.timing.actualStart != nil
    switch role {
    case .chicken:
        return [
            CountdownPhase(
                targetDate: game.effectiveStartDate,
                completionText: String(localized: "RUN! 🐔"),
                showNumericCountdown: true,
                isEnabled: hasLaunched
            ),
            CountdownPhase(
                targetDate: game.hunterStartDate,
                completionText: String(localized: "🔍 Hunters incoming!"),
                showNumericCountdown: false,
                isEnabled: hasLaunched && game.timing.headStartMinutes > 0
            ),
        ]
    case .hunter, .gameMaster:
        return [
            CountdownPhase(
                targetDate: game.effectiveStartDate,
                completionText: String(localized: "🐔 is hiding!"),
                showNumericCountdown: true,
                isEnabled: hasLaunched && game.timing.headStartMinutes > 0
            ),
            CountdownPhase(
                targetDate: game.hunterStartDate,
                completionText: String(localized: "LET'S HUNT! 🔍"),
                showNumericCountdown: true,
                isEnabled: hasLaunched
            ),
        ]
    }
}
