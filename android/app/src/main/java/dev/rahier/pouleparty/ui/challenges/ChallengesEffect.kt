package dev.rahier.pouleparty.ui.challenges

/** One-shot effects emitted by [ChallengesViewModel]. */
sealed interface ChallengesEffect {
    data class ShowError(val message: String) : ChallengesEffect
}
