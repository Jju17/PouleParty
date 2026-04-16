package dev.rahier.pouleparty.ui.map

import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType
import java.util.Date

/**
 * Common read-only surface shared by [ChickenMapUiState] and [HunterMapUiState].
 * Mirrors the iOS `MapFeatureState` protocol: lets shared composables
 * (map bottom bar, common overlays, haptics effect) accept any map state
 * without duplicating parameter lists.
 */
interface MapUiState {
    val game: Game
    val nextRadiusUpdate: Date?
    val nowDate: Date
    val radius: Int
    val circleCenter: Point?
    val winnerNotification: String?
    val countdownNumber: Int?
    val countdownText: String?
    val isOutsideZone: Boolean
    val availablePowerUps: List<PowerUp>
    val collectedPowerUps: List<PowerUp>
    val powerUpNotification: String?
    val lastActivatedPowerUpType: PowerUpType?
    val showGameInfo: Boolean
    val showPowerUpInventory: Boolean

    /**
     * `true` once the player's effective start time has passed.
     * Chicken uses `game.startDate`, hunter uses `game.hunterStartDate`.
     */
    val hasGameStarted: Boolean
}
