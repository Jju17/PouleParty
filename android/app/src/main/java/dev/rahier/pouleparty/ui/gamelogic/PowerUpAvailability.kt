package dev.rahier.pouleparty.ui.gamelogic

import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.powerups.model.PowerUpType

/**
 * Power-up types that have no effect in `stayInTheZone` because they rely on
 * the chicken broadcasting its position.
 */
private val POSITION_DEPENDENT_POWER_UPS: Set<PowerUpType> = setOf(
    PowerUpType.INVISIBILITY,
    PowerUpType.DECOY,
    PowerUpType.JAMMER,
)

fun availablePowerUpTypes(mode: GameMod): List<PowerUpType> =
    PowerUpType.entries.filter { type ->
        when (mode) {
            GameMod.FOLLOW_THE_CHICKEN -> true
            GameMod.STAY_IN_THE_ZONE -> type !in POSITION_DEPENDENT_POWER_UPS
        }
    }
