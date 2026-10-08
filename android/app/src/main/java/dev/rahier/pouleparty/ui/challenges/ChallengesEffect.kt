package dev.rahier.pouleparty.ui.challenges

/** One-shot effects emitted by [ChallengesViewModel]. */
sealed interface ChallengesEffect {
    data class ShowError(@param:androidx.annotation.StringRes val messageRes: Int) : ChallengesEffect
}
