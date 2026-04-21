package dev.rahier.pouleparty.ui.chickenmap

import dev.rahier.pouleparty.powerups.model.PowerUp

/** User-initiated actions on the chicken-map screen. */
sealed interface ChickenMapIntent {
    object CancelGameTapped : ChickenMapIntent
    object DismissCancelAlert : ChickenMapIntent
    object ConfirmCancelGame : ChickenMapIntent
    object ConfirmGameOver : ChickenMapIntent
    object InfoTapped : ChickenMapIntent
    object DismissGameInfo : ChickenMapIntent
    object FoundButtonTapped : ChickenMapIntent
    object DismissFoundCode : ChickenMapIntent
    object CodeCopied : ChickenMapIntent
    object PowerUpInventoryTapped : ChickenMapIntent
    object DismissPowerUpInventory : ChickenMapIntent
    data class ActivatePowerUp(val powerUp: PowerUp) : ChickenMapIntent
}
