import ComposableArchitecture
import FirebaseCore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct InjectedClockTests {
    @Test func theHunterTimerReadsTheInjectedClock() async {
        let pinned = Date(timeIntervalSince1970: 1_900_000_000)
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        } withDependencies: {
            $0.now = .constant(pinned)
        }
        store.exhaustivity = .off
        await store.send(.internal(.timerTicked)) {
            $0.nowDate = pinned
        }
    }

    @Test func theCooldownStartsFromTheInjectedClock() async {
        let pinned = Date(timeIntervalSince1970: 1_900_000_000)
        var state = HunterMapFeature.State(game: .mock)
        state.wrongCodeAttempts = 2
        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.now = .constant(pinned)
            $0.remoteConfigClient.codeMaxWrongAttempts = { 3 }
            $0.remoteConfigClient.codeCooldownSeconds = { 10 }
        }
        store.exhaustivity = .off
        await store.send(.internal(.wrongCodeRejected(lockedUntil: nil))) {
            $0.codeCooldownUntil = pinned.addingTimeInterval(10)
        }
    }

    @Test func gameOverByTimeUsesTheGivenInstant() {
        let end = Date(timeIntervalSince1970: 1_900_000_000)
        #expect(!checkGameOverByTime(endDate: end, now: end.addingTimeInterval(-1)))
        #expect(checkGameOverByTime(endDate: end, now: end))
    }

    @Test func effectWindowsUseTheGivenInstant() {
        var game = Game.mock
        let until = Date(timeIntervalSince1970: 1_900_000_000)
        game.powerUps.activeEffects.jammer = .init(date: until)
        #expect(game.isJammerActive(at: until.addingTimeInterval(-1)))
        #expect(!game.isJammerActive(at: until))
    }
}
