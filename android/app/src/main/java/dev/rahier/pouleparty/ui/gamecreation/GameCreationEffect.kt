package dev.rahier.pouleparty.ui.gamecreation

/** One-shot effects emitted by [GameCreationViewModel]. */
sealed interface GameCreationEffect {
    /** Navigate to the chicken map with the freshly created game. */
    data class GameStarted(val gameId: String) : GameCreationEffect

    /**
     * Forfait flow: the server created the game doc (in `pending_payment`).
     * Caller should dismiss the creation UI and return to Home — the game
     * appears in `Settings > My Games` once the webhook flips status to
     * `waiting`.
     */
    data class PaidGameCreated(val gameId: String) : GameCreationEffect
}
