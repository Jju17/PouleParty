package dev.rahier.pouleparty.ui.chickenconfig

/** One-shot effects emitted by [ChickenConfigViewModel]. */
sealed interface ChickenConfigEffect {
    /** Game persisted in Firestore — navigate to the chicken map. */
    data class GameStarted(val gameId: String) : ChickenConfigEffect
}
