//
//  ChallengesFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct ChallengesFeatureTests {

    private func makeChallenge(id: String, title: String = "t", body: String = "b", points: Int = 10) -> Challenge {
        Challenge(firestoreId: id, title: title, body: body, points: points, lastUpdated: nil)
    }

    private func makeCompletion(hunterId: String, ids: [String] = [], total: Int = 0, teamName: String = "") -> ChallengeCompletion {
        ChallengeCompletion(hunterId: hunterId, completedChallengeIds: ids, totalPoints: total, teamName: teamName)
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

    @Test func nicknamesFetchedPopulatesState() async {
        let store = TestStore(
            initialState: ChallengesFeature.State(
                gameId: "g",
                hunterId: "me",
                hunterIds: ["me"]
            )
        ) {
            ChallengesFeature()
        }

        await store.send(.internal(.nicknamesFetched(["h2": "Bob"]))) {
            $0.nicknames = ["h2": "Bob"]
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

    // MARK: - Validation flow

    @Test func validateTappedPresentsConfirmAlert() async {
        let challenge = makeChallenge(id: "c1", title: "Run", body: "Run fast", points: 50)
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

        await store.send(.view(.validateTapped(challenge))) {
            $0.pendingChallenge = challenge
            $0.destination = .alert(
                AlertState {
                    TextState("Did you send the proof on WhatsApp?")
                } actions: {
                    ButtonState(role: .cancel) {
                        TextState("Cancel")
                    }
                    ButtonState(action: .confirmValidation(challenge)) {
                        TextState("Confirm")
                    }
                } message: {
                    TextState("Tap Confirm to validate this challenge.")
                }
            )
        }
    }

    @Test func validateTappedForAlreadyCompletedChallengeIsNoop() async {
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

        await store.send(.view(.validateTapped(challenge)))
        // No state change — TestStore exhaustivity confirms this.
    }

    @Test func confirmValidationInvokesMarkChallengeCompleted() async {
        let challenge = makeChallenge(id: "c1", points: 25)
        let gotWrite = LockIsolated<(String, String, String, String, Int)?>(nil)

        var state = ChallengesFeature.State(
            gameId: "g1",
            hunterId: "me",
            hunterIds: ["me"],
            myTeamName: "Team Me",
            challenges: [challenge]
        )
        state.pendingChallenge = challenge
        state.destination = .alert(
            AlertState {
                TextState("Did you send the proof on WhatsApp?")
            } actions: {
                ButtonState(action: .confirmValidation(challenge)) {
                    TextState("Confirm")
                }
            }
        )

        let store = TestStore(initialState: state) {
            ChallengesFeature()
        } withDependencies: {
            $0.apiClient.markChallengeCompleted = { gameId, hunterId, teamName, challengeId, points in
                gotWrite.setValue((gameId, hunterId, teamName, challengeId, points))
            }
        }

        await store.send(.destination(.presented(.alert(.confirmValidation(challenge))))) {
            $0.pendingChallenge = nil
            $0.destination = nil
        }
        await store.finish()

        let captured = gotWrite.value
        #expect(captured?.0 == "g1")
        #expect(captured?.1 == "me")
        #expect(captured?.2 == "Team Me")
        #expect(captured?.3 == "c1")
        #expect(captured?.4 == 25)
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
            nicknames: ["me": "Me", "h4": "D"]
        )

        let entries = state.leaderboard
        #expect(entries.count == 4)
        #expect(entries[0].hunterId == "h2")
        #expect(entries[0].totalPoints == 30)
        #expect(entries[0].teamName == "Team B")
        #expect(entries[1].hunterId == "h3")
        #expect(entries[1].totalPoints == 10)
        // Hunters without completion docs fall back to nickname, 0 pts, sorted
        // alphabetically by team name.
        #expect(entries[2].totalPoints == 0)
        #expect(entries[3].totalPoints == 0)
        let zeroPointIds = Set([entries[2].hunterId, entries[3].hunterId])
        #expect(zeroPointIds == Set(["me", "h4"]))
    }

    @Test func leaderboardFallsBackToNicknameWhenTeamNameMissing() async {
        let completions: [ChallengeCompletion] = []  // nobody completed anything
        let state = ChallengesFeature.State(
            gameId: "g",
            hunterId: "me",
            hunterIds: ["me", "h2"],
            completions: completions,
            nicknames: ["me": "Julien", "h2": "Bob"]
        )

        let entries = state.leaderboard
        // Alphabetical by team (nickname) since ties on 0 pts
        #expect(entries.map(\.teamName) == ["Bob", "Julien"])
    }

    @Test func leaderboardPrefersChallengeCompletionTeamNameOverNickname() async {
        let completions = [makeCompletion(hunterId: "me", ids: ["c1"], total: 5, teamName: "Poule Team")]
        let state = ChallengesFeature.State(
            gameId: "g",
            hunterId: "me",
            hunterIds: ["me"],
            completions: completions,
            nicknames: ["me": "Fallback Nickname"]
        )

        #expect(state.leaderboard.first?.teamName == "Poule Team")
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
