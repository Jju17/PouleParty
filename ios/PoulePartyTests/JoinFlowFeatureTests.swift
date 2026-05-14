//
//  JoinFlowFeatureTests.swift
//  PoulePartyTests
//
//  Pins the defensive behaviour of the post-PP-90 Join flow:
//   - Self-join (hunter taps their own chicken code) is rejected
//   - Code normalization (uppercase, length, allowed chars) and dedup
//     (re-typing the same code doesn't re-query)
//   - `joinAsHunterTapped` is a silent no-op outside of `codeValidated`
//
//  PP-64: re-enabled after PP-90 retired `Game.registration` and the
//  registration-required gate. The reducer surface tested below is the
//  one in `Features/JoinFlow/JoinFlow.swift` as of 2026-05-14.

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
        ownGame.chickenId = myUid
        ownGame.status = .waiting

        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { myUid }
            $0.apiClient.findGameByCode = { _ in ownGame }
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
        game.chickenId = "other-creator"
        game.status = .waiting

        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.findGameByCode = { _ in game }
        }
        store.exhaustivity = .off

        await store.send(.codeChanged("ABC123"))
        await store.receive(\.codeValidationSucceeded)
    }

    // MARK: - Code normalization + dedup

    @Test func codeChangeUppercasesAndValidates() async {
        var game = Game.mock
        game.creatorId = "someone-else"
        game.chickenId = "someone-else"
        game.status = .waiting

        let seenCodes = LockIsolated<[String]>([])
        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.findGameByCode = { code in
                seenCodes.withValue { $0.append(code) }
                return game
            }
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
        game.chickenId = "someone-else"
        game.status = .waiting

        let callCount = LockIsolated(0)
        let store = TestStore(initialState: JoinFlowFeature.State()) {
            JoinFlowFeature()
        } withDependencies: {
            $0.userClient.currentUserId = { "user-xyz" }
            $0.apiClient.findGameByCode = { _ in
                callCount.withValue { $0 += 1 }
                return game
            }
        }
        store.exhaustivity = .off

        await store.send(.codeChanged("ABC123"))
        await store.receive(\.codeValidationSucceeded)
        // Re-firing the same code from the text field must not re-query.
        await store.send(.codeChanged("ABC123"))
        #expect(callCount.value == 1)
    }

    // MARK: - joinAsHunterTapped rejects non-final states

    @Test func joinAsHunterTappedIgnoredWhenStillValidating() async {
        var state = JoinFlowFeature.State()
        state.step = .validating
        let store = TestStore(initialState: state) {
            JoinFlowFeature()
        }
        await store.send(.joinAsHunterTapped)
        // No action received — silent no-op.
    }
}
