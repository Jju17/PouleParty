package dev.rahier.pouleparty.ui.gamecreation

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.powerups.model.PowerUpType

/**
 * User-initiated actions on the game-creation wizard. Dispatched through
 * [GameCreationViewModel.onIntent] so each button wires to exactly one
 * case — mirrors the [dev.rahier.pouleparty.ui.home.HomeIntent] pattern.
 */
sealed interface GameCreationIntent {
    object Next : GameCreationIntent
    object Back : GameCreationIntent
    object StartTimeTapped : GameCreationIntent
    object DismissDatePicker : GameCreationIntent
    object DismissTimePicker : GameCreationIntent
    object PowerUpSelectionTapped : GameCreationIntent
    object DismissPowerUpSelection : GameCreationIntent
    object CodeCopied : GameCreationIntent
    object DismissAlert : GameCreationIntent
    object StartGameTapped : GameCreationIntent
    data class ParticipatingChanged(val isParticipating: Boolean) : GameCreationIntent
    data class GameModeChanged(val mode: GameMod) : GameCreationIntent
    data class StartDateChanged(val year: Int, val month: Int, val day: Int) : GameCreationIntent
    data class StartTimeChanged(val hour: Int, val minute: Int) : GameCreationIntent
    data class DurationChanged(val minutes: Double) : GameCreationIntent
    data class HeadStartChanged(val minutes: Double) : GameCreationIntent
    data class InitialRadiusChanged(val radius: Double) : GameCreationIntent
    data class MaxPlayersChanged(val value: Int) : GameCreationIntent
    data class PowerUpsToggled(val enabled: Boolean) : GameCreationIntent
    data class PowerUpTypeToggled(val type: PowerUpType) : GameCreationIntent
    data class ChickenCanSeeHuntersToggled(val value: Boolean) : GameCreationIntent
    data class LocationSelected(val point: Point) : GameCreationIntent
    data class FinalLocationSelected(val point: Point?) : GameCreationIntent
    /** PP-88: chicken toggled the GameMaster role on/off on the wizard step. */
    data class GameMasterEnabledChanged(val enabled: Boolean) : GameCreationIntent
    /** PP-88: chicken typed into the 4-digit GameMaster password field. */
    data class GameMasterPasswordChanged(val password: String) : GameCreationIntent
    /**
     * PP-13 phase 1 — fired when the wizard enters `ZONES_RECAP`.
     * Recomputes `Game.zone.radius` from the placed pins (via the
     * client-side `computeZoneRadius` mirror of PP-69) and allocates a
     * fresh `driftSeed` if none is set.
     */
    object ZonesRecapEntered : GameCreationIntent
    /**
     * PP-14 phase 1 — Shuffle button on the recap step regenerates the
     * drift seed; preview circles redraw deterministically.
     */
    object ShuffleDriftSeed : GameCreationIntent
}
