import ComposableArchitecture
import FirebaseCore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct ValidationQueueFeatureTests {
    private func submission(_ id: String?, hunter: String = "h1") -> ChallengeSubmission {
        ChallengeSubmission(firestoreId: id, challengeId: "c1", hunterId: hunter)
    }

    @Test func acceptingAProofCallsTheServerAndClearsTheBusyFlag() async {
        let calls = LockIsolated<[(String, Bool)]>([])
        let store = TestStore(initialState: ValidationQueueFeature.State(gameId: "g1", hunterIds: ["h1"])) {
            ValidationQueueFeature()
        } withDependencies: {
            $0.apiClient.validateChallengeSubmission = { _, id, accept in calls.withValue { $0.append((id, accept)) } }
        }
        await store.send(.view(.validateTapped(submission("s1")))) {
            $0.busyIds = ["s1"]
        }
        await store.receive(\.internal.validateSucceeded) {
            $0.busyIds = []
        }
        #expect(calls.value.count == 1)
        #expect(calls.value.first?.1 == true)
    }

    @Test func aRejectedCallShowsATranslatedError() async {
        let store = TestStore(initialState: ValidationQueueFeature.State(gameId: "g1", hunterIds: ["h1"])) {
            ValidationQueueFeature()
        } withDependencies: {
            $0.apiClient.validateChallengeSubmission = { _, _, _ in throw ApiError(code: .submissionAlreadyHandled) }
        }
        store.exhaustivity = .off
        await store.send(.view(.rejectTapped(submission("s2"))))
        await store.receive(\.internal.validateFailed)
        #expect(store.state.error == ApiErrorCode.submissionAlreadyHandled.message)
        #expect(store.state.busyIds.isEmpty)
    }

    @Test func aSecondTapWhileBusyIsIgnored() async {
        var state = ValidationQueueFeature.State(gameId: "g1", hunterIds: ["h1"])
        state.busyIds = ["s1"]
        let store = TestStore(initialState: state) {
            ValidationQueueFeature()
        }
        await store.send(.view(.validateTapped(submission("s1"))))
    }

    @Test func aProofWithoutAnIdIsIgnored() async {
        let store = TestStore(initialState: ValidationQueueFeature.State(gameId: "g1", hunterIds: ["h1"])) {
            ValidationQueueFeature()
        }
        await store.send(.view(.validateTapped(submission(nil))))
    }

    @Test func theOpenedProofClosesWhenAnotherRefereeHandledIt() async {
        var state = ValidationQueueFeature.State(gameId: "g1", hunterIds: ["h1"])
        state.selected = submission("s1")
        let store = TestStore(initialState: state) {
            ValidationQueueFeature()
        }
        let remaining = [submission("s2")]
        await store.send(.internal(.submissionsUpdated(remaining))) {
            $0.submissions = remaining
            $0.selected = nil
        }
    }

    @Test func teamNamesFallBackWhenNoRegistrationExists() {
        var state = ValidationQueueFeature.State(gameId: "g1", hunterIds: ["h1", "h2"])
        state.registrations = [Registration(userId: "h1", teamName: "Les Renards")]
        #expect(state.teamName(for: "h1") == "Les Renards")
        #expect(state.teamName(for: "h2") == AppConstants.fallbackTeamName)
    }
}

struct LeaderboardEntriesTests {
    @Test func everyKnownHunterAppearsOnceWithItsTeamName() {
        var game = Game.mock
        game.roles = ["c": "chicken", "h1": "hunter", "h2": "hunter"]
        let found = Date(timeIntervalSince1970: 1_900_000_000)
        game.winners = [
            Winner(hunterId: "h1", hunterName: "old", timestamp: .init(date: found.addingTimeInterval(60))),
            Winner(hunterId: "h1", hunterName: "old", timestamp: .init(date: found)),
        ]
        let entries = buildLeaderboardEntries(
            game: game,
            registrations: [Registration(userId: "h1", teamName: "Team One"), Registration(userId: "h3", teamName: "Late")],
            currentUserId: "h2"
        )
        #expect(Set(entries.map(\.id)) == ["h1", "h2", "h3"])
        let h1 = entries.first { $0.id == "h1" }
        #expect(h1?.displayName == "Team One")
        #expect(h1?.foundTimestamp == found)
        #expect(entries.first { $0.id == "h2" }?.displayName == AppConstants.fallbackTeamName)
        #expect(entries.first { $0.id == "h2" }?.isCurrentUser == true)
    }
}
