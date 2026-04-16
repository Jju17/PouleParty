package dev.rahier.pouleparty.ui.huntermap

/** One-shot effects emitted by [HunterMapViewModel]. */
sealed interface HunterMapEffect {
    /** Hunter left the game — return to home menu. */
    object NavigateToMenu : HunterMapEffect
    /** Game finished — go to the victory/leaderboard screen. */
    object NavigateToVictory : HunterMapEffect
}
