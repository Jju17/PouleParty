//
//  HomeJoinFlowIntegrationTests.swift
//  PoulePartyTests
//
//  End-to-end TCA tests for the Home ↔ JoinFlow flow.
//
//  PP-64: re-enabled after PP-90 retired the registration-required gate.
//  The old `pendingRegistrationRejoinTapped` action + `PendingRegistration`
//  type are gone, so the test now only covers the post-PP-90 surface:
//  the JoinFlow delegate fires `joinGame(_, hunterName:)` and Home
//  immediately bridges to `hunterGameJoined`.

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
}
