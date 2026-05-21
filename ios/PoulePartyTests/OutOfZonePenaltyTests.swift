//
//  OutOfZonePenaltyTests.swift
//  PoulePartyTests
//
//  PP-37: parity tests for the PP-36 out-of-zone penalty (-1 point /
//  5 s). Strict mirror of the Android `OutOfZonePenaltyTest` — same
//  scenarios in the same order, same expected numeric outputs, so a
//  one-platform drift fails on this side without the other.
//
//  Implementation notes:
//  - The penalty path lives inside `.internal(.timerTicked)` on
//    `HunterMapFeature`. The reducer overwrites `state.nowDate = .now`
//    at the top of the tick, so we control timing by seeding
//    `state.lastPenaltyAt` with a Date offset from `.now`. This avoids
//    needing a fake clock and keeps the tests fast.
//  - `state.userLocation` and `state.mapCircle` are left `nil` so the
//    zone-check block is skipped and `isOutsideZone` keeps the value we
//    seed.
//  - `apiClient.decrementTotalPoints` is mocked with a call counter so
//    we assert "fires exactly N times" for each scenario.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

/// A Game.mock with start dates moved into the past so
/// `state.hasGameStarted == true` once the reducer sets
/// `state.nowDate = .now`.
private var startedGameMock: Game {
    var game = Game.mock
    game.startDate = .now.addingTimeInterval(-600)   // started 10 min ago
    game.endDate = .now.addingTimeInterval(3000)      // ends in 50 min
    return game
}

/// Builds a hunter-state primed for the penalty path:
/// - hunter has joined (`hunterId` non-empty)
/// - game already started (start in the past)
/// - hunter currently outside zone
/// - `userLocation` / `mapCircle` nil so the inline zone-check
///   doesn't reset `isOutsideZone`.
private func penaltyReadyState(
    lastPenaltyAt: Date? = nil,
    isGameOver: Bool = false,
    isOutsideZone: Bool = true,
    hunterId: String = "hunter-1"
) -> HunterMapFeature.State {
    var state = HunterMapFeature.State(game: startedGameMock)
    state.hunterId = hunterId
    state.isOutsideZone = isOutsideZone
    state.isGameOver = isGameOver
    state.lastPenaltyAt = lastPenaltyAt
    state.radius = 500
    return state
}

@MainActor
struct OutOfZonePenaltyTests {

    // MARK: - Scenario 1: 12s out of zone → -2 points

    /// Two complete 5 s windows fire exactly two penalty writes. TCA's
    /// `TestStore.state` is read-only between actions, so we model the
    /// "12 s dwell" with two independent stores — one per 5 s window —
    /// sharing the same call counter. Together they prove the
    /// cumulative property: each elapsed window pays one point. The
    /// "within-the-same-window" half of the property is covered by
    /// scenario 9 (`twoTicksWithinFiveSecondsFireOnlyOnePenalty`).
    @Test func twelveSecondsOutOfZoneFiresTwoPenalties() async {
        let calls = LockIsolated(0)
        // First 5 s window: lastPenaltyAt rewound 5 s → fires.
        let firstState = penaltyReadyState(lastPenaltyAt: .now.addingTimeInterval(-5))
        let firstStore = TestStore(initialState: firstState) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        firstStore.exhaustivity = .off
        await firstStore.send(.internal(.timerTicked))
        await firstStore.finish()

        // Second 5 s window: lastPenaltyAt rewound 5 s → fires again.
        let secondState = penaltyReadyState(lastPenaltyAt: .now.addingTimeInterval(-5))
        let secondStore = TestStore(initialState: secondState) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        secondStore.exhaustivity = .off
        await secondStore.send(.internal(.timerTicked))
        await secondStore.finish()

        #expect(calls.value == 2)
    }

    // MARK: - Scenario 2: 4s out of zone → 0 points

