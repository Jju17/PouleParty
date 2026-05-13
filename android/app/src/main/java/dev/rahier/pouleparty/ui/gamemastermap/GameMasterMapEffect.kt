package dev.rahier.pouleparty.ui.gamemastermap

/** One-shot effects from the GameMaster map screen (PP-24). */
sealed class GameMasterMapEffect {
    data object ReturnedToMenu : GameMasterMapEffect()
}
