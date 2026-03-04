//
//  Constants.swift
//  PouleParty
//
//  Created by Julien Rahier on 22/02/2026.
//

import CoreLocation
import Foundation

enum AppConstants {
    // MARK: - Preferences Keys
    static let prefOnboardingCompleted = "hasCompletedOnboarding"
    static let prefUserNickname = "userNickname"

    // MARK: - Time Intervals
    static let locationThrottleSeconds: TimeInterval = 5
    static let countdownThresholdSeconds: TimeInterval = 3
    static let countdownDisplaySeconds: TimeInterval = 1.5
    static let winnerNotificationSeconds: TimeInterval = 4
    static let codeCopyFeedbackSeconds: TimeInterval = 1
    static let confettiDurationSeconds: TimeInterval = 10

    // MARK: - Game Defaults
    static let defaultStartDelaySeconds: TimeInterval = 300  // 5 minutes
    static let defaultGameDurationSeconds: TimeInterval = 3900  // 65 minutes
    static let defaultLatitude = 50.8466
    static let defaultLongitude = 4.3528
    static let defaultInitialRadius = 1500.0
    static let defaultRadiusDecline = 100.0
    static let defaultRadiusInterval = 5.0  // in minutes
    static let defaultNumberOfPlayers = 10

    // MARK: - Location
    static let locationMinDistanceMeters: CLLocationDistance = 10

    // MARK: - Game Codes
    static let gameCodeLength = 6
    static let foundCodeMaxValue = 9999
    static let foundCodeDigits = 4

    // MARK: - Found Code Cooldown
    static let codeMaxWrongAttempts = 3
    static let codeCooldownSeconds: TimeInterval = 10

    // MARK: - Nickname
    static let nicknameMaxLength = 20

    // MARK: - Zone
    // Grace period disabled — kept for future game mode
    // static let outsideZoneGracePeriodSeconds = 30

    // MARK: - Confetti
    static let confettiParticleCount = 80
}
