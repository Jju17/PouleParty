package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.powerups.model.PowerUpType
import java.util.Date

/**
 * PP-zone-stored: one pre-generated zone circle, read from
 * `/games/{id}/zone/schedule` (written server-side by `onGameCreated`).
 * Clients render `circles[shrinkIndex]` read-only instead of recomputing
 * the drift on-device — this is what guarantees every device shows the
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
    /**
     * PP-71: server-set timestamp of the effective launch when
     * `manualStartEnabled == true`. `null` until the LAUNCH callable
     * fires; read by `hunterStartDate` (and the recomputed `end`)
     * to anchor every downstream timer on the actual start.
     */
    val actualStart: Timestamp? = null,
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
    /**
     * PP-107: single source of truth for membership. Maps each
     * participant's uid to their role (`"chicken"` | `"hunter"` |
     * `"gameMaster"`). A uid has exactly one role, so a "ghost" (no role)
     * or a double-role is impossible by construction. Written server-side
     * only (the role callables via admin SDK); `creatorId` stays as
     * ownership and also appears here with a role. Read through the
     * derived `chickenId` / `hunterIds` / `gameMasterIds` accessors below
     * — never mutate `roles` from a client.
     */
    val roles: Map<String, String> = emptyMap(),
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
    /**
     * Lifts the `maxPlayers` cap from 5 to 500 for parties created via the
     * admin code (`jujurahier`). Garde-fou client only — see PP-45 and the
     * firestore.rules `allow create` clause.
     */
    val isAdminCreation: Boolean = false,
    /**
     * PP-71: when true, the game waits for an explicit LAUNCH tap from
     * the chicken or a GameMaster at `timing.start` instead of starting
     * automatically.
     */
    val manualStartEnabled: Boolean = false,
    /**
     * QA only: when true the game was created via the `qa_debug_code`
     * long-press entry. Surfaces the on-map QA debug panel (force end /
     * spawn power-ups) and pairs with a compressed timing setup. Gated
     * server-side by the `debugAdvanceGame` callable, which refuses to act
     * on any game where this is false.
     */
    val isDebugGame: Boolean = false,
    /**
     * PP-52: when set, this game is linked to a batch of pre-paid web
     * registrations (`/eventRegistrations`). The JoinFlow then requires the
     * unique registration code (validated + single-use-claimed server-side via
     * `validateRegistrationCode`) before a hunter can join. Null for every
     * normal free game, which join with the gameCode alone.
     */
    val registrationBatchId: String? = null,
) {
    // ── Roles (PP-107) ─────────────────────────────────
    // `roles` is the stored single source of truth (uid -> role string).
    // These derived accessors keep every read site working unchanged while
    // the doc holds one clean map instead of three sprawled id fields.

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

    /**
     * True when [userId] is the player designated as the chicken
     * (PP-26 / PP-107). Use this instead of `creatorId == userId`
     * everywhere the question is "who runs and hides".
     */
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

    /**
     * PP-71: post-launch this is the server-stamped real start; before
     * the launch (or in auto-start mode) it falls through to the
     * planned `start`. Every downstream timer must read this instead
     * of `startDate` to stay in sync with the recomputed `end`.
     */
    @get:Exclude
    val effectiveStartDate: Date get() = timing.actualStart?.toDate() ?: startDate

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
