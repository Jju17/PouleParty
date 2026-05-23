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
        }
        store.exhaustivity = .off

        await store.send(.startButtonTapped)
        await store.receive(\.joinFlowAuthorized) {
            $0.destination = .joinFlow(JoinFlowFeature.State())
        }
    }

    @Test func createPartyStartsChickenConfigFlow() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-123" }
        }
        store.exhaustivity = .off

        await store.send(.createPartyTapped)
        await store.receive(\.chickenConfigLocationRequested)
    }

    @Test func createPartyLongPressOpensAdminCodeAlert() async {
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.createPartyLongPressed)
        await store.receive(\.adminCodeAlertRequested) {
            $0.adminCodeInput = ""
            $0.isShowingAdminCodeAlert = true
        }
    }

    @Test func adminCodeValidateWithCorrectCodeKicksOffLocationFlow() async {
        var state = HomeFeature.State()
        state.isShowingAdminCodeAlert = true
        state.adminCodeInput = AdminCode.value
        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off
        await store.send(.adminCodeValidateTapped) {
            $0.isShowingAdminCodeAlert = false
            $0.adminCodeInput = ""
            $0.pendingIsAdminCreation = true
        }
        await store.receive(\.chickenConfigLocationRequested)
    }

    @Test func adminCodeValidateWithWrongCodeShowsAlertAndDoesNotProceed() async {
        var state = HomeFeature.State()
        state.isShowingAdminCodeAlert = true
        state.adminCodeInput = "nope"
        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off
        await store.send(.adminCodeValidateTapped)
        #expect(store.state.isShowingAdminCodeAlert == false)
        #expect(store.state.pendingIsAdminCreation == false)
        if case .alert = store.state.destination {
            // OK — the wrong-code alert is presented.
        } else {
            Issue.record("Expected wrong-code alert destination")
        }
    }

    @Test func adminCodeDismissedClearsState() async {
        var state = HomeFeature.State()
        state.isShowingAdminCodeAlert = true
        state.adminCodeInput = "abc"
        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        await store.send(.adminCodeDismissed) {
            $0.isShowingAdminCodeAlert = false
            $0.adminCodeInput = ""
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
