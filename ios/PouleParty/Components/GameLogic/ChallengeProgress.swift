//
//  ChallengeProgress.swift
//  PouleParty
//

import Foundation

enum ChallengeProgress {
    /// Returns `true` when the hunter has validated at least
    /// `ceil(N × 0.80)` of the `oneShot` challenges in `level - 1`,
    /// where N is the total oneShot count at that previous level.
    /// `level == 1` is always unlocked. Repeatable challenges are
    /// excluded from the calculation: they have no upper bound so
    /// they can never gate progression.
    static func isLevelUnlocked(
        level: Int,
        challenges: [Challenge],
        validatedChallengeIds: Set<String>
    ) -> Bool {
        guard level > 1 else { return true }
        let progress = levelProgress(
            level: level,
            challenges: challenges,
            validatedChallengeIds: validatedChallengeIds
        )
        return progress.validated >= progress.threshold
    }

    /// Returns the count of oneShot challenges already validated for
    /// `level - 1`, the total oneShot count at that previous level,
    /// and the `ceil(N × 0.80)` threshold required to unlock `level`.
    /// The UI uses this to render the "🔒 Unlock at X / Y" label.
    /// When `level == 1` (or `level - 1` has no oneShot challenges)
    /// every component returns 0, which keeps the level unlocked.
    static func levelProgress(
        level: Int,
        challenges: [Challenge],
        validatedChallengeIds: Set<String>
    ) -> (validated: Int, total: Int, threshold: Int) {
        let previousLevel = level - 1
        let oneShotsAtPrevious = challenges.filter {
            $0.level == previousLevel && $0.type == .oneShot
        }
        let total = oneShotsAtPrevious.count
        let validated = oneShotsAtPrevious.filter {
            validatedChallengeIds.contains($0.id)
        }.count
        let threshold = Int(ceil(Double(total) * 0.80))
        return (validated, total, threshold)
    }
}
