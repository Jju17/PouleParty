//
//  ChallengeProgressTests.swift
//  PoulePartyTests
//

import Foundation
import Testing
@testable import PouleParty

struct ChallengeProgressTests {

    private func challenge(
        id: String,
        level: Int,
        type: Challenge.ChallengeType = .oneShot
    ) -> Challenge {
        Challenge(
            firestoreId: id,
            title: id,
            points: 10,
            type: type,
            level: level,
            number: 1
        )
    }

    @Test func level1IsAlwaysUnlocked() {
        let result = ChallengeProgress.isLevelUnlocked(
            level: 1,
            challenges: [],
            validatedChallengeIds: []
        )
        #expect(result == true)
    }

    @Test func nextLevelUnlockedWhenPreviousHasOnlyRepeatable() {
        let challenges = (1...3).map {
            challenge(id: "rep-\($0)", level: 1, type: .repeatable)
        }
        let result = ChallengeProgress.isLevelUnlocked(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: []
        )
        #expect(result == true)
    }

    @Test func oneOutOfOneOneShotUnlocksNext() {
        let challenges = [challenge(id: "a", level: 1)]
        let result = ChallengeProgress.isLevelUnlocked(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: ["a"]
        )
        #expect(result == true)
    }

    @Test func fourOutOfFiveOneShotUnlocksNext() {
        let challenges = (1...5).map { challenge(id: "c\($0)", level: 1) }
        let validated: Set<String> = ["c1", "c2", "c3", "c4"]
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: validated
        )
        #expect(progress.validated == 4)
        #expect(progress.total == 5)
        #expect(progress.threshold == 4)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: challenges,
                validatedChallengeIds: validated
            ) == true
        )
    }

    @Test func threeOutOfFiveOneShotKeepsNextLocked() {
        let challenges = (1...5).map { challenge(id: "c\($0)", level: 1) }
        let validated: Set<String> = ["c1", "c2", "c3"]
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: validated
        )
        #expect(progress.validated == 3)
        #expect(progress.total == 5)
        #expect(progress.threshold == 4)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: challenges,
                validatedChallengeIds: validated
            ) == false
        )
    }

    @Test func repeatableValidationsAreIgnoredInCount() {
        let oneShots = (1...4).map { challenge(id: "o\($0)", level: 1) }
        let repeatables = (1...5).map {
            challenge(id: "r\($0)", level: 1, type: .repeatable)
        }
        // 3 oneShots + 10 repeatable validations (modelled as 5 ids — the
        // count in `validatedChallengeIds` only tracks oneShots anyway).
        let validated: Set<String> = ["o1", "o2", "o3", "r1", "r2", "r3", "r4", "r5"]
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: oneShots + repeatables,
            validatedChallengeIds: validated
        )
        #expect(progress.validated == 3)
        #expect(progress.total == 4)
        #expect(progress.threshold == 4)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: oneShots + repeatables,
                validatedChallengeIds: validated
            ) == false
        )
    }
}
