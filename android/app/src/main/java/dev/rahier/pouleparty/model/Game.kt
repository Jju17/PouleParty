package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUpType
import java.util.Date

data class Timing(
    val start: Timestamp = Timestamp(Date(
        ((System.currentTimeMillis() + 7_200_000) / 60_000) * 60_000
    )),
    val end: Timestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
    val headStartMinutes: Double = 0.0
)

data class Zone(
    /**
     * Initial geometric center of the shrinking zone disc. PP-13
     * recomputes this on the recap step so the first circle
     * contains BOTH `startPin` and `finalCenter` without being
     * centered on either.
     */
    val center: GeoPoint = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
    /**
     * PP-11 / PP-13: user-placed start pin. Decoupled from
     * `center` so the recap can pick a non-centered initial disc
     * while keeping the visual start marker exactly where the
     * chicken dropped it. `null` for legacy games written before
     * the split — readers fall back to `center` (see
     * `Game.startPinPoint`).
     */
    val startPin: GeoPoint? = null,
    val finalCenter: GeoPoint? = null,
    val radius: Double = 1500.0,
    val shrinkIntervalMinutes: Double = 5.0,
    val shrinkMetersPerUpdate: Double = 100.0,
    val driftSeed: Int = 0
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
    /**
     * PP-35: defaults shipped to players are intentionally narrow. Only
     * `zoneFreeze` (chicken) and `zonePreview` (hunter) ship enabled, so
     * a brand-new party never spawns the noisy positional power-ups.
     * The other types stay in the enum + spawner so we can flip them
     * back on per-game from Firestore without a release.
     */
    val enabledTypes: List<String> = listOf(
        PowerUpType.ZONE_FREEZE.firestoreValue,
        PowerUpType.ZONE_PREVIEW.firestoreValue,
    ),
    val activeEffects: ActiveEffects = ActiveEffects()
)

data class Game(
    val id: String = "",
    val name: String = "",
    val maxPlayers: Int = 10,
    val gameMode: String = GameMod.STAY_IN_THE_ZONE.firestoreValue,
    val chickenCanSeeHunters: Boolean = false,
    val foundCode: String = "",
    val hunterIds: List<String> = emptyList(),
    val gameMasterIds: List<String> = emptyList(),
    val status: String = GameStatus.WAITING.firestoreValue,
    val winners: List<Winner> = emptyList(),
    val creatorId: String = "",
    /**
     * The player who runs and hides. Set to `creatorId` at game creation,
     * can be re-designated to any registered hunter by a GameMaster while
     * `status == waiting` (PP-26). Distinct from `creatorId`, which stays
     * the game's admin owner.
     */
    val chickenId: String = "",
    /**
     * True when the creator has enabled the GameMaster role and set a
     * password. The actual password lives in
     * `/games/{gameId}/private/security` (admin-SDK only, PP-23) — this
     * flag is the public signal so JoinFlow can show / hide the "Join
     * as GameMaster" CTA without leaking the password (PP-70).
     */
    val hasGameMasterPassword: Boolean = false,
    val timing: Timing = Timing(),
    val zone: Zone = Zone(),
    val powerUps: GamePowerUps = GamePowerUps(),
    val lastHeartbeat: Timestamp? = null,
    /**
     * Lifts the `maxPlayers` cap from 5 to 500 for parties created via the
     * admin code (`jujurahier`). Garde-fou client only — see PP-45 and the
     * firestore.rules `allow create` clause.
     */
    val isAdminCreation: Boolean = false
) {
    // ── Chicken Role (PP-26) ───────────────────────────

    /**
     * True when [userId] is the player designated as the chicken
     * (PP-26). Use this instead of `creatorId == userId` everywhere
     * the question is "who runs and hides".
     */
    @Exclude
    fun isChicken(userId: String): Boolean =
        userId.isNotEmpty() && chickenId == userId

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

    /**
     * Whether the timed effect associated with [type] is currently active on
     * the game doc. Used to gate activation — a second activation overwrites
     * `powerUps.activeEffects.<field>`, shifting the freeze window and
     * desyncing `findLastUpdate` between Chicken + Hunter (a 1.11.2
     * live-test report: the Hunter kept seeing the zone frozen after the
     * Chicken's game had already ended). Blocking the second activation at
     * the UI + ViewModel layer prevents that entirely. Keep in lockstep
     * with iOS `Game.isActive(effectOf:)`.
     */
    @Exclude
    fun isActive(type: PowerUpType): Boolean = when (type) {
        PowerUpType.INVISIBILITY -> isChickenInvisible
        PowerUpType.ZONE_FREEZE -> isZoneFrozen
        PowerUpType.RADAR_PING -> isRadarPingActive
        PowerUpType.DECOY -> isDecoyActive
        PowerUpType.JAMMER -> isJammerActive
        PowerUpType.ZONE_PREVIEW -> false // instant, no timed window
    }

    /** Returns true if the chicken's heartbeat is stale (>60s old), indicating disconnect. */
    @get:Exclude
    val isChickenDisconnected: Boolean
        get() {
            val heartbeat = lastHeartbeat ?: return false
            return Date().time - heartbeat.toDate().time > 60_000
        }

    // ── Computed Properties ────────────────────────────

    @get:Exclude
    val initialLocation: Point
        get() = Point.fromLngLat(zone.center.longitude, zone.center.latitude)

    /** PP-11 / PP-13 — user-placed start pin. Falls back to
     *  `zone.center` for legacy games written before the `startPin`
     *  field existed, so existing readers keep working. */
    @get:Exclude
    val startPinPoint: Point
        get() {
            val pin = zone.startPin ?: zone.center
            return Point.fromLngLat(pin.longitude, pin.latitude)
        }

    @get:Exclude
    val finalLocation: Point?
        get() = zone.finalCenter?.let { Point.fromLngLat(it.longitude, it.latitude) }

    val startDate: Date get() = timing.start.toDate()
    val endDate: Date get() = timing.end.toDate()
    val hunterStartDate: Date get() = Date(startDate.time + (timing.headStartMinutes * 60 * 1000).toLong())

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

        while (Date(lastUpdate.time + intervalMs).before(now)) {
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

    /** PP-11 / PP-13 — write the user-placed start pin AND mirror it
     *  into `zone.center` so the PP-11 preview circle stays anchored
     *  on the pin until PP-13 picks a non-centered computed center. */
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

    companion object {
        fun generateFoundCode(): String = "%04d".format((0..9999).random())

        val mock get() = Game(
            id = java.util.UUID.randomUUID().toString(),
            name = "Mock",
            maxPlayers = 10,
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() + 300_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_900_000))
            ),
            zone = Zone(
                center = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
                radius = 1500.0,
                shrinkIntervalMinutes = 5.0,
                shrinkMetersPerUpdate = 100.0,
                driftSeed = 42
            ),
            gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            foundCode = "1234"
        )
    }
}
