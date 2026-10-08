package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import java.util.Date
import java.util.UUID

val Game.Companion.mock: Game
    get() = Game(
        id = UUID.randomUUID().toString(),
        name = "Mock",
        maxPlayers = 10,
        timing = Timing(
            start = Timestamp(Date(System.currentTimeMillis() + 300_000)),
            end = Timestamp(Date(System.currentTimeMillis() + 3_900_000)),
        ),
        zone = Zone(
            center = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
            radius = 1500.0,
            shrinkIntervalMinutes = 5.0,
            shrinkMetersPerUpdate = 100.0,
            driftSeed = 42L,
        ),
        gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue,
        foundCode = "1234",
    )
