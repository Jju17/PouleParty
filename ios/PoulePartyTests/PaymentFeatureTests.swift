//
//  PaymentFeatureTests.swift
//  PoulePartyTests
//
//  Covers the PaymentFeature reducer, focused on the orphan-cleanup path
//  that was silently missing: a creator who swipe-dismisses the Stripe
//  PaymentSheet used to leave a `pending_payment` game doc behind, which
//  then showed up in My Games as a ghost "Paiement" entry.
//
//  These tests pin:
//   - `.sheetCompleted(.canceled)` in creatorForfait deletes the orphan game
//   - `.sheetCompleted(.failed)` in creatorForfait deletes the orphan game
//   - hunterCaution cancels DON'T delete (the game doc belongs to the chicken)
//   - delete failures don't propagate (we use `try?`) so UX stays smooth
//
//  A regression here would re-introduce the bug Julien flagged on 2026-04-23.
//

import ComposableArchitecture
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct PaymentFeatureTests {

    // MARK: - Cancel path (creator Forfait)

    @Test func creatorCancelDeletesOrphanGameDoc() async {
        let deletedGameIds = LockIsolated<[String]>([])
        var state = PaymentFeature.State(context: .creatorForfait(gameConfig: .mock))
        state.paymentConfig = PaymentFeature.PaymentSheetConfig(
            clientSecret: "pi_secret",
            ephemeralKeySecret: "ek_secret",
            customerId: "cus_123",
            amountCents: 500,
            gameId: "game-abc"
        )
        state.isPresentingSheet = true

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { id in
                deletedGameIds.withValue { $0.append(id) }
            }
        }
        store.exhaustivity = .off

        await store.send(.sheetCompleted(.canceled)) {
            $0.isPresentingSheet = false
            $0.paymentConfig = nil
        }
        // Allow the fire-and-forget `.run` block to drain.
        await Task.yield()
        await Task.yield()
        #expect(deletedGameIds.value == ["game-abc"])
    }

    @Test func creatorFailedDeletesOrphanGameDoc() async {
        let deletedGameIds = LockIsolated<[String]>([])
        var state = PaymentFeature.State(context: .creatorForfait(gameConfig: .mock))
        state.paymentConfig = PaymentFeature.PaymentSheetConfig(
            clientSecret: "pi_secret",
            ephemeralKeySecret: "ek_secret",
            customerId: "cus_456",
            amountCents: 1000,
            gameId: "game-failed"
        )
        state.isPresentingSheet = true

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { id in
                deletedGameIds.withValue { $0.append(id) }
            }
        }
        store.exhaustivity = .off

        await store.send(.sheetCompleted(.failed(message: "Card declined")))
        await Task.yield()
        await Task.yield()
        #expect(deletedGameIds.value == ["game-failed"])
        #expect(store.state.completionError == "Card declined")
    }

    @Test func hunterCancelDoesNotDeleteGameDoc() async {
        // Hunter Caution cancel must NOT delete the game — the doc belongs to
        // the chicken creator, and the hunter's registration is created
        // server-side by the webhook (never reached when sheet is cancelled).
        let deletedGameIds = LockIsolated<[String]>([])
        var state = PaymentFeature.State(context: .hunterCaution(gameId: "chicken-game"))
        state.paymentConfig = PaymentFeature.PaymentSheetConfig(
            clientSecret: "pi_secret",
            ephemeralKeySecret: "ek_secret",
            customerId: "cus_789",
            amountCents: 2000,
            gameId: nil
        )
        state.isPresentingSheet = true

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { id in
                deletedGameIds.withValue { $0.append(id) }
            }
        }
        store.exhaustivity = .off

        await store.send(.sheetCompleted(.canceled)) {
            $0.isPresentingSheet = false
            $0.paymentConfig = nil
        }
        await Task.yield()
        await Task.yield()
        #expect(deletedGameIds.value.isEmpty)
    }

    @Test func hunterFailedDoesNotDeleteGameDoc() async {
        let deletedGameIds = LockIsolated<[String]>([])
        var state = PaymentFeature.State(context: .hunterCaution(gameId: "chicken-game"))
        state.paymentConfig = PaymentFeature.PaymentSheetConfig(
            clientSecret: "pi_secret",
            ephemeralKeySecret: "ek_secret",
            customerId: "cus_abc",
            amountCents: 2000,
            gameId: nil
        )
        state.isPresentingSheet = true

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { id in
                deletedGameIds.withValue { $0.append(id) }
            }
        }
        store.exhaustivity = .off

        await store.send(.sheetCompleted(.failed(message: "Oops")))
        await Task.yield()
        await Task.yield()
        #expect(deletedGameIds.value.isEmpty)
    }

    @Test func creatorCancelWithoutGameIdDoesNothing() async {
        // Defensive: if paymentConfig is somehow nil (e.g. cancel fires twice
        // and the second call has no gameId), don't crash and don't call delete.
        let deletedGameIds = LockIsolated<[String]>([])
        let state = PaymentFeature.State(context: .creatorForfait(gameConfig: .mock))

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { id in
                deletedGameIds.withValue { $0.append(id) }
            }
        }
        store.exhaustivity = .off

        await store.send(.sheetCompleted(.canceled))
        await Task.yield()
        await Task.yield()
        #expect(deletedGameIds.value.isEmpty)
    }

    @Test func creatorCancelSwallowsDeleteErrors() async {
        // A failed delete (offline, rules change) must not propagate: we use
        // `try?` so the UX stays smooth even if the cleanup misfires.
        struct TestError: Error {}
        var state = PaymentFeature.State(context: .creatorForfait(gameConfig: .mock))
        state.paymentConfig = PaymentFeature.PaymentSheetConfig(
            clientSecret: "pi_secret",
            ephemeralKeySecret: "ek_secret",
            customerId: "cus_err",
            amountCents: 500,
            gameId: "game-offline"
        )
        state.isPresentingSheet = true

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { _ in
                throw TestError()
            }
        }
        store.exhaustivity = .off

        // If the error propagated, TestStore would fail with an unhandled effect.
        await store.send(.sheetCompleted(.canceled))
        await Task.yield()
        await Task.yield()
    }

    // MARK: - Happy path stays untouched

    @Test func creatorCompletionDoesNotDeleteGameDoc() async {
        // The happy path: payment succeeded, game is now in `waiting`. The
        // creator should land on PaymentConfirmation, NOT have the doc wiped.
        let deletedGameIds = LockIsolated<[String]>([])
        var state = PaymentFeature.State(context: .creatorForfait(gameConfig: .mock))
        state.paymentConfig = PaymentFeature.PaymentSheetConfig(
            clientSecret: "pi_secret",
            ephemeralKeySecret: "ek_secret",
            customerId: "cus_ok",
            amountCents: 500,
            gameId: "game-paid"
        )
        state.isPresentingSheet = true

        let store = TestStore(initialState: state) {
            PaymentFeature()
        } withDependencies: {
            $0.apiClient.deleteConfig = { id in
                deletedGameIds.withValue { $0.append(id) }
            }
        }
        store.exhaustivity = .off

        await store.send(.sheetCompleted(.completed))
        await store.receive(\.delegate.creatorPaymentConfirmed)
        #expect(deletedGameIds.value.isEmpty)
        #expect(store.state.completedGameId == "game-paid")
    }
}
