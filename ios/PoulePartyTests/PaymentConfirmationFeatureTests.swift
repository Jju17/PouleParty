//
//  PaymentConfirmationFeatureTests.swift
//  PoulePartyTests
//
//  Exhaustive coverage for the post-payment confirmation reducer. Focus is
//  explicitly on edge cases that could silently regress the UX around the
//  webhook / Firestore stream:
//
//  - `pendingPayment` → `waiting` status flip mid-screen
//  - `paymentFailed` delivered by webhook rejection
//  - Creator cancelling their own game while the other player is still on the
//    confirmation screen (status goes straight to `.done`)
//  - Start time in the past (user lingered, device clock skew)
//  - Multiple consecutive `gameUpdated` (last one wins)
//  - `backToHomeTapped` always routes back via `.delegate(.done)` regardless of
//    current game status
//
//  These are the kinds of things that make customer-facing UX silently wrong
//  when the reducer or the stream plumbing drifts. Covered here so regression
//  flips a test bulb instead of a store review.
//

import ComposableArchitecture
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct PaymentConfirmationFeatureTests {

    // MARK: - Initial state invariants

    @Test func initialStateHoldsGameAndKind() {
        let game = Game.mock
        let state = PaymentConfirmationFeature.State(game: game, kind: .creatorForfait)
        #expect(state.game.id == game.id)
        #expect(state.kind == .creatorForfait)
    }

    @Test func initialStateDefaultsNowToCurrentDate() {
        let before = Date()
        let state = PaymentConfirmationFeature.State(game: .mock, kind: .hunterCaution)
        let after = Date()
        #expect(state.now >= before)
        #expect(state.now <= after)
    }

    // MARK: - tick

    @Test func tickUpdatesNow() async {
        let fixed = Date(timeIntervalSince1970: 1_700_000_000)
        let state = PaymentConfirmationFeature.State(
            game: .mock,
            kind: .creatorForfait,
            now: Date(timeIntervalSince1970: 1_000_000_000)
        )
        let store = TestStore(initialState: state) {
            PaymentConfirmationFeature()
        }
        await store.send(.tick(fixed)) {
            $0.now = fixed
        }
    }

    @Test func multipleTicksEachUpdateNow() async {
        let t1 = Date(timeIntervalSince1970: 1_000)
        let t2 = Date(timeIntervalSince1970: 2_000)
        let t3 = Date(timeIntervalSince1970: 3_000)
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait, now: t1)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.tick(t2)) { $0.now = t2 }
        await store.send(.tick(t3)) { $0.now = t3 }
    }

    @Test func tickAcceptingDistantPastDoesNotCrash() async {
        // Device clock skew / mocked test clock could send a date from 1970.
        // Reducer just assigns it — no invariant here to enforce.
        let ancient = Date(timeIntervalSince1970: 0)
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .hunterCaution)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.tick(ancient)) { $0.now = ancient }
    }

    @Test func tickAcceptingDistantFutureDoesNotCrash() async {
        let future = Date(timeIntervalSinceNow: 365 * 24 * 3600 * 100) // ~2125
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.tick(future)) { $0.now = future }
    }

    // MARK: - gameUpdated

    @Test func gameUpdatedReplacesEntireGame() async {
        var updated = Game.mock
        updated.name = "Replaced"
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.gameUpdated(updated)) {
            $0.game = updated
        }
    }

    @Test func pendingPaymentToWaitingTransitionReflectedInState() async {
        // Webhook flips `pending_payment` → `waiting` a couple of seconds after
        // the client receives the PaymentSheet .completed. The confirmation
        // screen must reflect that transition so the status dot flips from
        // amber to orange and the "Validating payment…" label disappears.
        var pending = Game.mock
        pending.status = .pendingPayment
        var ready = pending
        ready.status = .waiting

        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: pending, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.gameUpdated(ready)) {
            $0.game = ready
            #expect($0.game.status == .waiting)
        }
    }

    @Test func paymentFailedTransitionReflectedInState() async {
        // If the webhook sees `payment_intent.payment_failed` (card declined at
        // 3-D Secure, Bancontact timeout…) it flips the game to `paymentFailed`.
        // Confirmation screen must show the failure status.
        var pending = Game.mock
        pending.status = .pendingPayment
        var failed = pending
        failed.status = .paymentFailed

        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: pending, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.gameUpdated(failed)) {
            $0.game = failed
        }
    }

    @Test func gameCancelledByCreatorMidViewIsReflected() async {
        // Race condition: hunter is on the confirmation screen; creator opens
        // the chicken side and cancels the whole game. Firestore flips status
        // to `.done` (games cancelled before start end up done with no winners).
        var waiting = Game.mock
        waiting.status = .waiting
        var cancelled = waiting
        cancelled.status = .done

        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: waiting, kind: .hunterCaution)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.gameUpdated(cancelled)) {
            $0.game = cancelled
        }
    }

    @Test func repeatedGameUpdatedLastOneWins() async {
        var a = Game.mock; a.name = "A"
        var b = Game.mock; b.name = "B"
        var c = Game.mock; c.name = "C"

        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.gameUpdated(a)) { $0.game = a }
        await store.send(.gameUpdated(b)) { $0.game = b }
        await store.send(.gameUpdated(c)) { $0.game = c }
    }

    @Test func gameUpdatedWithDifferentIdStillApplies() async {
        // Firestore stream should never deliver a foreign game ID, but the
        // reducer doesn't guard against it — document the behaviour explicitly
        // so if we ever add a guard the test flips.
        let foreign = Game(id: "different-id")
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.gameUpdated(foreign)) {
            $0.game = foreign
        }
    }

    // MARK: - backToHomeTapped / delegate

    @Test func backToHomeTappedEmitsDoneDelegate() async {
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.backToHomeTapped)
        await store.receive(\.delegate.done)
    }

    @Test func backToHomeTappedWorksRegardlessOfStatus() async {
        // The exit button must work even if the game is still in `pendingPayment`
        // — user might grow impatient waiting for the webhook flip.
        var pending = Game.mock
        pending.status = .pendingPayment
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: pending, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.backToHomeTapped)
        await store.receive(\.delegate.done)
    }

    @Test func backToHomeTappedWorksAfterPaymentFailed() async {
        // If the webhook rejects the charge, the user still needs a way out.
        var failed = Game.mock
        failed.status = .paymentFailed
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: failed, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.backToHomeTapped)
        await store.receive(\.delegate.done)
    }

    @Test func delegateActionIsNoOp() async {
        // The reducer receives its own `.delegate(.done)` after `backToHomeTapped`
        // and must not recurse or mutate state.
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: .mock, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        await store.send(.delegate(.done))
    }

    // MARK: - Kind semantics

    @Test func kindIsImmutableAfterInit() {
        // `let kind` — caller decides the kind at creation time and it can't
        // drift while the screen is presented. Protects against "I was a
        // creator but somehow the screen says 'Tu es inscrit'" bugs.
        let state = PaymentConfirmationFeature.State(game: .mock, kind: .hunterCaution)
        #expect(state.kind == .hunterCaution)
    }

    @Test func bothKindsAreEquatable() {
        #expect(PaymentConfirmationFeature.Kind.creatorForfait == .creatorForfait)
        #expect(PaymentConfirmationFeature.Kind.hunterCaution == .hunterCaution)
        #expect(PaymentConfirmationFeature.Kind.creatorForfait != .hunterCaution)
    }

    // MARK: - Start-time edge cases (countdown input)

    @Test func startDateInPastDoesNotPanicReducer() async {
        // View code clamps to zero; reducer just carries the game as-is.
        var old = Game.mock
        old.timing.start = Timestamp(date: Date(timeIntervalSinceNow: -3_600))
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: old, kind: .creatorForfait)
        ) {
            PaymentConfirmationFeature()
        }
        // Sanity: sending another update after a past start still works.
        var stillOld = old
        stillOld.name = "Past game, now renamed"
        await store.send(.gameUpdated(stillOld)) { $0.game = stillOld }
    }

    @Test func veryDistantStartDateDoesNotPanicReducer() async {
        var future = Game.mock
        future.timing.start = Timestamp(date: Date(timeIntervalSinceNow: 365 * 24 * 3600 * 10))
        let store = TestStore(
            initialState: PaymentConfirmationFeature.State(game: future, kind: .hunterCaution)
        ) {
            PaymentConfirmationFeature()
        }
        // Not crashing on a 10-year countdown is the assertion.
        let t = Date(timeIntervalSince1970: 1_700_000_000)
        await store.send(.tick(t)) { $0.now = t }
    }
}
