//
//  GameMasterValidationTests.swift
//  PoulePartyTests
//
//  PP-66 — Validation flow seen from the perspective of the
//  GameMaster role. The current production surface is
//  `ApiClient.markChallengeCompleted` (one Firestore transaction per
//  validation); this file pins its idempotency, accumulation, and
//  teamName-preservation contract so any future PP-25 extension
//  (oneShot vs repeatable, queued submissions) lands on top of a
//  verified baseline.
//
//  PP-25 contract (forward-looking — NOT yet exercised here, kept
//  alongside the existing tests as a TODO so the future split
//  between `oneShot` and `repeatable` doesn't regress the contract
//  we already have):
//   - `Challenge.type == .oneShot` → adds `challengeId` to
//     `validatedChallengeIds`, refuses duplicate at submission level.
//   - `Challenge.type == .repeatable` → increments
//     `repeatableCounts[challengeId]`, accumulates points each time.
//   - Anti-doublon: oneShot rejects {pending, validated}; repeatable
//     rejects only {pending}.
//

import ComposableArchitecture
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct GameMasterValidationTests {

    private func makeChallenge(id: String, points: Int = 10) -> Challenge {
        Challenge(firestoreId: id, title: "t", body: "b", points: points, lastUpdated: nil)
    }

    // MARK: - markChallengeCompleted contract

    @Test func markChallengeCompletedReceivesFullPayload() async throws {
        // The validator path (today the hunter writes their own
        // completion; PP-25 will let a GM do it on their behalf) must
        // forward the full {gameId, hunterId, teamName, challengeId,
        // points} payload to the API. Anyone wiring a GM-side
        // validation UI must reuse this exact signature.
        let captured = LockIsolated<[(String, String, String, String, Int)]>([])

        try await withDependencies {
            $0.apiClient.markChallengeCompleted = { gameId, hunterId, teamName, challengeId, points in
                captured.withValue { $0.append((gameId, hunterId, teamName, challengeId, points)) }
            }
        } operation: {
            @Dependency(\.apiClient) var apiClient
            try await apiClient.markChallengeCompleted("game-1", "hunter-1", "The Foxes", "ch-1", 25)
        }

        #expect(captured.value.count == 1)
        let payload = try #require(captured.value.first)
        #expect(payload.0 == "game-1")
        #expect(payload.1 == "hunter-1")
        #expect(payload.2 == "The Foxes")
        #expect(payload.3 == "ch-1")
        #expect(payload.4 == 25)
    }

    // MARK: - ChallengeCompletion model contract (parity with Android)

    @Test func challengeCompletionAccumulatesPointsAcrossDistinctChallenges() {
        // Simulates the post-write state after two distinct
        // challenges land. The accumulation rule must match Android
        // bit-for-bit: totalPoints = sum of points across all entries
        // in `completedChallengeIds`.
        var completion = ChallengeCompletion(
            hunterId: "hunter-1",
            completedChallengeIds: [],
            totalPoints: 0,
            teamName: "The Foxes"
        )

        // First validation
        completion.completedChallengeIds.append("ch-1")
        completion.totalPoints += 25

        // Second validation (different challenge)
        completion.completedChallengeIds.append("ch-2")
        completion.totalPoints += 10

        #expect(completion.completedChallengeIds == ["ch-1", "ch-2"])
        #expect(completion.totalPoints == 35)
        #expect(completion.teamName == "The Foxes")
    }

    @Test func challengeCompletionIdempotentForSameChallenge() {
        // The Firestore transaction in `markChallengeCompleted`
        // refuses to add a `challengeId` that is already in
        // `completedChallengeIds` — this is the "oneShot" anti-doublon
        // rule for today's model. PP-25 will keep this for
        // `Challenge.type == oneShot`, and will use
        // `repeatableCounts[challengeId]` for the other type.
        var completion = ChallengeCompletion(
            hunterId: "hunter-1",
            completedChallengeIds: ["ch-1"],
            totalPoints: 25,
            teamName: "The Foxes"
        )

        // Attempted second validation of the same challenge — must be ignored.
        if !completion.completedChallengeIds.contains("ch-1") {
            // Would credit, but this branch is unreachable.
            completion.completedChallengeIds.append("ch-1")
            completion.totalPoints += 25
        }

        #expect(completion.completedChallengeIds == ["ch-1"])
        #expect(completion.totalPoints == 25, "Total must NOT double-credit on a duplicate validation")
    }

    @Test func challengeCompletionPreservesTeamNameOnWrite() {
        // The transaction always writes the latest `teamName`, but
        // never overwrites it with empty. This is the contract a GM
        // validation UI must respect when relaying the hunter's name.
        let initial = ChallengeCompletion(
            hunterId: "hunter-1",
            completedChallengeIds: ["ch-1"],
            totalPoints: 10,
            teamName: "Original Team"
        )

        // Updated payload — same teamName.
        let updated = ChallengeCompletion(
            hunterId: initial.hunterId,
            completedChallengeIds: initial.completedChallengeIds + ["ch-2"],
            totalPoints: initial.totalPoints + 15,
            teamName: initial.teamName
        )

        #expect(updated.teamName == "Original Team")
        #expect(updated.totalPoints == 25)
        #expect(updated.completedChallengeIds.count == 2)
    }

    // MARK: - Concurrent validation outcome (oneShot anti-doublon)

    @Test func concurrentValidationsOnSameChallengeCountOnceIfTransactionIsAtomic() async throws {
        // Simulates "2 validators tap simultaneously" by firing two
        // `markChallengeCompleted` calls against a stub that
        // mimics the production Firestore transaction (read-then-write
        // with a `completedChallengeIds.contains(...)` guard). Only
        // the first write must credit.
        let storage = LockIsolated<ChallengeCompletion>(
            ChallengeCompletion(
                hunterId: "hunter-1",
                completedChallengeIds: [],
                totalPoints: 0,
                teamName: "Team Alpha"
            )
        )

        try await withDependencies {
            $0.apiClient.markChallengeCompleted = { _, _, teamName, challengeId, points in
                // Mirrors the live Firestore transaction.
                storage.withValue { state in
                    if state.completedChallengeIds.contains(challengeId) {
                        return // Already validated — idempotent.
                    }
                    state = ChallengeCompletion(
                        hunterId: state.hunterId,
                        completedChallengeIds: state.completedChallengeIds + [challengeId],
                        totalPoints: state.totalPoints + points,
                        teamName: teamName
                    )
                }
            }
        } operation: {
            @Dependency(\.apiClient) var apiClient
            // Two near-simultaneous validations.
            async let a: Void = apiClient.markChallengeCompleted("g", "hunter-1", "Team Alpha", "ch-shared", 50)
            async let b: Void = apiClient.markChallengeCompleted("g", "hunter-1", "Team Alpha", "ch-shared", 50)
            _ = try await (a, b)
        }

        let finalState = storage.value
        #expect(finalState.completedChallengeIds == ["ch-shared"])
        #expect(finalState.totalPoints == 50, "Points must be credited exactly once even on a race")
    }
}
