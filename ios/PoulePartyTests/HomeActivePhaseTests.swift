//
//  HomeActivePhaseTests.swift
//  PoulePartyTests
//
//  Pins the phase-aware active-game banner behaviour introduced on
//  2026-04-23: the Home banner must now distinguish an in-progress
//  game (CTA "Rejoin") from an upcoming game (CTA "Open" for the
//  chicken / "Join" for the hunter), and the dismissed-ids Set must
//  be applied so a previously-hidden game doesn't resurface.
//

import ComposableArchitecture
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct HomeActivePhaseTests {

    // MARK: - activeGameFound populates phase

    @Test func activeGameFoundInProgressSetsPhase() async {
        let game = Game.mock
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.activeGameFound(game, .hunter, .inProgress)) {
            $0.activeGame = game
            $0.activeGameRole = .hunter
            $0.activeGamePhase = .inProgress
        }
    }

    @Test func activeGameFoundUpcomingSetsPhase() async {
        let game = Game.mock
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.activeGameFound(game, .chicken, .upcoming)) {
            $0.activeGame = game
            $0.activeGameRole = .chicken
            $0.activeGamePhase = .upcoming
        }
    }

    // MARK: - dismiss persists by gameId

    @Test func dismissPersistsGameIdInSet() async {
        var game = Game.mock
        game.id = "to-dismiss"
        var state = HomeFeature.State()
        state.activeGame = game
        state.activeGameRole = .hunter
        state.activeGamePhase = .inProgress

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.activeGameBannerDismissed) {
            $0.activeGame = nil
            $0.activeGameRole = nil
            $0.activeGamePhase = nil
        }
        #expect(store.state.dismissedActiveGameIds.contains("to-dismiss"))
    }

    @Test func rejoinClearsDismissFlagForSameGame() async {
        var game = Game.mock
        game.id = "was-dismissed"
        var state = HomeFeature.State()
        state.activeGame = game
        state.activeGameRole = .chicken
        state.activeGamePhase = .upcoming
        state.$dismissedActiveGameIds.withLock { ids in
            ids.insert("was-dismissed")
            ids.insert("other-game")
        }

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.rejoinGameTapped)
        // The dismiss flag for this game is lifted, but other games stay.
        #expect(!store.state.dismissedActiveGameIds.contains("was-dismissed"))
        #expect(store.state.dismissedActiveGameIds.contains("other-game"))
    }

    // MARK: - noActiveGameFound clears phase

    @Test func noActiveGameFoundClearsAll() async {
        var game = Game.mock
        game.id = "stale"
        var state = HomeFeature.State()
        state.activeGame = game
        state.activeGameRole = .hunter
        state.activeGamePhase = .inProgress

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.noActiveGameFound) {
            $0.activeGame = nil
            $0.activeGameRole = nil
            $0.activeGamePhase = nil
        }
    }
}
