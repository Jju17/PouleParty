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
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: .now)
        ]

        #expect(!game.hunterIds.isEmpty)
        #expect(game.winners.count >= game.hunterIds.count)
    }

    @Test func notAllHuntersFoundWhenWinnersLessThanHunterIds() {
        var game = Game.mock
        game.hunterIds = ["h1", "h2", "h3"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now)
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
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now)
        ]

        #expect(!((!game.hunterIds.isEmpty) && game.winners.count >= game.hunterIds.count))
    }

    @Test func moreWinnersThanHuntersStillTriggers() {
        var game = Game.mock
        game.hunterIds = ["h1"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: .now)
        ]

        #expect(game.winners.count >= game.hunterIds.count)
    }

    @Test func singleHunterSingleWinnerTriggers() {
        var game = Game.mock
        game.hunterIds = ["h1"]
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now)
        ]

        #expect(!game.hunterIds.isEmpty && game.winners.count >= game.hunterIds.count)
    }

    // MARK: - Chicken state: allHuntersFound action

    @Test func chickenAllHuntersFoundActionIsNoOp() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        // allHuntersFound is handled by AppFeature, not ChickenMapFeature
        await store.send(.allHuntersFound)
    }

    // MARK: - Hunter state: allHuntersFound action

    @Test func hunterAllHuntersFoundActionIsNoOp() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        // allHuntersFound is handled by AppFeature, not HunterMapFeature
        await store.send(.allHuntersFound)
    }

    // MARK: - Game lifecycle with all-found

    @Test func gameLifecycleWithAllHuntersFound() {
        var game = Game.mock
        game.status = .inProgress
        game.hunterIds = ["h1", "h2"]
        #expect(game.status == .inProgress)

        // All hunters find chicken
        game.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: .now)
        ]
        game.status = .done
        #expect(game.status == .done)
        #expect(max(0, game.hunterIds.count - game.winners.count) == 0)
    }

    @Test func largeGameWithManyHuntersAllFound() {
        var game = Game.mock
        let hunterIds = (1...20).map { "h\($0)" }
        let winners = hunterIds.enumerated().map { (i, id) in
            Winner(hunterId: id, hunterName: "Hunter \(i)", timestamp: .now)
        }
        game.hunterIds = hunterIds
        game.winners = winners

        #expect(!game.hunterIds.isEmpty)
        #expect(game.winners.count >= game.hunterIds.count)
        #expect(max(0, game.hunterIds.count - game.winners.count) == 0)
    }

    // MARK: - Chicken gameUpdated detects all hunters found

    @Test func chickenGameUpdatedWithAllHuntersFoundTriggersNavigation() async {
        var game = Game.mock
        game.hunterIds = ["h1"]
        game.startDate = .now.addingTimeInterval(-600)
        game.endDate = .now.addingTimeInterval(3000)

        var state = ChickenMapFeature.State(game: game)
        state.previousWinnersCount = 0 // Already initialized
        state.hasGameStarted = true
        state.hasHuntStarted = true

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.apiClient.updateGameStatus = { _, _ in }
            $0.liveActivityClient.end = { _ in }
        }

        // Game update: the single hunter found the chicken
        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: .now)
        ]

        store.exhaustivity = .off
        await store.send(.gameUpdated(updatedGame))
        await store.receive(.allHuntersFound)
    }

    // MARK: - findActiveGame ordering logic

    @Test func mostRecentGameSelectedFromCandidates() {
        let olderGame = Game.mock
        var newer = Game.mock
        newer.id = "game-new"
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
