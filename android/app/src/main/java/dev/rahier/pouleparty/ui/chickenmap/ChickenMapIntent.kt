package dev.rahier.pouleparty.ui.chickenmap

import dev.rahier.pouleparty.powerups.model.PowerUp

/** User-initiated actions on the chicken-map screen. */
sealed interface ChickenMapIntent {
    object RetryLoad : ChickenMapIntent
    object CancelGameTapped : ChickenMapIntent
    object DismissCancelAlert : ChickenMapIntent
    object ConfirmCancelGame : ChickenMapIntent
    object InfoTapped : ChickenMapIntent
    object DismissGameInfo : ChickenMapIntent
    object FoundButtonTapped : ChickenMapIntent
    object DismissFoundCode : ChickenMapIntent
    object CodeCopied : ChickenMapIntent
    object PowerUpInventoryTapped : ChickenMapIntent
    object DismissPowerUpInventory : ChickenMapIntent
    data class ActivatePowerUp(val powerUp: PowerUp) : ChickenMapIntent
    object ValidationQueueTapped : ChickenMapIntent
    object DismissNewChickenAlert : ChickenMapIntent
    object LaunchTapped : ChickenMapIntent
    object LaunchErrorDismissed : ChickenMapIntent
    /** Banner tap at game-end → navigate to the Victory / leaderboard page. */
    object ViewLeaderboardTapped : ChickenMapIntent
    /** QA panel (debug games only): force the game to end now. */
    object DebugEndNowTapped : ChickenMapIntent
    /**
     * QA panel (debug games only): advance one lifecycle step
     * (launch / shrink+spawn / collapse) without waiting on the clock.
     */
    object DebugAdvanceStepTapped : ChickenMapIntent
}
