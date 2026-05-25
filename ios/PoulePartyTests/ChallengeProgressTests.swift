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
            points: 10,
            type: type,
            level: level,
            number: 1,
            titleByLocale: ["fr": id]
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

    @Test func tenOutOfTenOneShotUnlocksNext() {
        let challenges = (1...10).map { challenge(id: "c\($0)", level: 1) }
        let validated: Set<String> = Set((1...10).map { "c\($0)" })
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: validated
        )
        #expect(progress.validated == 10)
        #expect(progress.total == 10)
        #expect(progress.threshold == 8)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: challenges,
                validatedChallengeIds: validated
            ) == true
        )
    }

    @Test func eightOfTenIsExactlyTheBoundary() {
        let challenges = (1...10).map { challenge(id: "c\($0)", level: 1) }
        let validated: Set<String> = ["c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8"]
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: validated
        )
        #expect(progress.threshold == 8)
        #expect(progress.validated == 8)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: challenges,
                validatedChallengeIds: validated
            ) == true
        )
    }

    @Test func sevenOfTenStaysBlocked() {
        let challenges = (1...10).map { challenge(id: "c\($0)", level: 1) }
        let validated: Set<String> = ["c1", "c2", "c3", "c4", "c5", "c6", "c7"]
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: challenges,
                validatedChallengeIds: validated
            ) == false
        )
    }

    @Test func mixedLevelCatalogOnlyCountsRequestedLevel() {
        let level1 = (1...3).map { challenge(id: "l1-\($0)", level: 1) }
        let level2 = (1...5).map { challenge(id: "l2-\($0)", level: 2) }
        let level3 = (1...2).map { challenge(id: "l3-\($0)", level: 3) }
        let all = level1 + level2 + level3
        // Unlock level 3 → looks only at level 2. 4/5 oneShot at level 2 → unlock.
        let validated: Set<String> = ["l2-1", "l2-2", "l2-3", "l2-4"]
        let progress3 = ChallengeProgress.levelProgress(
            level: 3,
            challenges: all,
            validatedChallengeIds: validated
        )
        #expect(progress3.total == 5)
        #expect(progress3.validated == 4)
        #expect(progress3.threshold == 4)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 3,
                challenges: all,
                validatedChallengeIds: validated
            ) == true
        )
        // Same data: level 2 looks at level 1, 0/3 validated → blocked.
        let progress2 = ChallengeProgress.levelProgress(
            level: 2,
            challenges: all,
            validatedChallengeIds: validated
        )
        #expect(progress2.total == 3)
        #expect(progress2.validated == 0)
        #expect(progress2.threshold == 3)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: all,
                validatedChallengeIds: validated
            ) == false
        )
    }

    @Test func unknownValidatedIdsAreIgnored() {
        // A `validatedChallengeIds` may carry IDs that no longer exist in
        // the catalog (e.g. a challenge was removed by the admin). The
        // accessor only counts IDs that match the previous-level
        // oneShot challenges; orphan IDs are silently dropped.
        let challenges = (1...3).map { challenge(id: "c\($0)", level: 1) }
        let validated: Set<String> = ["c1", "c2", "ghost-id"]
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: challenges,
            validatedChallengeIds: validated
        )
        #expect(progress.validated == 2)
        #expect(progress.total == 3)
        #expect(progress.threshold == 3)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: challenges,
                validatedChallengeIds: validated
            ) == false
        )
    }

    @Test func validatedOnlyHasRepeatableIdsStillCountsZero() {
        let oneShots = (1...3).map { challenge(id: "o\($0)", level: 1) }
        let repeatables = (1...3).map {
            challenge(id: "r\($0)", level: 1, type: .repeatable)
        }
        let validated: Set<String> = ["r1", "r2", "r3"]
        let progress = ChallengeProgress.levelProgress(
            level: 2,
            challenges: oneShots + repeatables,
            validatedChallengeIds: validated
        )
        #expect(progress.validated == 0)
        #expect(progress.total == 3)
        #expect(progress.threshold == 3)
        #expect(
            ChallengeProgress.isLevelUnlocked(
                level: 2,
                challenges: oneShots + repeatables,
                validatedChallengeIds: validated
            ) == false
        )
    }
}
