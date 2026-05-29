//
//  Constants.swift
//  PouleParty
//
//  Created by Julien Rahier on 22/02/2026.
//

import CoreLocation
import Foundation
import os

enum AppConstants {
    // MARK: - Logging
    static let logSubsystem = "dev.rahier.pouleparty"

    // MARK: - Preferences Keys
    static let prefOnboardingCompleted = "hasCompletedOnboarding"
    static let prefUserNickname = "userNickname"
    static let prefIsMusicMuted = "isMusicMuted"
    static let prefLastMigratedVersion = "lastMigratedVersion"
    static let prefPendingChallenges = "pendingChallenges"

    // MARK: - Time Intervals
    static let locationThrottleSeconds: TimeInterval = 5
    static let countdownThresholdSeconds: TimeInterval = 3
    static let countdownDisplaySeconds: TimeInterval = 1.5
    static let winnerNotificationSeconds: TimeInterval = 4
    static let codeCopyFeedbackSeconds: TimeInterval = 1
    static let confettiDurationSeconds: TimeInterval = 10

    // MARK: - Game Defaults
    static let defaultLatitude = 50.8466
    static let defaultLongitude = 4.3528
    /// Default zone radius the wizard starts from. Mirrors the `Game.Zone`
    /// struct default and the Android `DEFAULT_INITIAL_RADIUS`. Compiled
    /// fallback for the `default_initial_radius_meters` Remote Config key.
    static let defaultInitialRadiusMeters: Double = 1500

    // MARK: - Location
    static let locationMinDistanceMeters: CLLocationDistance = 10

    // MARK: - Game Codes
    static let gameCodeLength = 6
    static let foundCodeMaxValue = 9999
    static let foundCodeDigits = 4

    // MARK: - Found Code Cooldown
    static let codeMaxWrongAttempts = 3
    static let codeCooldownSeconds: TimeInterval = 10

    // MARK: - Power-Ups
    static let jammerNoiseDegrees: Double = 0.0036 // ~200m noise for jammer power-up
    static let powerUpCollectionRadiusMeters: Double = 30
    static let powerUpInitialBatchSize = 5
    static let powerUpPeriodicBatchSize = 2

    // MARK: - Nickname
    static let nicknameMaxLength = 20

    // MARK: - Zone
    // Grace period disabled — kept for future game mode
    // static let outsideZoneGracePeriodSeconds = 30
    /// PP-36: how often the hunter loses a point while outside the
    /// zone. Mirrors `AppConstants.OUT_OF_ZONE_PENALTY_INTERVAL_MS`
    /// on Android — both platforms must tick at the same cadence.
    static let outOfZonePenaltyIntervalSeconds: TimeInterval = 5

    // MARK: - Confetti
    static let confettiParticleCount = 80

    // MARK: - Normal Mode Settings
    static let normalModeFixedInterval: Double = 5 // minutes
    static let normalModeMinimumRadius: Double = 100 // meters
}

extension Logger {
    init(category: String) {
        self.init(subsystem: AppConstants.logSubsystem, category: category)
    }
}
