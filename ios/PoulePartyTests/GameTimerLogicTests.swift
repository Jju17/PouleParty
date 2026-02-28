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
            currentCircle: CircleOverlay(center: currentCenter, radius: 1500)
        )
        #expect(result != nil)
        #expect(result!.newCircle!.center.latitude == initialCoords.latitude)
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
}
