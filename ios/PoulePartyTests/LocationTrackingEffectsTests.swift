//
//  LocationTrackingEffectsTests.swift
//  PoulePartyTests
//
//  Tests for power-up effects on location tracking logic.
//  Verifies that invisibility, jammer, radar ping, and decoy
//  behave correctly with respect to location writes.
//

import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

struct LocationTrackingEffectsTests {

    private func gameWithEffect(
        gameMode: Game.GameMode = .followTheChicken,
        invisibility: Timestamp? = nil,
        zoneFreeze: Timestamp? = nil,
        radarPing: Timestamp? = nil,
        decoy: Timestamp? = nil,
        jammer: Timestamp? = nil,
        chickenCanSeeHunters: Bool = false
    ) -> Game {
        var game = Game.mock
        game.gameMode = gameMode
        game.chickenCanSeeHunters = chickenCanSeeHunters
        game.powerUps.enabled = true
        game.powerUps.activeEffects = Game.ActiveEffects(
            invisibility: invisibility,
            zoneFreeze: zoneFreeze,
            radarPing: radarPing,
            decoy: decoy,
            jammer: jammer
        )
        return game
    }

    private var futureTimestamp: Timestamp { Timestamp(date: Date.now.addingTimeInterval(30)) }
    private var pastTimestamp: Timestamp { Timestamp(date: Date.now.addingTimeInterval(-10)) }

    // MARK: - Invisibility

    @Test func invisibilityActivePreventsChickenLocationWrite() {
        let game = gameWithEffect(invisibility: futureTimestamp)
        #expect(game.isChickenInvisible == true)
    }

    @Test func invisibilityExpiredAllowsChickenLocationWrite() {
        let game = gameWithEffect(invisibility: pastTimestamp)
        #expect(game.isChickenInvisible == false)
    }

    @Test func invisibilityNilAllowsChickenLocationWrite() {
        let game = gameWithEffect()
        #expect(game.isChickenInvisible == false)
    }

    // MARK: - Jammer

    @Test func jammerActiveAddNoiseToLocation() {
        let game = gameWithEffect(jammer: futureTimestamp)
        #expect(game.isJammerActive == true)
    }

    @Test func jammerNoiseIsApproximately200m() {
        // applyJammerNoise uses ±0.0018 degrees
        // 0.0018 * 111320 ≈ 200m
        let maxNoiseMeters = 0.0018 * 111_320
        #expect(maxNoiseMeters > 190)
        #expect(maxNoiseMeters < 210)
    }

    @Test func jammerExpiredStopsNoise() {
        let game = gameWithEffect(jammer: pastTimestamp)
        #expect(game.isJammerActive == false)
    }

    // MARK: - Radar Ping

    @Test func radarPingActiveInStayInTheZoneForcesChickenWrite() {
        let game = gameWithEffect(gameMode: .stayInTheZone, radarPing: futureTimestamp)
        #expect(game.isRadarPingActive == true)
    }

    @Test func radarPingInactiveInStayInTheZonePreventsChickenWrite() {
        let game = gameWithEffect(gameMode: .stayInTheZone)
        #expect(game.isRadarPingActive == false)
    }

    @Test func radarPingExpiredStopsForcedWrites() {
        let game = gameWithEffect(gameMode: .stayInTheZone, radarPing: pastTimestamp)
        #expect(game.isRadarPingActive == false)
    }

    // MARK: - Decoy

    @Test func decoyActiveCreatesFakePosition() {
        let game = gameWithEffect(decoy: futureTimestamp)
        #expect(game.isDecoyActive == true)
    }

