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
                    ButtonState(action: .openSettings) {
                        TextState("Open Settings")
                    }
                    ButtonState(role: .cancel) {
                        TextState("OK")
                    }
                } message: {
                    TextState("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.")
                }
            )
        }
    }

    @Test func createPartyWithLocationKicksOffDailyFreeLimitCheck() async {
        // PP-42: PlanSelection was retired. Tapping "Create Party" with
        // location granted now flows directly into the daily-free-limit check
        // (no intermediary sheet) and surfaces a `dailyFreeLimitChecked`
        // action when the limit allows another game.
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.locationClient.authorizationStatus = { .authorizedWhenInUse }
            $0.userClient.currentUserId = { "user-123" }
            $0.apiClient.countFreeGamesToday = { _ in 0 }
        }
        store.exhaustivity = .off

        await store.send(.createPartyTapped)
        await store.receive(\.dailyFreeLimitChecked)
    }

    @Test func adminModeTappedIsANoOpUntilPP45() async {
        // PP-42: the "Mode admin" button is a UI placeholder; PP-45 fills in
        // the modal asking for the `jujurahier` code.
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        }
        await store.send(.adminModeTapped)
    }

    @Test func webCreatePartyTappedIsANoOpUntilPP46() async {
        // PP-42: the "Envie de créer une partie ?" button is a UI placeholder;
        // PP-46 wires it to the localized landing page.
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        }
        await store.send(.webCreatePartyTapped)
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
            $0.apiClient.findActiveGame = { _ in (game, GameRole.hunter, GamePhase.inProgress) }
        }

        await store.send(.onTask)
        await store.receive(\.activeGameFound) {
            $0.activeGame = game
            $0.activeGameRole = .hunter
            $0.activeGamePhase = .inProgress
        }
    }

    @Test func onTaskFindsActiveGameAsChicken() async {
        let game = Game.mock
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-123" }
            $0.apiClient.findActiveGame = { _ in (game, GameRole.chicken, GamePhase.inProgress) }
        }

        await store.send(.onTask)
        await store.receive(\.activeGameFound) {
            $0.activeGame = game
            $0.activeGameRole = .chicken
            $0.activeGamePhase = .inProgress
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
