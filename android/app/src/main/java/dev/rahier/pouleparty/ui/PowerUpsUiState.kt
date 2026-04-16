package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType

/**
 * Read-only power-up UI surface shared by ChickenMapUiState and HunterMapUiState.
 * Mirrors the iOS `MapPowerUpsFeature.State`: groups every power-up UI field
 * so that shared composables (inventory sheet, notification banner, map
 * marker list) can accept a single `PowerUpsUiState` instead of ten loose
 * parameters.
 *
 * For now each map state keeps its own fields for backward compatibility;
 * this interface lets callers pick the relevant slice when helpful.
 */
interface PowerUpsUiState {
    val available: List<PowerUp>
    val collected: List<PowerUp>
    val showInventory: Boolean
    val notification: String?
    val lastActivatedType: PowerUpType?
    val activatingId: String?
}

/**
 * Thin view-state wrapper that exposes the `MapUiState` power-up fields
 * through the [PowerUpsUiState] contract. Use [MapUiState.powerUps] from
 * a composable to obtain it without refactoring existing state shapes.
 */
private data class PowerUpsView(
    override val available: List<PowerUp>,
    override val collected: List<PowerUp>,
    override val showInventory: Boolean,
    override val notification: String?,
    override val lastActivatedType: PowerUpType?,
    override val activatingId: String?,
) : PowerUpsUiState

/**
 * Projects the power-up fields from a [MapUiState] into a [PowerUpsUiState].
 * `activatingId` is optional on the projection because chicken and hunter
 * expose it as a concrete field only (not part of the interface).
 */
fun MapUiState.powerUps(activatingId: String? = null): PowerUpsUiState = PowerUpsView(
    available = availablePowerUps,
    collected = collectedPowerUps,
    showInventory = showPowerUpInventory,
    notification = powerUpNotification,
    lastActivatedType = lastActivatedPowerUpType,
    activatingId = activatingId,
)
