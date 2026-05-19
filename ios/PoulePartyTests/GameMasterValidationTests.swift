import ComposableArchitecture
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct GameMasterValidationTests {

    @Test func validateChallengeSubmissionForwardsPayload() async throws {
        let captured = LockIsolated<[(String, String, Bool)]>([])

        try await withDependencies {
            $0.apiClient.validateChallengeSubmission = { gameId, submissionId, accept in
                captured.withValue { $0.append((gameId, submissionId, accept)) }
            }
        } operation: {
            @Dependency(\.apiClient) var apiClient
            try await apiClient.validateChallengeSubmission("game-1", "sub-1", true)
            try await apiClient.validateChallengeSubmission("game-1", "sub-2", false)
        }

        #expect(captured.value.count == 2)
        #expect(captured.value[0].0 == "game-1")
        #expect(captured.value[0].1 == "sub-1")
        #expect(captured.value[0].2 == true)
        #expect(captured.value[1].2 == false)
    }

    @Test func oneShotValidationAddsChallengeIdAndAccumulatesPoints() {
        var completion = ChallengeCompletion(
            hunterId: "h",
            validatedChallengeIds: [],
            repeatableCounts: [:],
            totalPoints: 0,
            teamName: "Team"
        )

        completion.validatedChallengeIds.append("ch-1")
        completion.totalPoints += 25
        completion.validatedChallengeIds.append("ch-2")
        completion.totalPoints += 10

        #expect(completion.validatedChallengeIds == ["ch-1", "ch-2"])
        #expect(completion.totalPoints == 35)
        #expect(completion.repeatableCounts.isEmpty)
    }

    @Test func repeatableValidationIncrementsCountAndAccumulatesPoints() {
        var completion = ChallengeCompletion(
            hunterId: "h",
            validatedChallengeIds: [],
            repeatableCounts: [:],
            totalPoints: 0,
            teamName: "Team"
        )

        completion.repeatableCounts["bar-1", default: 0] += 1
        completion.totalPoints += 5
        completion.repeatableCounts["bar-1", default: 0] += 1
        completion.totalPoints += 5
        completion.repeatableCounts["bar-1", default: 0] += 1
        completion.totalPoints += 5

        #expect(completion.repeatableCounts["bar-1"] == 3)
        #expect(completion.totalPoints == 15)
        #expect(completion.validatedChallengeIds.isEmpty)
    }

    @Test func oneShotIdempotentOnDuplicateChallengeId() {
        var completion = ChallengeCompletion(
            hunterId: "h",
            validatedChallengeIds: ["ch-1"],
            repeatableCounts: [:],
            totalPoints: 25,
            teamName: "Team"
        )

        if !completion.validatedChallengeIds.contains("ch-1") {
            completion.validatedChallengeIds.append("ch-1")
            completion.totalPoints += 25
        }

        #expect(completion.validatedChallengeIds == ["ch-1"])
        #expect(completion.totalPoints == 25)
    }
}
