//
//  HomeJoinFlowIntegrationTests.swift
//  PoulePartyTests
//
//  End-to-end TCA tests for the Home ↔ JoinFlow ↔ PaymentFeature flow,
//  focused on the critical bugs flagged in the 2026-04-23 audit:
//   - pending_payment / payment_failed games can't be joined; an alert
//     surfaces instead of silent no-op
//   - hunter Caution webhook verification clears the optimistic pending
//     banner on timeout
//   - pendingRegistrationRejoinTapped respects game.status transitions
//

import ComposableArchitecture
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct HomeJoinFlowIntegrationTests {

    // MARK: - pending_payment / payment_failed rejection

    @Test func joinDelegateForPendingPaymentGameShowsAlert() async {
        var game = Game.mock
        game.status = .pendingPayment

        // HomeFeature's destination must be set to `.joinFlow` for the
        // presented action to be processed by the reducer (TCA drops
        // presented actions for a nil destination).
        var state = HomeFeature.State()
        state.destination = .joinFlow(JoinFlowFeature.State())

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.destination(.presented(.joinFlow(.delegate(.joinGame(game, hunterName: "Tester"))))))
        if case .alert = store.state.destination {
            // ok — pin that an alert surfaces.
        } else {
            Issue.record("Expected alert destination for pending_payment game join")
        }
    }

    @Test func joinDelegateForPaymentFailedGameShowsAlert() async {
        var game = Game.mock
        game.status = .paymentFailed

        var state = HomeFeature.State()
        state.destination = .joinFlow(JoinFlowFeature.State())

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.destination(.presented(.joinFlow(.delegate(.joinGame(game, hunterName: "Tester"))))))
        if case .alert = store.state.destination {
            // ok
        } else {
            Issue.record("Expected alert destination for payment_failed game join")
        }
    }

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

    // MARK: - hunterRegistrationVerified

    @Test func hunterRegistrationVerifiedFalseClearsPendingBanner() async {
        var game = Game.mock
        game.id = "game-verify"
        let initialPending = PendingRegistration(
            gameId: "game-verify",
            gameCode: game.gameCode,
            teamName: "The Foxes",
            startDate: game.startDate
        )
        var state = HomeFeature.State()
        state.$pendingRegistration.withLock { $0 = initialPending }

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.hunterRegistrationVerified(gameId: "game-verify", confirmed: false))
        #expect(store.state.pendingRegistration == nil)
        // Alert surfaces.
        if case .alert = store.state.destination {} else {
            Issue.record("Expected alert when verification failed")
        }
    }

    @Test func hunterRegistrationVerifiedFalseForOtherGameKeepsCurrentPending() async {
        let myPending = PendingRegistration(
            gameId: "game-current",
            gameCode: "AAA111",
            teamName: "Foxes",
            startDate: .now.addingTimeInterval(3600)
        )
        var state = HomeFeature.State()
        state.$pendingRegistration.withLock { $0 = myPending }

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        // Verification for a DIFFERENT game (race between two rapid
        // registrations) must not clear the current pending.
        await store.send(.hunterRegistrationVerified(gameId: "game-stale", confirmed: false))
        #expect(store.state.pendingRegistration?.gameId == "game-current")
    }

    @Test func hunterRegistrationVerifiedTrueIsNoop() async {
        let pending = PendingRegistration(
            gameId: "game-ok",
            gameCode: "BBB222",
            teamName: "Wolves",
            startDate: .now.addingTimeInterval(3600)
        )
        var state = HomeFeature.State()
        state.$pendingRegistration.withLock { $0 = pending }

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.hunterRegistrationVerified(gameId: "game-ok", confirmed: true))
        #expect(store.state.pendingRegistration?.gameId == "game-ok")
        #expect(store.state.destination == nil)
    }

    // MARK: - pendingRegistrationRejoinTapped status gating

    @Test func pendingRegistrationRejoinPendingPaymentRoutesToGameNotFound() async {
        var game = Game.mock
        game.id = "game-stuck"
        game.status = .pendingPayment
        let pending = PendingRegistration(
            gameId: "game-stuck",
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
        await store.receive(\.gameNotFound)
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
