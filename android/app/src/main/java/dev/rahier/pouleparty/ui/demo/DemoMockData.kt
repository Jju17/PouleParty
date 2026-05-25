package dev.rahier.pouleparty.ui.demo

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.ActiveEffects
import dev.rahier.pouleparty.model.ChickenLocation
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GamePowerUps
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.HunterLocation
import dev.rahier.pouleparty.model.Timing
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.powerups.model.PowerUp
import dev.rahier.pouleparty.powerups.model.PowerUpType
import java.util.Date

object DemoMockData {

    const val GAME_ID: String = "DEMO0000000000000000000000000000"
    const val CHICKEN_ID: String = "demo-chicken-uid"
    const val HUNTER_1_ID: String = "demo-hunter-1"
    const val HUNTER_2_ID: String = "demo-hunter-2"
    const val HUNTER_3_ID: String = "demo-hunter-3"
    const val GAME_MASTER_ID: String = "demo-gm-1"

    private const val ZONE_LAT: Double = 50.8266
    private const val ZONE_LON: Double = 4.3528

    val zoneCenterPoint: Point = Point.fromLngLat(ZONE_LON, ZONE_LAT)

    private fun fiveMinutesAgo(): Timestamp =
        Timestamp(Date(System.currentTimeMillis() - 5 * 60 * 1000L))

    private fun in24Hours(): Timestamp =
        Timestamp(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000L))

    fun activeGame(): Game = Game(
        id = GAME_ID,
        name = "Demo party",
        maxPlayers = 10,
        gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
        chickenCanSeeHunters = true,
        foundCode = "0000",
        hunterIds = listOf(HUNTER_1_ID, HUNTER_2_ID, HUNTER_3_ID, CHICKEN_ID),
        gameMasterIds = listOf(GAME_MASTER_ID),
        status = GameStatus.IN_PROGRESS.firestoreValue,
        winners = emptyList(),
        creatorId = CHICKEN_ID,
        chickenId = CHICKEN_ID,
        hasGameMasterPassword = true,
        timing = Timing(
            start = fiveMinutesAgo(),
            end = in24Hours(),
            headStartMinutes = 0.0,
        ),
        zone = Zone(
            center = GeoPoint(ZONE_LAT, ZONE_LON),
            startPin = GeoPoint(ZONE_LAT, ZONE_LON),
            finalCenter = GeoPoint(ZONE_LAT + 0.002, ZONE_LON + 0.002),
            radius = 800.0,
            shrinkIntervalMinutes = 5.0,
            shrinkMetersPerUpdate = 100.0,
            driftSeed = 4242,
        ),
        powerUps = GamePowerUps(
            enabled = true,
            enabledTypes = listOf(
                PowerUpType.ZONE_FREEZE.firestoreValue,
                PowerUpType.ZONE_PREVIEW.firestoreValue,
            ),
            activeEffects = ActiveEffects(),
        ),
        lastHeartbeat = Timestamp.now(),
    )

    fun victoryGame(): Game = activeGame().copy(
        status = GameStatus.DONE.firestoreValue,
        winners = listOf(
            Winner(hunterId = HUNTER_1_ID, hunterName = "Red Foxes", timestamp = Timestamp.now()),
        ),
    )

    fun chickenLocation(): ChickenLocation = ChickenLocation(
        location = GeoPoint(ZONE_LAT, ZONE_LON),
        timestamp = Timestamp.now(),
        invisible = false,
    )

    val chickenPoint: Point = zoneCenterPoint

    data class DemoHunter(
        val id: String,
        val teamName: String,
        val point: Point,
    )

    val hunters: List<DemoHunter> = listOf(
        DemoHunter(
            id = HUNTER_1_ID,
            teamName = "Red Foxes",
            point = Point.fromLngLat(ZONE_LON + 0.0014, ZONE_LAT + 0.0009),
        ),
        DemoHunter(
            id = HUNTER_2_ID,
            teamName = "Blue Jays",
            point = Point.fromLngLat(ZONE_LON - 0.0017, ZONE_LAT + 0.0011),
        ),
        DemoHunter(
            id = HUNTER_3_ID,
            teamName = "Green Wolves",
            point = Point.fromLngLat(ZONE_LON + 0.0006, ZONE_LAT - 0.0018),
        ),
    )

    fun hunterLocations(): List<HunterLocation> = hunters.map { hunter ->
        HunterLocation(
            hunterId = hunter.id,
            location = GeoPoint(hunter.point.latitude(), hunter.point.longitude()),
            timestamp = Timestamp.now(),
        )
    }

    fun powerUps(): List<PowerUp> = listOf(
        PowerUp(
            id = "demo-pu-1",
            type = PowerUpType.ZONE_FREEZE.firestoreValue,
            location = GeoPoint(ZONE_LAT + 0.0010, ZONE_LON + 0.0005),
            spawnedAt = Timestamp.now(),
        ),
        PowerUp(
            id = "demo-pu-2",
            type = PowerUpType.ZONE_PREVIEW.firestoreValue,
            location = GeoPoint(ZONE_LAT - 0.0008, ZONE_LON + 0.0012),
            spawnedAt = Timestamp.now(),
        ),
        PowerUp(
            id = "demo-pu-3",
            type = PowerUpType.ZONE_FREEZE.firestoreValue,
            location = GeoPoint(ZONE_LAT + 0.0006, ZONE_LON - 0.0014),
            spawnedAt = Timestamp.now(),
        ),
        PowerUp(
            id = "demo-pu-4",
            type = PowerUpType.ZONE_PREVIEW.firestoreValue,
            location = GeoPoint(ZONE_LAT - 0.0013, ZONE_LON - 0.0007),
            spawnedAt = Timestamp.now(),
        ),
        PowerUp(
            id = "demo-pu-5",
            type = PowerUpType.ZONE_FREEZE.firestoreValue,
            location = GeoPoint(ZONE_LAT + 0.0001, ZONE_LON + 0.0018),
            spawnedAt = Timestamp.now(),
        ),
    )
}
