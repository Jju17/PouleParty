package dev.rahier.pouleparty.ui.huntermap

/** One-shot effects emitted by [HunterMapViewModel]. */
sealed interface HunterMapEffect {
    /** Hunter left the game — return to home menu. */
    object NavigateToMenu : HunterMapEffect
    /** Game finished — go to the victory/leaderboard screen. */
    object NavigateToVictory : HunterMapEffect
    /** PP-107: a GameMaster re-designated this hunter as the chicken
     *  mid-`waiting`. Re-route to the chicken map (replacing the hunter
     *  map in the back stack) and surface the "new chicken" alert there. */
    data class NavigateToChickenMap(val gameId: String) : HunterMapEffect
}
