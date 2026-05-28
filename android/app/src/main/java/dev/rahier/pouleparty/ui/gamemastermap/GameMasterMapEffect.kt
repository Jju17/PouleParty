package dev.rahier.pouleparty.ui.gamemastermap

sealed class GameMasterMapEffect {
    data object ReturnedToMenu : GameMasterMapEffect()
    data object OpenValidationQueue : GameMasterMapEffect()
    /** Status flipped to DONE. Parent navigates to the Victory /
     *  leaderboard screen so the GM has a clear "Back to menu" path. */
    data object NavigateToVictory : GameMasterMapEffect()
}
