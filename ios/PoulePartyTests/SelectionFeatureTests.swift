//
//  SelectionFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import Foundation
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

    @Test func validatePasswordWithoutLocationShowsAlert() async {
        var state = SelectionFeature.State()
        state.isAuthenticating = true

        let store = TestStore(initialState: state) {
            SelectionFeature()
        } withDependencies: {
            $0.locationClient.authorizationStatus = { .denied }
        }

        await store.send(.validatePasswordButtonTapped) {
            $0.isAuthenticating = false
            $0.password = ""
        }
        await store.receive(\.locationRequired) {
            $0.destination = .alert(
                AlertState {
                    TextState("Location Required")
                } actions: {
                    ButtonState(role: .cancel) {
                        TextState("OK")
                    }
                } message: {
                    TextState("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.")
                }
            )
        }
    }

    @Test func initialStateHasDefaultValues() {
        let state = SelectionFeature.State()
        #expect(state.gameCode == "")
        #expect(state.isJoiningGame == false)
        #expect(state.destination == nil)
    }

    @Test func initialStateHasEmptyHunterName() {
        let state = SelectionFeature.State()
        #expect(state.hunterName == "")
    }

    // MARK: - Rejoin Game

    @Test func initialStateHasNilActiveGame() {
        let state = SelectionFeature.State()
        #expect(state.activeGame == nil)
        #expect(state.activeGameRole == nil)
    }

    @Test func onTaskWithNoUserIdDoesNothing() async {
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        } withDependencies: {
            $0.authClient.currentUserId = { nil }
        }

        await store.send(.onTask)
    }

    @Test func onTaskFindsActiveGameAsHunter() async {
        let game = Game.mock
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        } withDependencies: {
            $0.authClient.currentUserId = { "user-123" }
            $0.apiClient.findActiveGame = { _ in (game, .hunter) }
        }

        await store.send(.onTask)
        await store.receive(\.activeGameFound) {
            $0.activeGame = game
            $0.activeGameRole = .hunter
        }
    }

    @Test func onTaskFindsActiveGameAsChicken() async {
        let game = Game.mock
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        } withDependencies: {
            $0.authClient.currentUserId = { "user-123" }
            $0.apiClient.findActiveGame = { _ in (game, .chicken) }
        }

        await store.send(.onTask)
        await store.receive(\.activeGameFound) {
            $0.activeGame = game
            $0.activeGameRole = .chicken
        }
    }

    @Test func onTaskFindsNoActiveGame() async {
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        } withDependencies: {
            $0.authClient.currentUserId = { "user-123" }
            $0.apiClient.findActiveGame = { _ in nil }
        }

        await store.send(.onTask)
        await store.receive(\.noActiveGameFound)
    }

    @Test func rejoinGameTappedAsHunterNavigatesToHunterMap() async {
        var state = SelectionFeature.State()
        state.activeGame = Game.mock
        state.activeGameRole = .hunter

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.rejoinGameTapped) {
            $0.activeGame = nil
            $0.activeGameRole = nil
        }
        await store.receive(\.goToHunterMapTriggered)
    }

    @Test func rejoinGameTappedAsChickenNavigatesToChickenMap() async {
        var state = SelectionFeature.State()
        state.activeGame = Game.mock
        state.activeGameRole = .chicken

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.rejoinGameTapped) {
            $0.activeGame = nil
            $0.activeGameRole = nil
        }
        await store.receive(\.goToChickenMapTriggered)
    }

    @Test func rejoinGameTappedWithNoActiveGameDoesNothing() async {
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        }

        await store.send(.rejoinGameTapped)
    }
}
