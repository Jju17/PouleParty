package dev.rahier.pouleparty.ui.gamecreation

/** One-shot effects emitted by [GameCreationViewModel]. */
sealed interface GameCreationEffect {
    /** Navigate to the chicken map with the freshly created game. */
    data class GameStarted(val gameId: String) : GameCreationEffect
}