    /// Less than a full 5 s window: no penalty fires.
    @Test func fourSecondsOutOfZoneFiresNoPenalty() async {
        let calls = LockIsolated(0)
        let state = penaltyReadyState(lastPenaltyAt: .now.addingTimeInterval(-4))
        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 0)
    }

    // MARK: - Scenario 3: re-enters just before a tick → no penalty

    /// Re-entry resets `lastPenaltyAt` to nil. The next tick checks
    /// `isOutsideZone` again — if the hunter is back inside, no
    /// penalty fires that tick.
    @Test func reEntersJustBeforeTickFiresNoPenalty() async {
        let calls = LockIsolated(0)
        // Hunter was outside, almost due, but came back in: state
        // before tick has `isOutsideZone = false` AND a stale
        // `lastPenaltyAt`. The reducer's `else` branch must reset
        // `lastPenaltyAt` to nil and NOT fire a penalty.
        var state = penaltyReadyState(
            lastPenaltyAt: .now.addingTimeInterval(-4),
            isOutsideZone: false
        )
        // Make sure we don't accidentally enable the zone-check path
        // and flip `isOutsideZone` back to true.
        state.userLocation = nil
        state.mapCircle = nil

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 0)
        #expect(store.state.lastPenaltyAt == nil, "Re-entry must clear lastPenaltyAt")
    }

    // MARK: - Scenario 4: exits just after a tick → first penalty 5s later

    /// First out-of-zone tick MUST NOT fire immediately — it just
    /// starts the 5 s window. Mirrors the Android
    /// `firstTickOutOfZoneStartsWindowDoesNotFire` test.
    @Test func firstTickOutOfZoneStartsWindowAndDoesNotFire() async {
        let calls = LockIsolated(0)
        let state = penaltyReadyState(lastPenaltyAt: nil)
        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 0)
        #expect(store.state.lastPenaltyAt != nil, "First tick must seed lastPenaltyAt")
    }

    // MARK: - Scenario 5: pre-game (chicken hasn't started) → no penalty

    /// Hunter map shouldn't even be reachable before
    /// `chickenStartDate`, but the reducer is robust about it: a tick
    /// with `now < game.startDate` must not fire any penalty.
    @Test func preGameFiresNoPenalty() async {
        let calls = LockIsolated(0)
        // Pull the game start into the future so `hasGameStarted == false`.
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(600)  // starts in 10 min
        game.endDate = .now.addingTimeInterval(4200)
        var state = HunterMapFeature.State(game: game)
        state.hunterId = "hunter-1"
        state.isOutsideZone = true
        state.lastPenaltyAt = .now.addingTimeInterval(-10) // ages
        state.radius = 500

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 0)
    }

    // MARK: - Scenario 6: head-start window → no penalty

    /// During the head-start window (chicken running, hunters locked
    /// in place) the hunter cannot legally be hunting yet — the
    /// reducer's `hasGameStarted` gate (which checks
    /// `nowDate >= hunterStartDate`) must short-circuit the penalty
    /// path.
    @Test func headStartFiresNoPenalty() async {
        let calls = LockIsolated(0)
        // Chicken started, hunters haven't.
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-60)  // 1 min ago
        game.endDate = .now.addingTimeInterval(3600)
        game.timing.headStartMinutes = 10              // hunters wait 10 min
        var state = HunterMapFeature.State(game: game)
        state.hunterId = "hunter-1"
        state.isOutsideZone = true
        state.lastPenaltyAt = .now.addingTimeInterval(-10) // ages
        state.radius = 500

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 0)
    }

    // MARK: - Scenario 7: game over → no penalty

    /// Once the game ends (timeout, zone collapse, all hunters found)
    /// the penalty stops. Mirrors the Android
    /// `isGameOverFiresNoPenalty` test.
    @Test func isGameOverFiresNoPenalty() async {
        let calls = LockIsolated(0)
        let state = penaltyReadyState(
            lastPenaltyAt: .now.addingTimeInterval(-10),
            isGameOver: true
        )
        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 0)
    }

    // MARK: - Scenario 9: anti double-count within a 5s window

    /// Two ticks fired within the same 5 s window must fire exactly
    /// one penalty — the `lastPenaltyAt` guard is the anti
    /// double-count. Models two ticks where the first fires
    /// (lastPenaltyAt was 5 s old, gets bumped to now), and the
    /// second fires immediately after (lastPenaltyAt is now ~0 s
    /// old, no penalty).
    @Test func twoTicksWithinFiveSecondsFireOnlyOnePenalty() async {
        let calls = LockIsolated(0)
        let state = penaltyReadyState(lastPenaltyAt: .now.addingTimeInterval(-5))
        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.decrementTotalPoints = { _, _ in
                calls.withValue { $0 += 1 }
            }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked))
        // Don't rewind `lastPenaltyAt`: the reducer just bumped it to
        // `.now` on the previous tick. A second tick fired
        // immediately must NOT fire another penalty.
        await store.send(.internal(.timerTicked))

        await store.finish()
        #expect(calls.value == 1)
    }
}
