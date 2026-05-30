package dev.rahier.pouleparty.ui.chickenmap

import dev.rahier.pouleparty.powerups.model.PowerUp

/** User-initiated actions on the chicken-map screen. */
sealed interface ChickenMapIntent {
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
    /** PP-71: chicken taps LAUNCH when status == READY_TO_LAUNCH. */
    object LaunchTapped : ChickenMapIntent
    /** PP-71: dismisses the error alert after a failed launchGame call. */
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
