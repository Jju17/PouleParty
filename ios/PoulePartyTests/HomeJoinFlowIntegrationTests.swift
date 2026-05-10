//
//  HomeJoinFlowIntegrationTests.swift
//  PoulePartyTests
//
//  End-to-end TCA tests for the Home ↔ JoinFlow flow.
//

import ComposableArchitecture
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct HomeJoinFlowIntegrationTests {

    @Test func joinDelegateForWaitingGameSendsHunterGameJoined() async {
        var game = Game.mock
        game.status = .waiting

        var state = HomeFeature.State()
        state.destination = .joinFlow(JoinFlowFeature.State())

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.destination(.presented(.joinFlow(.delegate(.joinGame(game, hunterName: "Tester"))))))
        await store.receive(\.hunterGameJoined)
        #expect(store.state.destination == nil)
    }

    @Test func pendingRegistrationRejoinWaitingRoutesToHunterGameJoined() async {
        var game = Game.mock
        game.id = "game-ready"
        game.status = .waiting
        let pending = PendingRegistration(
            gameId: "game-ready",
            gameCode: game.gameCode,
            teamName: "Team",
            startDate: game.startDate
        )
        var state = HomeFeature.State()
        state.$pendingRegistration.withLock { $0 = pending }

        let store = TestStore(initialState: state) {
            HomeFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.getConfig = { _ in game }
        }
        store.exhaustivity = .off

        await store.send(.pendingRegistrationRejoinTapped)
        await store.receive(\.hunterGameJoined)
    }
}
