package dev.rahier.pouleparty.ui.huntermap

import dev.rahier.pouleparty.powerups.model.PowerUp

/** User-initiated actions on the hunter-map screen. */
sealed interface HunterMapIntent {
    /**
     * Dispatched from `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` in
     * [HunterMapScreen]. Triggers an immediate hunter-location refresh so
     * the chicken's map catches up when the player re-opens the app,
     * rather than waiting for the periodic 5 s writer's next tick.
     */
    object AppResumed : HunterMapIntent
    object DismissRegistrationRequiredAlert : HunterMapIntent
    object PowerUpInventoryTapped : HunterMapIntent
    object DismissPowerUpInventory : HunterMapIntent
    object FoundButtonTapped : HunterMapIntent
    object DismissFoundCodeEntry : HunterMapIntent
    object SubmitFoundCode : HunterMapIntent
    object VictoryNavigated : HunterMapIntent
    object DismissWrongCodeAlert : HunterMapIntent
    object RetryWinnerRegistration : HunterMapIntent
    object DismissWinnerRegistrationError : HunterMapIntent
    object LeaveGameTapped : HunterMapIntent
    object DismissLeaveAlert : HunterMapIntent
    object ConfirmLeaveGame : HunterMapIntent
    object ConfirmGameOver : HunterMapIntent
    object InfoTapped : HunterMapIntent
    object DismissGameInfo : HunterMapIntent
    object CodeCopied : HunterMapIntent
    data class ActivatePowerUp(val powerUp: PowerUp) : HunterMapIntent
    data class EnteredCodeChanged(val code: String) : HunterMapIntent
}
