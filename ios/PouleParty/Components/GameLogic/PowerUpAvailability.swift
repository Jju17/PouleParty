//
//  PowerUpAvailability.swift
//  PouleParty
//
//  PP-35: pure helper that returns the power-up types that make sense for a
//  given GameMode. In `stayInTheZone` the chicken does not broadcast its
//  position so positional power-ups (invisibility / decoy / jammer) are
//  filtered out entirely. The creation wizard uses this list to render only
//  the compatible cards (strict filtering, no greying-out), and the cloud
//  function mirror lives in `functions/src/powerUpSpawn.ts`
//  (`filterEnabledTypesServer`) so the spawn never produces a useless type.
//
//  Must stay byte-for-byte equivalent to the Android sibling
//  `ui/gamelogic/PowerUpAvailability.kt`.
//

import Foundation

/// Power-up types that have no effect in `stayInTheZone` because they rely
/// on the chicken broadcasting its position.
private let positionDependentPowerUps: Set<PowerUp.PowerUpType> = [
    .invisibility,
    .decoy,
    .jammer,
]

/// Returns the ordered list of power-up types that are usable in `mode`.
/// Strict filter: types that are not compatible are omitted from the
/// returned list, not greyed out, so callers can iterate without re-checking
/// compatibility.
///
/// Compatibility matrix (PP-35):
/// - `followTheChicken` → every type.
/// - `stayInTheZone` → `zoneFreeze`, `zonePreview`, `radarPing` only.
func availablePowerUpTypes(for mode: Game.GameMode) -> [PowerUp.PowerUpType] {
    PowerUp.PowerUpType.allCases.filter { type in
        switch mode {
        case .followTheChicken:
            return true
        case .stayInTheZone:
            return !positionDependentPowerUps.contains(type)
        }
    }
}
