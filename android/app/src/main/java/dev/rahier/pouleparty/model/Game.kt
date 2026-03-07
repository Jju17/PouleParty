package dev.rahier.pouleparty.model

import com.mapbox.geojson.Point
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import java.util.Date

data class Game(
    val id: String = "",
    val name: String = "",
    val numberOfPlayers: Int = 10,
    val radiusIntervalUpdate: Double = 5.0, // in minutes
    val startTimestamp: Timestamp = Timestamp(Date(
        ((System.currentTimeMillis() + 300_000) / 60_000) * 60_000
    )),
    val endTimestamp: Timestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
    val initialCoordinates: GeoPoint = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
    val initialRadius: Double = 1500.0,
    val radiusDeclinePerUpdate: Double = 100.0,
    val chickenHeadStartMinutes: Double = 0.0, // In minutes, 0 = no head start
    val gameMod: String = GameMod.STAY_IN_THE_ZONE.firestoreValue,
    val chickenCanSeeHunters: Boolean = false,
    val foundCode: String = "",
    val hunterIds: List<String> = emptyList(),
    val status: String = GameStatus.WAITING.firestoreValue,
    val winners: List<Winner> = emptyList(),
    val creatorId: String = "",
    val driftSeed: Int = 0,
    val powerUpsEnabled: Boolean = false,
    val enabledPowerUpTypes: List<String> = PowerUpType.entries.map { it.firestoreValue },
    val activeInvisibilityUntil: Timestamp? = null,
    val activeZoneFreezeUntil: Timestamp? = null,
    val activeRadarPingUntil: Timestamp? = null
) {
    // ── Power-Up Active Effects ────────────────────────

    @get:Exclude
    val isChickenInvisible: Boolean
        get() = activeInvisibilityUntil != null && Date().before(activeInvisibilityUntil.toDate())

    @get:Exclude
    val isZoneFrozen: Boolean
        get() = activeZoneFreezeUntil != null && Date().before(activeZoneFreezeUntil.toDate())

    @get:Exclude
    val isRadarPingActive: Boolean
        get() = activeRadarPingUntil != null && Date().before(activeRadarPingUntil.toDate())

    // ── Computed Properties ────────────────────────────

    @get:Exclude
    val initialLocation: Point
        get() = Point.fromLngLat(initialCoordinates.longitude, initialCoordinates.latitude)

    val startDate: Date get() = startTimestamp.toDate()
    val endDate: Date get() = endTimestamp.toDate()
    val hunterStartDate: Date get() = Date(startDate.time + (chickenHeadStartMinutes * 60 * 1000).toLong())

    val gameCode: String
        get() = id.take(6).uppercase()

    @get:Exclude
    val gameModEnum: GameMod
        get() = GameMod.fromFirestore(gameMod)

    @get:Exclude
    val gameStatusEnum: GameStatus
        get() = GameStatus.fromFirestore(status)

    // ── Game Logic ─────────────────────────────────────

    fun findLastUpdate(): Pair<Date, Int> {
        var lastUpdate = hunterStartDate
        var lastRadius = initialRadius.toInt()

        if (radiusIntervalUpdate <= 0) {
            return Pair(lastUpdate, lastRadius)
        }

        val now = Date()
        val intervalMs = (radiusIntervalUpdate * 60 * 1000).toLong()

        while (Date(lastUpdate.time + intervalMs).before(now)) {
            lastUpdate = Date(lastUpdate.time + intervalMs)
            lastRadius -= radiusDeclinePerUpdate.toInt()
        }

        lastRadius = maxOf(0, lastRadius)
        val nextUpdate = Date(lastUpdate.time + intervalMs)
        return Pair(nextUpdate, lastRadius)
    }

    // ── Builder Helpers ────────────────────────────────

    fun withStartDate(date: Date): Game = copy(startTimestamp = Timestamp(date))
    fun withEndDate(date: Date): Game = copy(endTimestamp = Timestamp(date))
    fun withInitialLocation(point: Point): Game = copy(
        initialCoordinates = GeoPoint(point.latitude(), point.longitude())
    )
    fun withChickenHeadStart(minutes: Double): Game = copy(chickenHeadStartMinutes = minutes)
    fun withChickenCanSeeHunters(value: Boolean): Game = copy(chickenCanSeeHunters = value)

    companion object {
        fun generateFoundCode(): String = "%04d".format((0..9999).random())

        val mock = Game(
            id = java.util.UUID.randomUUID().toString(),
            name = "Mock",
            numberOfPlayers = 10,
            radiusIntervalUpdate = 5.0,
            startTimestamp = Timestamp(Date(System.currentTimeMillis() + 300_000)),
            endTimestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
            initialCoordinates = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
            initialRadius = 1500.0,
            radiusDeclinePerUpdate = 100.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            foundCode = "1234",
            driftSeed = 42
        )
    }
}
