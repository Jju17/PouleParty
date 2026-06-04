package dev.rahier.pouleparty.ui.chickenmap

/** One-shot effects emitted by [ChickenMapViewModel]. */
sealed interface ChickenMapEffect {
    /** Navigate back to the home menu (chicken cancelled the game). */
    object NavigateToMenu : ChickenMapEffect
    /** Navigate to the victory screen with the post-game leaderboard. */
    object NavigateToVictory : ChickenMapEffect
    object OpenValidationQueue : ChickenMapEffect
    /** PP-107: a GameMaster swapped the chicken to someone else mid-`waiting`;
     *  this player is now a plain hunter. Re-route to the hunter map. */
    data class NavigateToHunterMap(val gameId: String, val teamName: String) : ChickenMapEffect
}
