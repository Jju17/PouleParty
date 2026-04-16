package dev.rahier.pouleparty.ui.chickenmap

/** One-shot effects emitted by [ChickenMapViewModel]. */
sealed interface ChickenMapEffect {
    /** Navigate back to the home menu (chicken cancelled the game). */
    object NavigateToMenu : ChickenMapEffect
    /** Navigate to the victory screen with the post-game leaderboard. */
    object NavigateToVictory : ChickenMapEffect
}
