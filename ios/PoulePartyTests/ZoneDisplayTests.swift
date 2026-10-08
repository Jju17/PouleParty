import CoreLocation
import FirebaseCore
import Foundation
import Testing
@testable import PouleParty

struct ZoneDisplayTests {
    @Test func aStoredCenterReplacesTheCircle() {
        var state = HunterMapFeature.State(game: .mock)
        let center = CLLocationCoordinate2D(latitude: 50.8, longitude: 4.3)
        state.applyZone(ZoneRenderState(radius: 400, center: center, nextUpdate: nil))
        #expect(state.radius == 400)
        #expect(state.mapCircle?.center.latitude == 50.8)
        #expect(state.mapCircle?.radius == 400)
    }

    @Test func withoutAStoredCenterTheLiveCenterIsKept() {
        var state = HunterMapFeature.State(game: .mock)
        state.mapCircle = CircleOverlay(center: .init(latitude: 1, longitude: 2), radius: 900)
        let next = Date(timeIntervalSince1970: 1_900_000_000)
        state.applyZone(ZoneRenderState(radius: 300, center: nil, nextUpdate: next))
        #expect(state.mapCircle?.center.latitude == 1)
        #expect(state.mapCircle?.radius == 300)
        #expect(state.nextRadiusUpdate == next)
    }

    @Test func noCenterAtAllUsesTheFallback() {
        var state = ChickenMapFeature.State(game: .mock)
        state.mapCircle = nil
        state.applyZone(ZoneRenderState(radius: 200, center: nil, nextUpdate: nil), fallbackCenter: .init(latitude: 3, longitude: 4))
        #expect(state.mapCircle?.center.longitude == 4)
    }

    @Test func countdownTextAsksForADismissal() {
        var state = HunterMapFeature.State(game: .mock)
        let afterNumber = state.applyCountdown(.updateNumber(3))
        #expect(!afterNumber)
        #expect(state.countdownNumber == 3)
        let afterText = state.applyCountdown(.showText("Go"))
        #expect(afterText)
        #expect(state.countdownNumber == nil)
        #expect(state.countdownText == "Go")
        let afterNothing = state.applyCountdown(.noChange)
        #expect(!afterNothing)
    }

    @Test func nothingCountsDownBeforeAManualLaunch() {
        var game = Game.mock
        game.manualStartEnabled = true
        game.timing.actualStart = nil
        #expect(countdownPhases(for: .chicken, game: game).allSatisfy { !$0.isEnabled })
        #expect(countdownPhases(for: .hunter, game: game).allSatisfy { !$0.isEnabled })
        game.timing.actualStart = Timestamp(date: .now)
        #expect(countdownPhases(for: .hunter, game: game).last?.isEnabled == true)
    }

    @Test func theChickenHeadStartPhaseNeedsAHeadStart() {
        var game = Game.mock
        game.timing.headStartMinutes = 0
        #expect(countdownPhases(for: .chicken, game: game)[1].isEnabled == false)
        #expect(countdownPhases(for: .hunter, game: game)[0].isEnabled == false)
    }

    @Test func gameOverStateCountsRemainingHunters() {
        var game = Game.mock
        game.roles = ["c": "chicken", "h1": "hunter", "h2": "hunter"]
        game.winners = [Winner(hunterId: "h1", hunterName: "A", timestamp: Timestamp(date: .now))]
        let state = gameOverLiveActivityState(game: game, radius: 50)
        #expect(state.activeHunters == 1)
        #expect(state.winnersCount == 1)
        #expect(state.gamePhase == .gameOver)
    }
}
