package dev.rahier.pouleparty.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.util.Date

data class Game(
    val id: String = "",
    val name: String = "",
    val numberOfPlayers: Int = 10,
    val radiusIntervalUpdate: Double = 5.0, // in minutes
    val startTimestamp: Timestamp = Timestamp(Date(System.currentTimeMillis() + 300_000)),
    val endTimestamp: Timestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
    val initialCoordinates: GeoPoint = GeoPoint(50.8466, 4.3528),
    val initialRadius: Double = 1500.0,
    val radiusDeclinePerUpdate: Double = 100.0,
    val chickenHeadStartMinutes: Double = 0.0, // In minutes, 0 = no head start
    val gameMod: String = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
    val foundCode: String = "",
    val hunterIds: List<String> = emptyList(),
    val status: String = GameStatus.WAITING.firestoreValue,
    val winners: List<Winner> = emptyList()
) {
    /** Computed: CLLocationCoordinate2D equivalent */
    val initialLocation: LatLng
        get() = LatLng(initialCoordinates.latitude, initialCoordinates.longitude)

    val startDate: Date get() = startTimestamp.toDate()
    val endDate: Date get() = endTimestamp.toDate()
    val hunterStartDate: Date get() = Date(startDate.time + (chickenHeadStartMinutes * 60 * 1000).toLong())

    /** Game code = first 6 chars of ID uppercased (matches iOS) */
    val gameCode: String
        get() = id.take(6).uppercase()

    /** Parsed game mod enum */
    val gameModEnum: GameMod
        get() = GameMod.fromFirestore(gameMod)

    /** Parsed game status enum */
    val gameStatusEnum: GameStatus
        get() = GameStatus.fromFirestore(status)

    /**
     * Walk forward from startDate in steps of radiusIntervalUpdate
     * until we pass "now". Returns the next update date and current radius.
     */
    fun findLastUpdate(): Pair<Date, Int> {
        var lastUpdate = hunterStartDate
        var lastRadius = initialRadius.toInt()
        val now = Date()
        val intervalMs = (radiusIntervalUpdate * 60 * 1000).toLong()

        while (Date(lastUpdate.time + intervalMs).before(now)) {
            lastUpdate = Date(lastUpdate.time + intervalMs)
            lastRadius -= radiusDeclinePerUpdate.toInt()
        }

        val nextUpdate = Date(lastUpdate.time + intervalMs)
        return Pair(nextUpdate, lastRadius)
    }

    fun withStartDate(date: Date): Game = copy(startTimestamp = Timestamp(date))
    fun withEndDate(date: Date): Game = copy(endTimestamp = Timestamp(date))
    fun withInitialLocation(latLng: LatLng): Game = copy(
        initialCoordinates = GeoPoint(latLng.latitude, latLng.longitude)
    )
    fun withChickenHeadStart(minutes: Double): Game = copy(chickenHeadStartMinutes = minutes)

    companion object {
        fun generateFoundCode(): String = "%04d".format((0..9999).random())

        val mock = Game(
            id = java.util.UUID.randomUUID().toString(),
            name = "Mock",
            numberOfPlayers = 10,
            radiusIntervalUpdate = 5.0,
            startTimestamp = Timestamp(Date(System.currentTimeMillis() + 300_000)),
            endTimestamp = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
            initialCoordinates = GeoPoint(50.8466, 4.3528),
            initialRadius = 1500.0,
            radiusDeclinePerUpdate = 100.0,
            gameMod = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
            foundCode = "1234"
        )
    }
}

enum class GameMod(val firestoreValue: String, val title: String) {
    FOLLOW_THE_CHICKEN("followTheChicken", "Follow the chicken \uD83D\uDC14"),
    STAY_IN_THE_ZONE("stayInTheZone", "Stay in tha zone \uD83D\uDCCD"),
    MUTUAL_TRACKING("mutualTracking", "Mutual tracking \uD83D\uDC40");

    companion object {
        fun fromFirestore(value: String): GameMod =
            entries.firstOrNull { it.firestoreValue == value } ?: FOLLOW_THE_CHICKEN
    }
}

enum class GameStatus(val firestoreValue: String) {
    WAITING("waiting"),
    IN_PROGRESS("inProgress"),
    DONE("done");

    companion object {
        fun fromFirestore(value: String): GameStatus =
            entries.firstOrNull { it.firestoreValue == value } ?: WAITING
    }
}
