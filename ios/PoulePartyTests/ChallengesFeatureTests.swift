//
//  ChallengesFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
@Suite(.serialized)
struct ChallengesFeatureTests {

    private func makeChallenge(id: String, title: String = "t", body: String = "b", points: Int = 10) -> Challenge {
        Challenge(
            firestoreId: id,
            points: points,
            lastUpdated: nil,
            titleByLocale: ["fr": title],
            bodyByLocale: ["fr": body]
        )
    }

    private func makeCompletion(hunterId: String, ids: [String] = [], total: Int = 0, teamName: String = "") -> ChallengeCompletion {
        ChallengeCompletion(hunterId: hunterId, validatedChallengeIds: ids, repeatableCounts: [:], totalPoints: total, teamName: teamName)
    }

    // MARK: - Streaming

    @Test func challengesUpdatedPopulatesState() async {
        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me"]
            )
        ) {
            ChallengesFeature()
        }

        let challenges = [makeChallenge(id: "c1"), makeChallenge(id: "c2")]
        await store.send(.internal(.challengesUpdated(challenges))) {
            $0.challenges = challenges
        }
    }

    @Test func completionsUpdatedPopulatesState() async {
        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me", "h2"]
            )
        ) {
            ChallengesFeature()
        }

        let completions = [makeCompletion(hunterId: "me", ids: ["c1"], total: 10, teamName: "Team Me")]
        await store.send(.internal(.completionsUpdated(completions))) {
            $0.completions = completions
        }
    }

    @Test func registrationsUpdatedPopulatesState() async {
        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me"]
            )
        ) {
            ChallengesFeature()
        }

        await store.send(.internal(.registrationsUpdated([
            Registration(userId: "h2", teamName: "Bob"),
            Registration(userId: "h3", teamName: ""),  // empty teamName filtered out
        ]))) {
            $0.registrations = ["h2": "Bob"]
        }
    }

    // MARK: - Tab switching

    @Test func tabChangedUpdatesSelectedTab() async {
        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me"]
            )
        ) {
            ChallengesFeature()
        }

        await store.send(.view(.tabChanged(.leaderboard))) {
            $0.selectedTab = .leaderboard
        }
        await store.send(.view(.tabChanged(.challenges))) {
            $0.selectedTab = .challenges
        }
    }

    // MARK: - Validation flow (2-tap)

    @Test func markAsDoneMovesChallengeToPendingLocal() async {
        let challenge = makeChallenge(id: "c1", title: "Run", body: "Run fast", points: 50)
        let suite = UserDefaults(suiteName: "test-\(UUID().uuidString)")!
        PendingChallengeStore.inject(suite)
        defer { PendingChallengeStore.resetForTesting() }

        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me"],
                challenges: [challenge]
            )
        ) {
            ChallengesFeature()
        }

        await store.send(.view(.markAsDoneTapped(challenge))) {
            $0.pendingLocalIds = ["c1"]
        }
        #expect(PendingChallengeStore.ids(forGame: "g") == ["c1"])
    }

    @Test func markAsDoneForAlreadyCompletedChallengeIsNoop() async {
        let challenge = makeChallenge(id: "c1")
        let completion = makeCompletion(hunterId: "me", ids: ["c1"], total: 10, teamName: "Me")
        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me"],
                challenges: [challenge],
                completions: [completion]
            )
        ) {
            ChallengesFeature()
        }

        await store.send(.view(.markAsDoneTapped(challenge)))
    }

    @Test func submitForValidationOpensPhotoPicker() async {
        let challenge = makeChallenge(id: "c1", points: 25)
        let suite = UserDefaults(suiteName: "test-\(UUID().uuidString)")!
        PendingChallengeStore.inject(suite)
        defer { PendingChallengeStore.resetForTesting() }
        PendingChallengeStore.add("c1", forGame: "g1")

        var state = ChallengesFeature.State(
            gameId: "g1",
            hunterId: "me",
            hunterIds: ["me"],
            myTeamName: "Team Me",
            challenges: [challenge]
        )
        state.pendingLocalIds = ["c1"]

        let store = TestStore(initialState: state) {
            ChallengesFeature()
        }

        await store.send(.view(.submitForValidationTapped(challenge))) {
            $0.photoTarget = challenge
        }
    }

    @Test func photoPickedInvokesSubmitChallenge() async {
        let challenge = makeChallenge(id: "c1", points: 25)
        let suite = UserDefaults(suiteName: "test-\(UUID().uuidString)")!
        PendingChallengeStore.inject(suite)
        defer { PendingChallengeStore.resetForTesting() }
        PendingChallengeStore.add("c1", forGame: "g1")

        let gotSubmit = LockIsolated<(String, String, String, Challenge.ChallengeType, Int)?>(nil)

        var state = ChallengesFeature.State(
            gameId: "g1",
            hunterId: "me",
            hunterIds: ["me"],
            myTeamName: "Team Me",
            challenges: [challenge]
        )
        state.pendingLocalIds = ["c1"]
        state.photoTarget = challenge

        let store = TestStore(initialState: state) {
            ChallengesFeature()
        } withDependencies: {
            $0.apiClient.submitChallenge = { gameId, challengeId, hunterId, type, data in
                gotSubmit.setValue((gameId, challengeId, hunterId, type, data.count))
                return ChallengeSubmission(
                    firestoreId: "sub1",
                    challengeId: challengeId,
                    hunterId: hunterId,
                    type: type,
                    photoUrl: "https://example.com/x.jpg",
                    status: .pending
                )
            }
        }

        let bytes = Data(repeating: 0xFF, count: 16)
        await store.send(.view(.photoPicked(challengeId: "c1", data: bytes))) {
            $0.photoTarget = nil
            $0.submittingIds = ["c1"]
        }
        await store.receive(\.internal.submissionWriteSucceeded) {
            $0.submittingIds = []
            $0.pendingLocalIds = []
        }

        let captured = gotSubmit.value
        #expect(captured?.0 == "g1")
        #expect(captured?.1 == "c1")
        #expect(captured?.2 == "me")
        #expect(captured?.3 == .oneShot)
        #expect(captured?.4 == 16)
        #expect(PendingChallengeStore.ids(forGame: "g1").isEmpty)
    }

    // MARK: - Leaderboard sorting & current hunter highlight

    @Test func leaderboardSortsByPointsDescWithZeroPointsForMissingCompletions() async {
        let completions = [
            makeCompletion(hunterId: "h2", ids: ["c1"], total: 30, teamName: "Team B"),
            makeCompletion(hunterId: "h3", ids: ["c2"], total: 10, teamName: "Team C")
        ]
        let state = ChallengesFeature.State(
            gameId: "g",
            hunterId: "me",
            hunterIds: ["me", "h2", "h3", "h4"],
            challenges: [],
            completions: completions,
            registrations: ["me": "Me", "h4": "D"]
        )

        let entries = state.leaderboard
        #expect(entries.count == 4)
        #expect(entries[0].hunterId == "h2")
        #expect(entries[0].totalPoints == 30)
        #expect(entries[0].teamName == "Team B")
        #expect(entries[1].hunterId == "h3")
        #expect(entries[1].totalPoints == 10)
        // Hunters without completion docs fall back to the team name from
        // their registration doc, 0 pts, sorted alphabetically.
        #expect(entries[2].totalPoints == 0)
        #expect(entries[3].totalPoints == 0)
        let zeroPointIds = Set([entries[2].hunterId, entries[3].hunterId])
        #expect(zeroPointIds == Set(["me", "h4"]))
    }

    @Test func leaderboardFallsBackToRegisteredTeamNameWhenNoCompletion() async {
        let completions: [ChallengeCompletion] = []  // nobody completed anything
        let state = ChallengesFeature.State(
            gameId: "g",
            hunterId: "me",
            hunterIds: ["me", "h2"],
            completions: completions,
            registrations: ["me": "Julien", "h2": "Bob"]
        )

        let entries = state.leaderboard
        // Alphabetical by team (registration) since ties on 0 pts
        #expect(entries.map(\.teamName) == ["Bob", "Julien"])
    }

    @Test func leaderboardPrefersRegistrationTeamNameOverCompletionTeamName() async {
        // PP-90: registration teamName is the live source of truth. If the
        // user edits it post-completion, the leaderboard reflects the new
        // value rather than the snapshot baked into the completion doc.
        let completions = [makeCompletion(hunterId: "me", ids: ["c1"], total: 5, teamName: "Old Team")]
        let state = ChallengesFeature.State(
            gameId: "g",
            hunterId: "me",
            hunterIds: ["me"],
            completions: completions,
            registrations: ["me": "New Team"]
        )

        #expect(state.leaderboard.first?.teamName == "New Team")
    }

    // MARK: - Current hunter highlight helper

    @Test func myCompletedIdsReflectsCurrentHunterOnly() async {
        let completions = [
            makeCompletion(hunterId: "me", ids: ["c1", "c2"], total: 20, teamName: "Me"),
            makeCompletion(hunterId: "h2", ids: ["c3"], total: 10, teamName: "Other")
        ]
        let state = ChallengesFeature.State(
            gameId: "g",
            hunterId: "me",
            hunterIds: ["me", "h2"],
            completions: completions
        )

        #expect(state.myCompletedIds == ["c1", "c2"])
    }
}
