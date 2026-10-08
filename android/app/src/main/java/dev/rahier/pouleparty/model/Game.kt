package dev.rahier.pouleparty.model

import dev.rahier.pouleparty.ui.gamelogic.MAX_SHRINK_ITERATIONS
import java.util.UUID
import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUpType
import java.util.Date

/**
 * PP-zone-stored: one pre-generated zone circle, read from
 * `/games/{id}/zone/schedule` (written server-side by `onGameCreated`).
 * Clients render `circles[shrinkIndex]` read-only instead of recomputing
 * the drift on-device, this is what guarantees every device shows the
 * exact same circle. `radiusMeters` is an exact Double (no Int truncation).
 * In `followTheChicken`, `lat`/`lng` hold the start pin but the runtime
 * uses the live chicken GPS for the center and only takes `radiusMeters`.
 */
data class ZoneCircle(
    val order: Int = 0,
    val radiusMeters: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
) {
    @get:Exclude
    val center: Point
        get() = Point.fromLngLat(lng, lat)
}

data class Timing(
    val start: Timestamp = Timestamp(Date(
        ((System.currentTimeMillis() + 7_200_000) / 60_000) * 60_000
    )),
    val end: Timestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
    val headStartMinutes: Double = 2.0,
    val actualStart: Timestamp? = null,
)

data class Zone(
    val center: GeoPoint = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
    val startPin: GeoPoint? = null,
    val finalCenter: GeoPoint? = null,
    val radius: Double = 1500.0,
    val shrinkIntervalMinutes: Double = 5.0,
    val shrinkMetersPerUpdate: Double = 100.0,
    val driftSeed: Long = 0
)

data class ActiveEffects(
    val invisibility: Timestamp? = null,
    val zoneFreeze: Timestamp? = null,
    val radarPing: Timestamp? = null,
    val decoy: Timestamp? = null,
    val jammer: Timestamp? = null
)

data class GamePowerUps(
    val enabled: Boolean = false,
    val enabledTypes: List<String> = PowerUpType.entries.map { it.firestoreValue },
    val activeEffects: ActiveEffects = ActiveEffects()
)

