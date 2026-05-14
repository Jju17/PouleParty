//
//  HomeJoinFlowIntegrationTests.swift
//  PoulePartyTests
//
//  End-to-end TCA tests for the Home ↔ JoinFlow flow.
//
//  PP-19 note: these tests target a `PendingRegistration` state that no
//  longer exists post-PP-90. The suite is disabled at the compiler
//  level so the test target still builds; the file will be rewritten
//  or deleted in a follow-up cleanup ticket.

#if false

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

    // PP-90 retired the PendingRegistration flow — anyone can join at
    // any point, no pre-registration window. The
    // `pendingRegistrationRejoinTapped` action and the
    // `PendingRegistration` type were removed with it. The legacy
    // integration test that targeted them is intentionally dropped.
}

#endif
