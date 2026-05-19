package dev.rahier.pouleparty.ui.gamemastermap

sealed class GameMasterMapEffect {
    data object ReturnedToMenu : GameMasterMapEffect()
    data object OpenValidationQueue : GameMasterMapEffect()
}
