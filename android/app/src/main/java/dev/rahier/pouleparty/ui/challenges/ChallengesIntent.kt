package dev.rahier.pouleparty.ui.challenges

import dev.rahier.pouleparty.model.Challenge

sealed interface ChallengesIntent {
    data class TabSelected(val tab: ChallengesTab) : ChallengesIntent
    data class MarkAsDoneTapped(val challenge: Challenge) : ChallengesIntent
    data class SubmitForValidationTapped(val challenge: Challenge) : ChallengesIntent
    data class PhotoPicked(val challengeId: String, val bytes: ByteArray) : ChallengesIntent
    data object PhotoSourceCancelled : ChallengesIntent
    data object UploadErrorDismissed : ChallengesIntent
}

enum class ChallengesTab {
    CHALLENGES,
    LEADERBOARD
}

enum class ChallengeStatus {
    AVAILABLE,
    PENDING_LOCAL,
    SUBMITTING,
    AWAITING_VALIDATION,
    VALIDATED,
    REJECTED,
}
