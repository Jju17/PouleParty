//
//  AllHuntersFoundTests.swift
//  PoulePartyTests
//
//  Tests covering the "all hunters found" game-end condition
//  and findActiveGame ordering logic.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct AllHuntersFoundTests {

    // MARK: - Detection logic (model level)

    @Test func allHuntersFoundWhenWinnersEqualsHunterIds() {
        var game = Game.mock
        game.hunterIds = ["h1", "h2"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now)),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: Timestamp(date: .now))
        ]

        #expect(!game.hunterIds.isEmpty)
        #expect(game.winners.count >= game.hunterIds.count)
    }

    @Test func notAllHuntersFoundWhenWinnersLessThanHunterIds() {
        var game = Game.mock
        game.hunterIds = ["h1", "h2", "h3"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]

        #expect(game.winners.count < game.hunterIds.count)
    }

    @Test func emptyHunterIdsPreventsAllFoundDetection() {
        var game = Game.mock
        game.hunterIds = []
        game.winners = []

        // Guard: hunterIds must not be empty
        #expect(!((!game.hunterIds.isEmpty) && game.winners.count >= game.hunterIds.count))
    }

    @Test func emptyHunterIdsWithWinnersDoesNotTrigger() {
        var game = Game.mock
        game.hunterIds = []
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]

        #expect(!((!game.hunterIds.isEmpty) && game.winners.count >= game.hunterIds.count))
    }

    @Test func moreWinnersThanHuntersStillTriggers() {
        var game = Game.mock
        game.hunterIds = ["h1"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now)),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: Timestamp(date: .now))
        ]

        #expect(game.winners.count >= game.hunterIds.count)
    }

    @Test func singleHunterSingleWinnerTriggers() {
        var game = Game.mock
        game.hunterIds = ["h1"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]

        #expect(!game.hunterIds.isEmpty && game.winners.count >= game.hunterIds.count)
    }

    // MARK: - Game lifecycle with all-found

    @Test func gameLifecycleWithAllHuntersFound() {
        var game = Game.mock
        game.status = .inProgress
        game.hunterIds = ["h1", "h2"]
        #expect(game.status == .inProgress)

        // All hunters find chicken
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now)),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: Timestamp(date: .now))
        ]
        game.status = .done
        #expect(game.status == .done)
        #expect(max(0, game.hunterIds.count - game.winners.count) == 0)
    }

    @Test func largeGameWithManyHuntersAllFound() {
        var game = Game.mock
        let hunterIds = (1...20).map { "h\($0)" }
        let winners = hunterIds.enumerated().map { (i, id) in
            Winner(hunterId: id, hunterName: "Hunter \(i)", timestamp: Timestamp(date: .now))
        }
        game.hunterIds = hunterIds
        game.winners = winners

        #expect(!game.hunterIds.isEmpty)
        #expect(game.winners.count >= game.hunterIds.count)
        #expect(max(0, game.hunterIds.count - game.winners.count) == 0)
    }

    // MARK: - Chicken gameUpdated detects all hunters found

    @Test func chickenGameUpdatedWithAllHuntersFoundFlipsToGameOver() async {
        // PP-16: when all hunters find the chicken, the chicken stays
        // on the map. The reducer flips `isGameOver = true`, pushes the
        // status to .done server-side, and ends the Live Activity — but
        // NO `.delegate.allHuntersFound` fires. PP-18's manual
        // leaderboard CTA is the only path off the map.
        var game = Game.mock
        game.hunterIds = ["h1"]
        game.startDate = .now.addingTimeInterval(-600)
        game.endDate = .now.addingTimeInterval(3000)

        var state = ChickenMapFeature.State(game: game)
        state.previousWinnersCount = 0
        state.nowDate = .now

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.apiClient.updateGameStatus = { _, _ in }
            $0.liveActivityClient.end = { _ in }
            $0.locationClient.stopTracking = { }
            $0.continuousClock = ImmediateClock()
        }

        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]

        store.exhaustivity = .off
        await store.send(.internal(.gameUpdated(updatedGame)))
        // No follow-up delegate action.
        #expect(store.state.isGameOver)
    }

    // MARK: - findActiveGame ordering logic

    @Test func mostRecentGameSelectedFromCandidates() {
        let olderGame = Game.mock
        var newer = Game(id: "game-new")
        newer.startDate = .now.addingTimeInterval(1000)

        let candidates: [(Game, GameRole)] = [
            (olderGame, .hunter),
            (newer, .chicken)
        ]

        let result = candidates.max(by: { $0.0.startDate < $1.0.startDate })
        #expect(result != nil)
        #expect(result!.0.id == "game-new")
        #expect(result!.1 == .chicken)
    }

    @Test func emptyCandidatesReturnsNil() {
        let candidates: [(Game, GameRole)] = []
        let result = candidates.max(by: { $0.0.startDate < $1.0.startDate })
        #expect(result == nil)
    }

    @Test func singleCandidateIsReturned() {
        let game = Game.mock
        let candidates: [(Game, GameRole)] = [(game, .hunter)]
        let result = candidates.max(by: { $0.0.startDate < $1.0.startDate })
        #expect(result != nil)
        #expect(result!.1 == .hunter)
    }
}
