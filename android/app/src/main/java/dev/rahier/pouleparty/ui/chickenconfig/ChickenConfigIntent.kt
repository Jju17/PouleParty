package dev.rahier.pouleparty.ui.chickenconfig

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.PowerUpType

/** User-initiated actions on the chicken-config screen. */
sealed interface ChickenConfigIntent {
    object StartTimeTapped : ChickenConfigIntent
    object DismissTimePicker : ChickenConfigIntent
    object MapSetupTapped : ChickenConfigIntent
    object DismissMapConfig : ChickenConfigIntent
    object PowerUpSelectionTapped : ChickenConfigIntent
    object DismissPowerUpSelection : ChickenConfigIntent
    object CodeCopied : ChickenConfigIntent
    object DismissAlert : ChickenConfigIntent
    object StartGameTapped : ChickenConfigIntent
    data class StartTimeChanged(val hour: Int, val minute: Int) : ChickenConfigIntent
    data class GameModeChanged(val mode: GameMod) : ChickenConfigIntent
    data class ChickenCanSeeHuntersToggled(val value: Boolean) : ChickenConfigIntent
    data class RadiusIntervalUpdateChanged(val value: Double) : ChickenConfigIntent
    data class RadiusDeclineChanged(val value: Double) : ChickenConfigIntent
    data class HeadStartChanged(val value: Double) : ChickenConfigIntent
    data class InitialRadiusChanged(val value: Double) : ChickenConfigIntent
    data class ExpertModeToggled(val expert: Boolean) : ChickenConfigIntent
    data class GameDurationChanged(val minutes: Double) : ChickenConfigIntent
    data class LocationSelected(val point: Point) : ChickenConfigIntent
    data class FinalLocationSelected(val point: Point?) : ChickenConfigIntent
    data class PowerUpsToggled(val enabled: Boolean) : ChickenConfigIntent
    data class PowerUpTypeToggled(val type: PowerUpType) : ChickenConfigIntent
}
