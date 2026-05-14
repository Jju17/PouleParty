package dev.rahier.pouleparty.ui.gamelogic

import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.powerups.model.PowerUpType

/**
 * PP-35: pure helper that returns the power-up types that make sense for a
 * given [GameMod]. In `stayInTheZone` the chicken does not broadcast its
 * position so positional power-ups (invisibility / decoy / jammer) are
 * filtered out entirely. The creation wizard uses this list to render only
 * the compatible cards (strict filtering, no greying-out), and the cloud
 * function mirror lives in `functions/src/powerUpSpawn.ts`
 * (`filterEnabledTypesServer`) so the spawn never produces a useless type.
 *
 * Must stay byte-for-byte equivalent to the iOS sibling
 * `Components/GameLogic/PowerUpAvailability.swift`.
 */

/**
 * Power-up types that have no effect in `stayInTheZone` because they rely on
 * the chicken broadcasting its position.
 */
private val POSITION_DEPENDENT_POWER_UPS: Set<PowerUpType> = setOf(
    PowerUpType.INVISIBILITY,
    PowerUpType.DECOY,
    PowerUpType.JAMMER,
)

/**
 * Returns the ordered list of power-up types that are usable in [mode].
 * Strict filter: types that are not compatible are omitted from the
 * returned list, not greyed out, so callers can iterate without re-checking
 * compatibility.
 *
 * Compatibility matrix (PP-35):
 * - `FOLLOW_THE_CHICKEN` → every type.
 * - `STAY_IN_THE_ZONE` → `ZONE_FREEZE`, `ZONE_PREVIEW`, `RADAR_PING` only.
 */
fun availablePowerUpTypes(mode: GameMod): List<PowerUpType> =
    PowerUpType.entries.filter { type ->
        when (mode) {
            GameMod.FOLLOW_THE_CHICKEN -> true
            GameMod.STAY_IN_THE_ZONE -> type !in POSITION_DEPENDENT_POWER_UPS
        }
    }