data class Game(
    val id: String = "",
    val name: String = "",
    val maxPlayers: Int = 10,
    val gameMode: String = GameMod.STAY_IN_THE_ZONE.firestoreValue,
    val chickenCanSeeHunters: Boolean = true,
    val foundCode: String = "",
    val status: String = GameStatus.WAITING.firestoreValue,
    val winners: List<Winner> = emptyList(),
    val creatorId: String = "",
    val roles: Map<String, String> = emptyMap(),
    val hasGameMasterPassword: Boolean = false,
    val timing: Timing = Timing(),
    val zone: Zone = Zone(),
    val powerUps: GamePowerUps = GamePowerUps(),
    @get:PropertyName("isAdminCreation")
    @field:PropertyName("isAdminCreation")
    val isAdminCreation: Boolean = false,
    val manualStartEnabled: Boolean = false,
    /**
     * QA only: when true the game was created via the `qa_debug_code`
     * long-press entry. Surfaces the on-map QA debug panel (force end /
     * spawn power-ups) and pairs with a compressed timing setup. Gated
     * server-side by the `debugAdvanceGame` callable, which refuses to act
     * on any game where this is false.
     */
    @get:PropertyName("isDebugGame")
    @field:PropertyName("isDebugGame")
    val isDebugGame: Boolean = false,
    val registrationBatchId: String? = null,
) {

    /** The single chicken's uid, or "" when none is set yet. */
    @get:Exclude
    val chickenId: String
        get() = roles.entries.firstOrNull { it.value == "chicken" }?.key ?: ""

    /** All hunter uids. Order is not significant (derived from a map). */
    @get:Exclude
    val hunterIds: List<String>
        get() = roles.filterValues { it == "hunter" }.keys.toList()

    /** All GameMaster uids. */
    @get:Exclude
    val gameMasterIds: List<String>
        get() = roles.filterValues { it == "gameMaster" }.keys.toList()

    /** This user's role on the game, or null if they have none. */
    @Exclude
    fun role(userId: String): String? =
        if (userId.isEmpty()) null else roles[userId]

    @Exclude
    fun isChicken(userId: String): Boolean = role(userId) == "chicken"

    @Exclude
    fun isHunter(userId: String): Boolean = role(userId) == "hunter"

    @Exclude
    fun isGameMaster(userId: String): Boolean = role(userId) == "gameMaster"

    // ── Power-Up Active Effects ────────────────────────

    @get:Exclude
    val isChickenInvisible: Boolean
        get() = powerUps.activeEffects.invisibility != null && Date().before(powerUps.activeEffects.invisibility.toDate())

    @get:Exclude
    val isZoneFrozen: Boolean
        get() = powerUps.activeEffects.zoneFreeze != null && Date().before(powerUps.activeEffects.zoneFreeze.toDate())

    @get:Exclude
    val isRadarPingActive: Boolean
        get() = powerUps.activeEffects.radarPing != null && Date().before(powerUps.activeEffects.radarPing.toDate())

    @get:Exclude
    val isDecoyActive: Boolean
        get() = powerUps.activeEffects.decoy != null && Date().before(powerUps.activeEffects.decoy.toDate())

    @get:Exclude
    val isJammerActive: Boolean
        get() = powerUps.activeEffects.jammer != null && Date().before(powerUps.activeEffects.jammer.toDate())

    @Exclude
    fun isActive(type: PowerUpType): Boolean = when (type) {
        PowerUpType.INVISIBILITY -> isChickenInvisible
        PowerUpType.ZONE_FREEZE -> isZoneFrozen
        PowerUpType.RADAR_PING -> isRadarPingActive
        PowerUpType.DECOY -> isDecoyActive
        PowerUpType.JAMMER -> isJammerActive
        PowerUpType.ZONE_PREVIEW -> false // instant, no timed window
    }

    // ── Computed Properties ────────────────────────────

    @get:Exclude
    val initialLocation: Point
        get() = Point.fromLngLat(zone.center.longitude, zone.center.latitude)

    @get:Exclude
    val startPinPoint: Point
        get() {
            val pin = zone.startPin ?: zone.center
            return Point.fromLngLat(pin.longitude, pin.latitude)
        }

    @get:Exclude
    val finalLocation: Point?
        get() = zone.finalCenter?.let { Point.fromLngLat(it.longitude, it.latitude) }

    @get:Exclude
    val startDate: Date get() = timing.start.toDate()
    @get:Exclude
    val endDate: Date get() = timing.end.toDate()

    @get:Exclude
    val effectiveStartDate: Date get() = timing.actualStart?.toDate() ?: startDate

    @get:Exclude
    val hunterStartDate: Date get() =
        Date(effectiveStartDate.time + (timing.headStartMinutes * 60 * 1000).toLong())

    val gameCode: String
        get() = id.take(6).uppercase()

    @get:Exclude
    val gameModEnum: GameMod
        get() = GameMod.fromFirestore(gameMode)

    @get:Exclude
    val gameStatusEnum: GameStatus
        get() = GameStatus.fromFirestore(status)

    // ── Game Logic ─────────────────────────────────────

    fun findLastUpdate(): Pair<Date, Int> {
        var lastUpdate = hunterStartDate
        var lastRadius = zone.radius.toInt()

        if (zone.shrinkIntervalMinutes <= 0) {
            return Pair(lastUpdate, lastRadius)
        }

        val now = Date()
        val intervalMs = (zone.shrinkIntervalMinutes * 60 * 1000).toLong()

        // Zone freeze window: skip radius reductions for shrinks inside [freezeStart, freezeEnd)
        val freezeEnd = powerUps.activeEffects.zoneFreeze?.toDate()
        val freezeDuration = (PowerUpType.ZONE_FREEZE.durationSeconds ?: 0) * 1000L
        val freezeStart = freezeEnd?.let { Date(it.time - freezeDuration) }

        var iterations = 0
        while (Date(lastUpdate.time + intervalMs).before(now) && lastRadius > 0 &&
            iterations < MAX_SHRINK_ITERATIONS
        ) {
            iterations += 1
            lastUpdate = Date(lastUpdate.time + intervalMs)
            val isFrozen = freezeStart != null
                && !lastUpdate.before(freezeStart) && lastUpdate.before(freezeEnd)
            if (!isFrozen) {
                lastRadius -= zone.shrinkMetersPerUpdate.toInt()
            }
        }

        lastRadius = maxOf(0, lastRadius)
        val nextUpdate = Date(lastUpdate.time + intervalMs)
        return Pair(nextUpdate, lastRadius)
    }

    // ── Builder Helpers ────────────────────────────────

    fun withStartDate(date: Date): Game = copy(timing = timing.copy(start = Timestamp(date)))
    fun withEndDate(date: Date): Game = copy(timing = timing.copy(end = Timestamp(date)))
    fun withInitialLocation(point: Point): Game = copy(
        zone = zone.copy(center = GeoPoint(point.latitude(), point.longitude()))
    )

    fun withStartPin(point: Point): Game = copy(
        zone = zone.copy(
            startPin = GeoPoint(point.latitude(), point.longitude()),
            center = GeoPoint(point.latitude(), point.longitude()),
        )
    )
    fun withFinalLocation(point: Point?): Game = copy(
        zone = zone.copy(finalCenter = point?.let { GeoPoint(it.latitude(), it.longitude()) })
    )
    fun withChickenHeadStart(minutes: Double): Game = copy(timing = timing.copy(headStartMinutes = minutes))
    fun withChickenCanSeeHunters(value: Boolean): Game = copy(chickenCanSeeHunters = value)

    companion object
}
