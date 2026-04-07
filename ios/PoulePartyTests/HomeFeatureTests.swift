//
//  HomeFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct HomeFeatureTests {

    @Test func startButtonTappedShowsJoinFlow() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.locationClient.authorizationStatus = { .authorizedWhenInUse }
        }

        await store.send(.startButtonTapped) {
            $0.destination = .joinFlow(JoinFlowFeature.State())
        }
    }

    @Test func createPartyWithoutLocationShowsAlert() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.locationClient.authorizationStatus = { .denied }
        }

        await store.send(.createPartyTapped)
        await store.receive(\.locationPermissionDenied) {
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

    @Test func createPartyWithLocationShowsPlanSelection() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.locationClient.authorizationStatus = { .authorizedWhenInUse }
        }

        await store.send(.createPartyTapped) {
            $0.destination = .planSelection(PlanSelectionFeature.State())
        }
    }

    @Test func initialStateHasDefaultValues() {
        let state = HomeFeature.State()
        #expect(state.gameCode == "")
        #expect(state.destination == nil)
    }

    // MARK: - Rejoin Game

    @Test func initialStateHasNilActiveGame() {
        let state = HomeFeature.State()
        #expect(state.activeGame == nil)
        #expect(state.activeGameRole == nil)
    }

    @Test func onTaskWithNoUserIdDoesNothing() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { nil }
        }

        await store.send(.onTask)
    }

    @Test func onTaskFindsActiveGameAsHunter() async {
        let game = Game.mock
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-123" }
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
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-123" }
            $0.apiClient.findActiveGame = { _ in (game, .chicken) }
        }

        await store.send(.onTask)
        await store.receive(\.activeGameFound) {
            $0.activeGame = game
            $0.activeGameRole = .chicken
        }
    }

    @Test func onTaskFindsNoActiveGame() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-123" }
            $0.apiClient.findActiveGame = { _ in nil }
            $0.continuousClock = ImmediateClock()
        }

        await store.send(.onTask)
        await store.receive(\.noActiveGameFound)
    }

    @Test func rejoinGameTappedAsHunterNavigatesToHunterMap() async {
        var state = HomeFeature.State()
        state.activeGame = Game.mock
        state.activeGameRole = .hunter

        let store = TestStore(initialState: state) {
            HomeFeature()
        }

        await store.send(.rejoinGameTapped) {
            $0.activeGame = nil
            $0.activeGameRole = nil
        }
        await store.receive(\.hunterGameJoined)
    }

    @Test func rejoinGameTappedAsChickenNavigatesToChickenMap() async {
        var state = HomeFeature.State()
        state.activeGame = Game.mock
        state.activeGameRole = .chicken

        let store = TestStore(initialState: state) {
            HomeFeature()
        }

        await store.send(.rejoinGameTapped) {
            $0.activeGame = nil
            $0.activeGameRole = nil
        }
        await store.receive(\.chickenGameStarted)
    }

    @Test func rejoinGameTappedWithNoActiveGameDoesNothing() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        }

        await store.send(.rejoinGameTapped)
    }
}
