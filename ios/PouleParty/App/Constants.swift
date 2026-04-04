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
    static let prefIsMusicMuted = "isMusicMuted"

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
    static let powerUpCollectionRadiusMeters: Double = 30
    static let powerUpInitialBatchSize = 5
    static let powerUpPeriodicBatchSize = 2

    // MARK: - Nickname
    static let nicknameMaxLength = 20

    // MARK: - Zone
    // Grace period disabled — kept for future game mode
    // static let outsideZoneGracePeriodSeconds = 30

    // MARK: - Confetti
    static let confettiParticleCount = 80

    // MARK: - Pricing (Admin-defined)
    static let flatPricePerPlayerCents = 300 // 3€ per player
    static let flatMinPlayers = 6
    static let flatMaxPlayers = 50
    static let depositAmountCents = 1000 // 10€ deposit
    static let commissionPercent: Double = 15.0

    // MARK: - Normal Mode Settings
    static let normalModeFixedInterval: Double = 5 // minutes
    static let normalModeMinimumRadius: Double = 100 // meters
}
