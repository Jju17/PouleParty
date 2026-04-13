package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import java.util.Date

data class Timing(
    val start: Timestamp = Timestamp(Date(
        ((System.currentTimeMillis() + 7_200_000) / 60_000) * 60_000
    )),
    val end: Timestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
    val headStartMinutes: Double = 0.0
)

data class Zone(
    val center: GeoPoint = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
    val finalCenter: GeoPoint? = null,
    val radius: Double = 1500.0,
    val shrinkIntervalMinutes: Double = 5.0,
    val shrinkMetersPerUpdate: Double = 100.0,
    val driftSeed: Int = 0
)

data class Pricing(
    val model: String = PricingModel.FREE.firestoreValue,
    val pricePerPlayer: Int = 0,
    val deposit: Int = 0,
    val commission: Double = 15.0
)

data class GameRegistration(
    val required: Boolean = false,
    val closesMinutesBefore: Int? = 15
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
    val chickenCanSeeHunters: Boolean = false,
    val foundCode: String = "",
    val hunterIds: List<String> = emptyList(),
    val status: String = GameStatus.WAITING.firestoreValue,
    val winners: List<Winner> = emptyList(),
    val creatorId: String = "",
    val timing: Timing = Timing(),
    val zone: Zone = Zone(),
    val pricing: Pricing = Pricing(),
    val registration: GameRegistration = GameRegistration(),
    val powerUps: GamePowerUps = GamePowerUps(),
    val lastHeartbeat: Timestamp? = null
) {
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

    @get:Exclude
    val pricingModelEnum: PricingModel
        get() = PricingModel.fromFirestore(pricing.model)

    @get:Exclude
    val isPaid: Boolean
        get() = pricingModelEnum != PricingModel.FREE

    @get:Exclude
    val registrationDeadline: Date?
        get() {
            val minutes = registration.closesMinutesBefore ?: return null
            return Date(startDate.time - minutes * 60 * 1000L)
        }

    @get:Exclude
    val isRegistrationClosed: Boolean
        get() {
            if (!registration.required) return false
            val deadline = registrationDeadline ?: return false
            return Date().after(deadline) || Date() == deadline
        }

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
