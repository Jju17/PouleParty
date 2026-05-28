package dev.rahier.pouleparty.ui.challenges

import dev.rahier.pouleparty.model.Challenge
import dev.rahier.pouleparty.model.SubmissionMediaType

sealed interface ChallengesIntent {
    data class TabSelected(val tab: ChallengesTab) : ChallengesIntent
    /** Single-tap flow: tapping "Doing it" opens the camera immediately. */
    data class DoingItTapped(val challenge: Challenge) : ChallengesIntent
    /** Native camera returned a captured photo OR short video. Auto-submitted. */
    data class MediaCaptured(
        val challengeId: String,
        val bytes: ByteArray,
        val mediaType: SubmissionMediaType,
    ) : ChallengesIntent
    /** User backed out of the camera without capturing. */
    data object CaptureCancelled : ChallengesIntent
    data object UploadErrorDismissed : ChallengesIntent
}

enum class ChallengesTab {
    CHALLENGES,
    LEADERBOARD
}

enum class ChallengeStatus {
    AVAILABLE,
    SUBMITTING,
    AWAITING_VALIDATION,
    VALIDATED,
    REJECTED,
}
