package dev.rahier.pouleparty.ui.challenges

import dev.rahier.pouleparty.model.Challenge

/** User-initiated actions on the Challenges sheet. */
sealed interface ChallengesIntent {
    data class TabSelected(val tab: ChallengesTab) : ChallengesIntent
    data class ValidateTapped(val challenge: Challenge) : ChallengesIntent
    object ConfirmValidation : ChallengesIntent
    object DismissConfirmation : ChallengesIntent
}

/** Sheet segmented picker tabs. */
enum class ChallengesTab {
    CHALLENGES,
    LEADERBOARD
}
