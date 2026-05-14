//
//  MapFeatureState.swift
//  PouleParty
//
//  Shared read-only surface for ChickenMapFeature.State and
//  HunterMapFeature.State. Lets shared map components (bottom bar,
//  overlays, haptics modifier) accept any map state without duplicating
//  their parameter lists.
//

import CoreLocation
import Foundation

protocol MapFeatureState: Equatable {
    var game: Game { get }
    var nextRadiusUpdate: Date? { get }
    var nowDate: Date { get }
    var radius: Int { get }
    var mapCircle: CircleOverlay? { get }
    var winnerNotification: String? { get }
    var countdownNumber: Int? { get }
    var countdownText: String? { get }
    var isOutsideZone: Bool { get }
    var availablePowerUps: [PowerUp] { get }
    var collectedPowerUps: [PowerUp] { get }
    var powerUpNotification: String? { get }
    var lastActivatedPowerUpType: PowerUp.PowerUpType? { get }
    var showGameInfo: Bool { get }
    var showPowerUpInventory: Bool { get }
    /// `true` once the player's effective start time has passed.
    /// Chicken uses `game.startDate`, hunter uses `game.hunterStartDate`.
    var hasGameStarted: Bool { get }
    /// PP-16 — flipped to `true` at gameOver. PP-18 exposes the
    /// "View leaderboard" CTA based on this; PP-17's red overtime
    /// timer is driven by `endDate` and doesn't need this flag.
    var isGameOver: Bool { get }
}

extension ChickenMapFeature.State: MapFeatureState {}
extension HunterMapFeature.State: MapFeatureState {}
extension GameMasterMapFeature.State: MapFeatureState {}
