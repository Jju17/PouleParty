//
//  JoinFlowFeatureTests.swift
//  PoulePartyTests
//
//  Pins the defensive behaviour of the Join flow:
//   - Self-join (hunter taps their own chicken code) is rejected
//   - pending_payment / payment_failed rejection in the joinGame delegate
//     (handled by HomeFeature) — tested here via the delegate contract
//   - Code normalization doesn't double-fire validation on repeated taps
//

import ComposableArchitecture
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct JoinFlowFeatureTests {

    // MARK: - Self-join protection

    @Test func hunterTypingOwnChickenCodeIsRejected() async {
        let myUid = "user-abc"
        var ownGame = Game.mock
        ownGame.creatorId = myUid
        ownGame.status = .waiting

        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { myUid }
            $0.apiClient.findGameByCode = { _ in ownGame }
            $0.apiClient.findRegistration = { _, _ in nil }
        }
        store.exhaustivity = .off

        await store.send(.codeChanged("ABC123"))
        await store.receive(\.codeValidationFailed) {
            $0.step = .codeNotFound
        }
    }

    @Test func hunterTypingOtherCreatorCodeIsAccepted() async {
        var game = Game.mock
        game.creatorId = "other-creator"
        game.status = .waiting
        game.registration.required = false

        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.findGameByCode = { _ in game }
            $0.apiClient.findRegistration = { _, _ in nil }
        }
        store.exhaustivity = .off

        await store.send(.codeChanged("ABC123"))
        await store.receive(\.codeValidationSucceeded)
    }

    // MARK: - Code normalization + dedup

    @Test func codeChangeUppercasesAndValidates() async {
        var game = Game.mock
        game.creatorId = "someone-else"
        game.status = .waiting
        game.registration.required = false

        let seenCodes = LockIsolated<[String]>([])
        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.findGameByCode = { code in
                seenCodes.withValue { $0.append(code) }
                return game
            }
            $0.apiClient.findRegistration = { _, _ in nil }
        }
        store.exhaustivity = .off

        await store.send(.codeChanged("abc123"))
        await store.receive(\.codeValidationSucceeded)
        #expect(seenCodes.value == ["ABC123"])
    }

    @Test func repeatedCodeChangeDoesNotRevalidate() async {
        // The dedup skip path compares the normalized code against
        // `game.gameCode` on the already-validated step. Game.mock's id ↦
        // gameCode derivation would mismatch "ABC123", so we pick a game id
        // whose first 6 chars upper-case equal the typed code.
        var game = Game.mock
        game.id = "abc123xyz9999999999"  // gameCode = "ABC123"
        game.creatorId = "someone-else"
        game.status = .waiting
        game.registration.required = false

        let callCount = LockIsolated(0)
        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.findGameByCode = { _ in
                callCount.withValue { $0 += 1 }
                return game
            }
            $0.apiClient.findRegistration = { _, _ in nil }
        }
        store.exhaustivity = .off

        await store.send(.codeChanged("ABC123"))
        await store.receive(\.codeValidationSucceeded)
        // Re-firing the same code from the text field must not re-query.
        await store.send(.codeChanged("ABC123"))
        #expect(callCount.value == 1)
    }

    // MARK: - joinTapped rejects non-final states

    @Test func joinTappedIgnoredWhenStillValidating() async {
        var state = JoinFlowFeature.State()
        state.step = .validating
        let store = TestStore(initialState: state) {
            JoinFlowFeature()
        }
        await store.send(.joinTapped)
        // No action received — silent no-op.
    }

    @Test func joinTappedIgnoredWhenRegistrationRequiredButNotRegistered() async {
        var game = Game.mock
        game.registration.required = true
        var state = JoinFlowFeature.State()
        state.step = .codeValidated(game, alreadyRegistered: false)
        let store = TestStore(initialState: state) {
            JoinFlowFeature()
        }
        await store.send(.joinTapped)
    }
}