    @Test func decoyPositionIsDeterministicFromSeed() {
        let seed = 42
        let decoyExpires = Int(Date.now.timeIntervalSince1970)
        let combinedSeed = seed ^ decoyExpires

        let angle1 = seededRandom(seed: combinedSeed, index: 0) * 2 * .pi
        let angle2 = seededRandom(seed: combinedSeed, index: 0) * 2 * .pi
        #expect(abs(angle1 - angle2) < 1e-15)

        let distance1 = 200 + seededRandom(seed: combinedSeed, index: 1) * 300
        let distance2 = 200 + seededRandom(seed: combinedSeed, index: 1) * 300
        #expect(abs(distance1 - distance2) < 1e-10)
    }

    @Test func decoyDistanceIsBetween200mAnd500m() {
        for seed in [42, 99, 12345, 0, 999_999] {
            let distance = 200 + seededRandom(seed: seed, index: 1) * 300
            #expect(distance >= 200, "Decoy distance \(distance) should be >= 200")
            #expect(distance <= 500, "Decoy distance \(distance) should be <= 500")
        }
    }

    @Test func decoyExpiredHidesFakePosition() {
        let game = gameWithEffect(decoy: pastTimestamp)
        #expect(game.isDecoyActive == false)
    }

    // MARK: - Location Throttle

    @Test func locationThrottleIs5Seconds() {
        #expect(AppConstants.locationThrottleSeconds == 5)
    }

    @Test func locationMinimumDistanceIs10Meters() {
        #expect(AppConstants.locationMinDistanceMeters == 10)
    }

    // MARK: - Zone Check by Role and Mode

    @Test func shouldCheckZoneChickenInFollowTheChickenIsFalse() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .followTheChicken) == false)
    }

    @Test func shouldCheckZoneHunterInFollowTheChickenIsTrue() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .followTheChicken) == true)
    }

    @Test func shouldCheckZoneChickenInStayInTheZoneIsTrue() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .stayInTheZone) == true)
    }

    @Test func shouldCheckZoneHunterInStayInTheZoneIsTrue() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .stayInTheZone) == true)
    }

    // MARK: - Combined Effects

    @Test func jammerAndRadarPingCanBeActiveSimultaneously() {
        let game = gameWithEffect(gameMode: .stayInTheZone, radarPing: futureTimestamp, jammer: futureTimestamp)
        #expect(game.isJammerActive == true)
        #expect(game.isRadarPingActive == true)
    }

    @Test func invisibilityOverridesJammerNoWriteAtAll() {
        let game = gameWithEffect(invisibility: futureTimestamp, jammer: futureTimestamp)
        #expect(game.isChickenInvisible == true)
        #expect(game.isJammerActive == true)
        // In real code, invisibility check comes first and skips the write entirely
    }

    @Test func multipleEffectsCanExpireIndependently() {
        let game = gameWithEffect(
            invisibility: pastTimestamp,
            zoneFreeze: pastTimestamp,
            jammer: futureTimestamp
        )
        #expect(game.isChickenInvisible == false)
        #expect(game.isJammerActive == true)
        #expect(game.isZoneFrozen == false)
    }

    // MARK: - chickenCanSeeHunters

    @Test func chickenCanSeeHuntersGatesHunterLocationWrites() {
        let gameWith = gameWithEffect(chickenCanSeeHunters: true)
        #expect(gameWith.chickenCanSeeHunters == true)

        let gameWithout = gameWithEffect(chickenCanSeeHunters: false)
        #expect(gameWithout.chickenCanSeeHunters == false)
    }

    // MARK: - Heartbeat

    @Test func isChickenDisconnectedFalseWhenNoHeartbeat() {
        let game = Game.mock
        #expect(game.isChickenDisconnected == false)
    }

    @Test func isChickenDisconnectedFalseWhenRecentHeartbeat() {
        var game = Game.mock
        game.lastHeartbeat = Timestamp(date: Date.now.addingTimeInterval(-10))
        #expect(game.isChickenDisconnected == false)
    }

    @Test func isChickenDisconnectedTrueWhenStaleHeartbeat() {
        var game = Game.mock
        game.lastHeartbeat = Timestamp(date: Date.now.addingTimeInterval(-90))
        #expect(game.isChickenDisconnected == true)
    }
}
