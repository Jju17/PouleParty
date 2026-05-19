package dev.rahier.pouleparty.ui.validation

import dev.rahier.pouleparty.model.ChallengeSubmission

sealed interface ValidationQueueIntent {
    data object CloseTapped : ValidationQueueIntent
    data class SubmissionTapped(val submission: ChallengeSubmission) : ValidationQueueIntent
    data object DetailDismissed : ValidationQueueIntent
    data class ValidateTapped(val submission: ChallengeSubmission) : ValidationQueueIntent
    data class RejectTapped(val submission: ChallengeSubmission) : ValidationQueueIntent
    data object ErrorDismissed : ValidationQueueIntent
}

sealed interface ValidationQueueEffect {
    data object Dismiss : ValidationQueueEffect
}
