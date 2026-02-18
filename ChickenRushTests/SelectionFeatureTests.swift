//
//  SelectionFeatureTests.swift
//  ChickenRushTests
//

import ComposableArchitecture
import Testing
@testable import PouleParty

@MainActor
struct SelectionFeatureTests {

    @Test func startButtonTappedShowsJoinDialog() async {
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        }

        await store.send(.startButtonTapped) {
            $0.isJoiningGame = true
        }
    }

    @Test func wrongPasswordClearsField() async {
        var state = SelectionFeature.State()
        state.password = "wrongpassword"

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.validatePasswordButtonTapped) {
            $0.password = ""
        }
    }

    @Test func correctPasswordGoesToConfig() async {
        var state = SelectionFeature.State()
        state.password = ""

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.validatePasswordButtonTapped) {
            $0.isAuthenticating = false
        }

        await store.receive(\.goToChickenConfigTriggered) {
            $0.destination = .chickenConfig(
                ChickenConfigFeature.State(game: $0.destination.flatMap {
                    if case let .chickenConfig(configState) = $0 {
                        return configState.$game
                    }
                    return nil
                } ?? Shared(Game(id: "")))
            )
        }
    }

    @Test func dismissChickenConfigClearsDestination() async {
        var state = SelectionFeature.State()
        state.destination = .chickenConfig(
            ChickenConfigFeature.State(game: Shared(Game.mock))
        )

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.dismissChickenConfig) {
            $0.destination = nil
        }
    }

    @Test func joinGameWithEmptyCodeDoesNothing() async {
        var state = SelectionFeature.State()
        state.gameCode = "  "

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.joinGameButtonTapped)
    }
}
