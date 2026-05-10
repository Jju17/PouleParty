//
//  AnalyticsClient.swift
//  PouleParty
//

import ComposableArchitecture
import FirebaseAnalytics

struct AnalyticsClient {
    var logEvent: (_ name: String, _ parameters: [String: Any]) -> Void
}

extension AnalyticsClient {
    // MARK: - Convenience methods

    func gameCreated(gameMode: String, maxPlayers: Int, powerUpsEnabled: Bool) {
        logEvent("game_created", [
            "game_mode": gameMode,
            "max_players": maxPlayers,
            "power_ups_enabled": powerUpsEnabled,
        ])
    }

    func gameJoined(gameMode: String, gameCode: String) {
        logEvent("game_joined", [
            "game_mode": gameMode,
            "game_code": gameCode,
        ])
    }

    func gameStarted(gameMode: String) {
        logEvent("game_started", ["game_mode": gameMode])
    }

    func gameEnded(reason: String, winnersCount: Int) {
        logEvent("game_ended", [
            "reason": reason,
            "winners_count": winnersCount,
        ])
    }

    func hunterFoundChicken(attempts: Int) {
        logEvent("hunter_found_chicken", ["attempts": attempts])
    }

    func hunterWrongCode(attemptNumber: Int) {
        logEvent("hunter_wrong_code", ["attempt_number": attemptNumber])
    }

    func powerUpCollected(type: String, role: String) {
        logEvent("power_up_collected", [
            "type": type,
            "role": role,
        ])
    }

    func powerUpActivated(type: String, role: String) {
        logEvent("power_up_activated", [
            "type": type,
            "role": role,
        ])
    }

    func registrationCompleted() {
        logEvent("registration_completed", [:])
    }

    func onboardingCompleted() {
        logEvent("onboarding_completed", [:])
    }
}

extension AnalyticsClient: TestDependencyKey {
    static let testValue = AnalyticsClient(
        logEvent: { _, _ in }
    )
}

extension AnalyticsClient: DependencyKey {
    static var liveValue = AnalyticsClient(
        logEvent: { name, parameters in
            Analytics.logEvent(name, parameters: parameters)
        }
    )
}

extension DependencyValues {
    var analyticsClient: AnalyticsClient {
        get { self[AnalyticsClient.self] }
        set { self[AnalyticsClient.self] = newValue }
    }
}
