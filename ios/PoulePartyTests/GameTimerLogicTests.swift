//
//  GameTimerLogicTests.swift
//  PoulePartyTests
//

import CoreLocation
import Testing
@testable import PouleParty

struct GameTimerLogicTests {

    // MARK: - evaluateCountdown

    @Test func countdownNoChangeWhenFarFromTarget() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(60),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownShowsNumberWhenWithinThreshold() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(2.5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .updateNumber(3))
    }

    @Test func countdownNoChangeWhenNumberAlreadyShown() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(2.5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: 3,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownShowsTextOnCompletion() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-0.5),
            completionText: "RUN!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .showText("RUN!"))
    }

    @Test func countdownSkipsDisabledPhases() {
        let disabledPhase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(2),
            completionText: "SKIP",
            showNumericCountdown: true,
            isEnabled: false
        )
        let enabledPhase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-0.5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [disabledPhase, enabledPhase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .showText("GO!"))
    }

    @Test func countdownNoChangeAfterAllPhasesComplete() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownNoChangeWhenTextAlreadyShowing() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-0.5),
            completionText: "RUN!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: "RUN!"
        )
        #expect(result == .noChange)
    }

    // MARK: - checkGameOverByTime

    @Test func gameOverByTimeWhenPastEnd() {
        #expect(checkGameOverByTime(endDate: Date.now.addingTimeInterval(-1)) == true)
    }

    @Test func gameNotOverWhenBeforeEnd() {
        #expect(checkGameOverByTime(endDate: Date.now.addingTimeInterval(60)) == false)
    }

    // MARK: - processRadiusUpdate

    @Test func radiusUpdateReturnsNilWhenNoNextUpdate() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: nil,
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result == nil)
    }

    @Test func radiusUpdateReturnsNilWhenNotDueYet() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(60),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result == nil)
    }

    @Test func radiusUpdateShrinksWhenDue() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: CircleOverlay(
                center: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
                radius: 1500
            )
        )
        #expect(result != nil)
        #expect(result!.newRadius == 1400)
        #expect(result!.isGameOver == false)
    }

    @Test func radiusUpdateGameOverWhenRadiusReachesZero() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 100,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result != nil)
        #expect(result!.isGameOver == true)
        #expect(result!.gameOverMessage == "The zone has collapsed!")
    }

    @Test func radiusUpdateUsesInitialCoordsForStayInTheZone() {
        let initialCoords = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let currentCenter = CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0)
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .stayInTheZone,
            initialCoordinates: initialCoords,
            currentCircle: CircleOverlay(center: currentCenter, radius: 1500),
            driftSeed: 12345
        )
        #expect(result != nil)
        // With drift, center is close to initialCoords but not exactly equal
        let distance = CLLocation(latitude: result!.newCircle!.center.latitude, longitude: result!.newCircle!.center.longitude)
            .distance(from: CLLocation(latitude: initialCoords.latitude, longitude: initialCoords.longitude))
        // Max drift = min(1400*0.5, 100*0.5) = 50m
        #expect(distance < 60, "Drifted center should be within 60m of base point")
    }

    // MARK: - deterministicDriftCenter

    @Test func driftCenterIsDeterministic() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result1 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        let result2 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        #expect(result1.latitude == result2.latitude)
        #expect(result1.longitude == result2.longitude)
    }

    @Test func driftCenterDiffersForDifferentRadius() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result1 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        let result2 = deterministicDriftCenter(basePoint: base, oldRadius: 1400, newRadius: 1300, driftSeed: 12345)
        let areDifferent = result1.latitude != result2.latitude || result1.longitude != result2.longitude
        #expect(areDifferent, "Different radius values should produce different drift centers")
    }

    @Test func driftCenterDiffersForDifferentSeeds() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result1 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 11111)
        let result2 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 99999)
        let areDifferent = result1.latitude != result2.latitude || result1.longitude != result2.longitude
        #expect(areDifferent, "Different seeds should produce different drift centers")
    }

    @Test func driftCenterStaysCloseToBase() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 54321)
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        // safeDrift = min(1400*0.5, 100*0.5) = 50m
        #expect(distance <= 50, "Drifted center must be within safeDrift of base")
    }

    @Test func driftCenterReturnsBaseWhenNoRoom() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        // oldRadius == newRadius → no room to drift
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1000, newRadius: 1000, driftSeed: 12345)
        #expect(result.latitude == base.latitude)
        #expect(result.longitude == base.longitude)
    }

    // MARK: - detectNewWinners

    @Test func detectNewWinnersReturnsNilWhenNoNewWinners() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now)
        ]
        let result = detectNewWinners(winners: winners, previousCount: 1)
        #expect(result == nil)
    }

    @Test func detectNewWinnersReturnsNotification() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: .now)
        ]
        let result = detectNewWinners(winners: winners, previousCount: 1)
        #expect(result == "Bob a trouvé la poule !")
    }

    @Test func detectNewWinnersFiltersOwnHunter() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now),
            Winner(hunterId: "me", hunterName: "Me", timestamp: .now)
        ]
        let result = detectNewWinners(winners: winners, previousCount: 1, ownHunterId: "me")
        #expect(result == nil)
    }

    @Test func detectNewWinnersWithEmptyList() {
        let result = detectNewWinners(winners: [], previousCount: 0)
        #expect(result == nil)
    }

    @Test func detectNewWinnersFromZero() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now)
        ]
        let result = detectNewWinners(winners: winners, previousCount: 0)
        #expect(result == "Alice a trouvé la poule !")
    }

    // MARK: - shouldCheckZone

    @Test func shouldCheckZoneChickenStayInTheZone() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .stayInTheZone) == true)
    }

    @Test func shouldCheckZoneHunterStayInTheZone() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .stayInTheZone) == true)
    }

    @Test func shouldCheckZoneChickenFollowTheChicken() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .followTheChicken) == false)
    }

    @Test func shouldCheckZoneHunterFollowTheChicken() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .followTheChicken) == true)
    }

    @Test func shouldCheckZoneChickenMutualTracking() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .mutualTracking) == false)
    }

    @Test func shouldCheckZoneHunterMutualTracking() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .mutualTracking) == true)
    }

    // MARK: - checkZoneStatus

    @Test func checkZoneStatusInsideZone() {
        // Brussels center, ~100m away, 1500m radius → inside
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let nearby = CLLocationCoordinate2D(latitude: 50.8470, longitude: 4.3530)
        let result = checkZoneStatus(userLocation: nearby, zoneCenter: center, zoneRadius: 1500)
        #expect(result.isOutsideZone == false)
    }

    @Test func checkZoneStatusOutsideZone() {
        // Brussels center vs Namur (~60km away), 1500m radius → outside
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let farAway = CLLocationCoordinate2D(latitude: 50.4674, longitude: 4.8720)
        let result = checkZoneStatus(userLocation: farAway, zoneCenter: center, zoneRadius: 1500)
        #expect(result.isOutsideZone == true)
    }

    @Test func checkZoneStatusAtCenter() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let result = checkZoneStatus(userLocation: center, zoneCenter: center, zoneRadius: 1500)
        #expect(result.isOutsideZone == false)
        #expect(result.distanceToCenter < 1)
    }
}
