package dev.rahier.pouleparty.ui.home

/**
 * One-shot events emitted by [HomeViewModel]. The screen collects them
 * from a [Channel] and reacts (navigation, toast). Effects are not part
 * of [HomeUiState] because re-emitting them on recomposition would break
 * idempotency.
 */
sealed interface HomeEffect {
    /** Navigate to the chicken map (rejoin or start as chicken). */
    data class NavigateToChickenMap(val gameId: String) : HomeEffect
    /** Navigate to the hunter map for a live game. */
    data class NavigateToHunterMap(val gameId: String, val hunterName: String) : HomeEffect
    /** Navigate to the post-game screen when the joined game is DONE. */
    data class NavigateToGameDone(val gameId: String) : HomeEffect
}
