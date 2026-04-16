package dev.rahier.pouleparty.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.data.AnalyticsRepository
import dev.rahier.pouleparty.data.FirestoreRepository
import dev.rahier.pouleparty.data.LocationRepository
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.PowerUp
import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.chickenconfig.powerUpEmoji
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Abstract base ViewModel for map screens (Chicken and Hunter).
 * Extracts duplicated logic shared between [ChickenMapViewModel] and [HunterMapViewModel].
 *
 * Since the two VMs use different UiState types, shared methods accept lambdas
 * for state reads/writes rather than accessing `_uiState` directly.
 */
abstract class BaseMapViewModel(
    protected val firestoreRepository: FirestoreRepository,
    protected val locationRepository: LocationRepository,
    protected val analyticsRepository: AnalyticsRepository,
    protected val auth: FirebaseAuth
) : ViewModel() {

    protected val streamJobs = mutableListOf<Job>()
    protected var notificationJob: Job? = null
    private val collectingPowerUpIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** The game document ID — provided by SavedStateHandle in each subclass. */
    abstract val gameId: String

    /** The current player's ID (userId for chicken, hunterId for hunter). */
    protected abstract val playerId: String

    /** The current player's role as a string ("chicken" or "hunter") for analytics. */
    protected abstract val analyticsRole: String

    /** Log tag for power-up collection errors. */
    protected abstract val logTag: String

    /** Current player location, or null if unknown. Read by [checkPowerUpProximity]. */
    protected abstract val currentUserLocation: Point?

    /** Power-ups currently visible to this player. Read by [checkPowerUpProximity]. */
    protected abstract val currentAvailablePowerUps: List<PowerUp>

    /** Shows a power-up notification in this VM's UiState. */
    protected abstract fun notifyPowerUp(message: String, type: PowerUpType?)

    // ── Shared helpers ───────────────────────────────────

    /**
     * Cancels all tracked stream jobs and clears the list.
     */
    protected fun cancelStreams() {
        streamJobs.forEach { it.cancel() }
        streamJobs.clear()
    }

    /**
     * Shows a power-up notification for [POWER_UP_NOTIFICATION_MS] then clears it.
     *
     * @param message    The notification text.
     * @param type       Optional power-up type for icon display.
     * @param updateState Callback to push notification into the VM-specific UiState.
     *                    Called with `(message, type)` to show, then `(null, null)` to clear.
     */
    protected fun showPowerUpNotification(
        message: String,
        type: PowerUpType? = null,
        updateState: (String?, PowerUpType?) -> Unit
    ) {
        notificationJob?.cancel()
        notificationJob = viewModelScope.launch {
            updateState(message, type)
            delay(AppConstants.POWER_UP_NOTIFICATION_MS)
            updateState(null, null)
        }
    }

    /**
     * Checks whether the player is within collection radius of any available power-up
     * and collects it if so. Reads state through the abstract accessors defined on
     * this class so subclasses never need to re-wire the arguments.
     */
    protected fun checkPowerUpProximity() {
        val userLoc = currentUserLocation ?: return
        for (powerUp in currentAvailablePowerUps) {
            if (powerUp.id in collectingPowerUpIds) continue
            val results = FloatArray(1)
            Location.distanceBetween(
                userLoc.latitude(), userLoc.longitude(),
                powerUp.location.latitude, powerUp.location.longitude,
                results
            )
            if (results[0] <= AppConstants.POWER_UP_COLLECTION_RADIUS_METERS) {
                collectingPowerUpIds.add(powerUp.id)
                viewModelScope.launch {
                    try {
                        firestoreRepository.collectPowerUp(gameId, powerUp.id, playerId)
                        analyticsRepository.powerUpCollected(powerUp.type, analyticsRole)
                        notifyPowerUp("Collected: ${powerUp.typeEnum.title}!", powerUp.typeEnum)
                    } catch (e: Exception) {
                        Log.e(logTag, "Failed to collect power-up", e)
                        notifyPowerUp("Failed to collect power-up", null)
                    } finally {
                        collectingPowerUpIds.remove(powerUp.id)
                    }
                }
            }
        }
    }

    /**
     * Shows a "code copied" feedback for [CODE_COPY_FEEDBACK_MS].
     *
     * @param updateState Called with `true` to show feedback, then `false` to clear.
     */
    protected fun handleCodeCopied(updateState: (Boolean) -> Unit) {
        updateState(true)
        viewModelScope.launch {
            delay(AppConstants.CODE_COPY_FEEDBACK_MS)
            updateState(false)
        }
    }

    /**
     * Detects when an opponent activates a cross-player power-up (e.g. invisibility, zone freeze)
     * by comparing old and new game snapshots.
     *
     * @param oldGame         The previous game snapshot.
     * @param newGame         The updated game snapshot.
     * @param onNotification  Callback to show a notification with the power-up message and type.
     */
    protected fun detectCrossPlayerPowerUp(
        oldGame: Game,
        newGame: Game,
        onNotification: (String, PowerUpType?) -> Unit
    ) {
        data class Check(val old: Timestamp?, val new: Timestamp?, val type: PowerUpType)
        val checks = listOf(
            Check(oldGame.powerUps.activeEffects.invisibility, newGame.powerUps.activeEffects.invisibility, PowerUpType.INVISIBILITY),
            Check(oldGame.powerUps.activeEffects.zoneFreeze, newGame.powerUps.activeEffects.zoneFreeze, PowerUpType.ZONE_FREEZE),
            Check(oldGame.powerUps.activeEffects.radarPing, newGame.powerUps.activeEffects.radarPing, PowerUpType.RADAR_PING),
            Check(oldGame.powerUps.activeEffects.decoy, newGame.powerUps.activeEffects.decoy, PowerUpType.DECOY),
            Check(oldGame.powerUps.activeEffects.jammer, newGame.powerUps.activeEffects.jammer, PowerUpType.JAMMER),
        )
        val now = java.util.Date()
        for (check in checks) {
            if (check.new != null && now.before(check.new.toDate()) && check.old?.toDate() != check.new.toDate()) {
                onNotification("${powerUpEmoji(check.type)} ${check.type.title} activated!", check.type)
                return
            }
        }
    }
}
