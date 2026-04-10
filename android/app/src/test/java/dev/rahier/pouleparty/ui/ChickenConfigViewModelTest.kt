package dev.rahier.pouleparty.ui

import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.NORMAL_MODE_FIXED_INTERVAL
import dev.rahier.pouleparty.model.NORMAL_MODE_MINIMUM_RADIUS
import dev.rahier.pouleparty.model.Zone
import dev.rahier.pouleparty.model.calculateNormalModeSettings
import dev.rahier.pouleparty.ui.chickenconfig.ChickenConfigUiState
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class ChickenConfigViewModelTest {

    @Test
    fun `initial state has game with correct defaults`() {
        val state = ChickenConfigUiState()
        assertEquals(Game.mock.zone.radius, state.game.zone.radius, 0.01)
        assertFalse(state.codeCopied)
        assertFalse(state.showAlert)
    }

    @Test
    fun `game code is 6 characters`() {
        val game = Game(id = "abcdef-1234")
        assertEquals(6, game.gameCode.length)
    }

    @Test
    fun `updating game mod changes state`() {
        val game = Game(id = "test", gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue)
        val updated = game.copy(gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue)
        assertEquals(GameMod.STAY_IN_THE_ZONE, updated.gameModEnum)
    }

    @Test
    fun `updating radius interval changes state`() {
        val game = Game(id = "test", zone = Zone(shrinkIntervalMinutes = 5.0))
        val updated = game.copy(zone = game.zone.copy(shrinkIntervalMinutes = 10.0))
        assertEquals(10.0, updated.zone.shrinkIntervalMinutes, 0.01)
    }

    @Test
    fun `updating radius decline changes state`() {
        val game = Game(id = "test", zone = Zone(shrinkMetersPerUpdate = 100.0))
        val updated = game.copy(zone = game.zone.copy(shrinkMetersPerUpdate = 200.0))
        assertEquals(200.0, updated.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `updating start date creates new game`() {
        val game = Game(id = "test")
        val newDate = Date(System.currentTimeMillis() + 1_000_000)
        val updated = game.withStartDate(newDate)

        assertNotSame(game, updated)
        assertTrue(Math.abs(updated.startDate.time - newDate.time) < 1000)
    }

    @Test
    fun `alert state can be shown and dismissed`() {
        var state = ChickenConfigUiState()
        assertFalse(state.showAlert)

        state = state.copy(showAlert = true, alertMessage = "Error")
        assertTrue(state.showAlert)
        assertEquals("Error", state.alertMessage)

        state = state.copy(showAlert = false)
        assertFalse(state.showAlert)
    }

    // MARK: Normal mode calculation — basic

    @Test
    fun `calculateNormalModeSettings always returns fixed interval of 5`() {
        val durations = listOf(5.0, 30.0, 60.0, 90.0, 120.0, 150.0, 180.0)
        for (duration in durations) {
            val (interval, _) = calculateNormalModeSettings(1500.0, duration)
            assertEquals("interval should be 5 for duration $duration", NORMAL_MODE_FIXED_INTERVAL, interval, 0.01)
        }
    }

    @Test
    fun `calculateNormalModeSettings correct decline for 2h`() {
        val (_, decline) = calculateNormalModeSettings(1500.0, 120.0)
        val expected = (1500.0 - 100.0) / 24.0
        assertEquals(expected, decline, 0.01)
    }

    @Test
    fun `calculateNormalModeSettings correct decline for 1h`() {
        val (_, decline) = calculateNormalModeSettings(1500.0, 60.0)
        val expected = (1500.0 - 100.0) / 12.0
        assertEquals(expected, decline, 0.01)
    }

    // MARK: All picker durations reach 100m

    @Test
    fun `all picker durations reach 100m`() {
        val durations = listOf(60.0, 90.0, 120.0, 150.0, 180.0)
        for (duration in durations) {
            val radius = 1500.0
            val (interval, decline) = calculateNormalModeSettings(radius, duration)
            val finalRadius = radius - (decline * (duration / interval))
            assertEquals("duration $duration should reach 100m", NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01)
        }
    }

    // MARK: All radius slider values reach 100m

    @Test
    fun `all radius slider values reach 100m`() {
        // Slider: 500 to 2000 step 100
        var radius = 500.0
        while (radius <= 2000.0) {
            val (interval, decline) = calculateNormalModeSettings(radius, 120.0)
            val finalRadius = radius - (decline * (120.0 / interval))
            assertEquals("radius $radius should reach 100m", NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01)
            radius += 100.0
        }
    }

    // MARK: All combinations of radius x duration

    @Test
    fun `all radius and duration combinations reach 100m`() {
        val durations = listOf(60.0, 90.0, 120.0, 150.0, 180.0)
        var radius = 500.0
        while (radius <= 2000.0) {
            for (duration in durations) {
                val (interval, decline) = calculateNormalModeSettings(radius, duration)
                val finalRadius = radius - (decline * (duration / interval))
                assertEquals(
                    "radius=$radius, duration=$duration should reach 100m",
                    NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01
                )
            }
            radius += 100.0
        }
    }

    // MARK: Decline never produces negative final radius

    @Test
    fun `decline never produces negative final radius`() {
        val durations = listOf(5.0, 30.0, 60.0, 90.0, 120.0, 150.0, 180.0)
        var radius = 100.0
        while (radius <= 2000.0) {
            for (duration in durations) {
                val (interval, decline) = calculateNormalModeSettings(radius, duration)
                val finalRadius = radius - (decline * (duration / interval))
                assertTrue(
                    "radius=$radius, duration=$duration gave negative final radius $finalRadius",
                    finalRadius >= -0.01
                )
            }
            radius += 100.0
        }
    }

    // MARK: Edge cases — zero and negative duration

    @Test
    fun `zero duration returns zero decline`() {
        val (interval, decline) = calculateNormalModeSettings(1500.0, 0.0)
        assertEquals(NORMAL_MODE_FIXED_INTERVAL, interval, 0.01)
        assertEquals(0.0, decline, 0.01)
    }

    @Test
    fun `negative duration returns zero decline`() {
        val (interval, decline) = calculateNormalModeSettings(1500.0, -10.0)
        assertEquals(NORMAL_MODE_FIXED_INTERVAL, interval, 0.01)
        assertEquals(0.0, decline, 0.01)
    }

    // MARK: Edge cases — minimal duration (single shrink)

    @Test
    fun `five minute duration produces single shrink to 100m`() {
        val (interval, decline) = calculateNormalModeSettings(1500.0, 5.0)
        assertEquals(5.0, interval, 0.01)
        // 1 shrink: 1500 - decline = 100
        val finalRadius = 1500.0 - decline
        assertEquals(NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01)
    }

    // MARK: Edge cases — radius at or below minimum

    @Test
    fun `radius exactly at minimum gives zero decline`() {
        val (_, decline) = calculateNormalModeSettings(NORMAL_MODE_MINIMUM_RADIUS, 120.0)
        assertEquals(0.0, decline, 0.01)
    }

    @Test
    fun `radius below minimum gives zero decline`() {
        val (_, decline) = calculateNormalModeSettings(50.0, 120.0)
        assertEquals(0.0, decline, 0.01)
    }

    @Test
    fun `radius just above minimum`() {
        val (interval, decline) = calculateNormalModeSettings(101.0, 120.0)
        assertEquals(5.0, interval, 0.01)
        val finalRadius = 101.0 - (decline * (120.0 / interval))
        assertEquals(NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01)
    }

    // MARK: Edge cases — large values

    @Test
    fun `very large radius reaches 100m`() {
        val radius = 10000.0
        val (interval, decline) = calculateNormalModeSettings(radius, 120.0)
        val finalRadius = radius - (decline * (120.0 / interval))
        assertEquals(NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01)
        assertTrue(decline > 0)
    }

    @Test
    fun `very long duration produces small decline`() {
        val (interval, decline) = calculateNormalModeSettings(1500.0, 600.0)
        // 600/5 = 120 shrinks, (1500-100)/120 = 11.67
        assertTrue("decline should be small for long duration", decline < 12)
        assertTrue("decline should be positive", decline > 11)
        val finalRadius = 1500.0 - (decline * (600.0 / interval))
        assertEquals(NORMAL_MODE_MINIMUM_RADIUS, finalRadius, 0.01)
    }

    // MARK: Decline is always non-negative for any input

    @Test
    fun `decline is non-negative for all inputs`() {
        val testRadii = listOf(0.0, 50.0, 100.0, 500.0, 1000.0, 1500.0, 2000.0)
        val testDurations = listOf(0.0, 5.0, 30.0, 60.0, 120.0, 180.0, 600.0)
        for (radius in testRadii) {
            for (duration in testDurations) {
                val (_, decline) = calculateNormalModeSettings(radius, duration)
                assertTrue(
                    "decline should be >= 0 for radius=$radius, duration=$duration",
                    decline >= 0.0
                )
            }
        }
    }

    // MARK: Integration — findLastUpdate with normal mode settings

    @Test
    fun `findLastUpdate with normal mode settings shrinks correctly`() {
        val radius = 1500.0
        val duration = 120.0
        val (interval, decline) = calculateNormalModeSettings(radius, duration)

        // Game started 30.5 minutes ago (extra buffer to avoid boundary issues)
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test",
            timing = com.google.firebase.Timestamp(Date(now - 1_830_000)).let { start ->
                dev.rahier.pouleparty.model.Timing(
                    start = start,
                    end = com.google.firebase.Timestamp(Date(now + ((duration - 30) * 60_000).toLong()))
                )
            },
            zone = Zone(
                shrinkIntervalMinutes = interval,
                radius = radius,
                shrinkMetersPerUpdate = decline
            )
        )
        val (_, currentRadius) = game.findLastUpdate()
        // findLastUpdate truncates decline to Int on each step:
        // 6 shrinks * decline.toInt() subtracted from initialRadius.toInt()
        val expectedRadius = radius.toInt() - (decline.toInt() * 6)
        assertEquals(expectedRadius, currentRadius)
    }

    @Test
    fun `findLastUpdate with normal mode never goes below zero`() {
        val radius = 500.0
        val duration = 60.0
        val (interval, decline) = calculateNormalModeSettings(radius, duration)

        // Game started way longer ago than duration
        val now = System.currentTimeMillis()
        val game = Game(
            id = "test",
            timing = dev.rahier.pouleparty.model.Timing(
                start = com.google.firebase.Timestamp(Date(now - 7_200_000)),
                end = com.google.firebase.Timestamp(Date(now - 3_600_000))
            ),
            zone = Zone(
                shrinkIntervalMinutes = interval,
                radius = radius,
                shrinkMetersPerUpdate = decline
            )
        )
        val (_, currentRadius) = game.findLastUpdate()
        assertTrue("currentRadius should be >= 0, got $currentRadius", currentRadius >= 0)
    }

    // MARK: State — expert/normal mode toggle behavior

    @Test
    fun `initial state has expert mode disabled with 120 min default`() {
        val state = ChickenConfigUiState()
        assertFalse(state.isExpertMode)
        assertEquals(120.0, state.gameDurationMinutes, 0.01)
    }

    @Test
    fun `toggling to expert mode preserves current game values`() {
        val game = Game(id = "test", zone = Zone(shrinkIntervalMinutes = 7.0, shrinkMetersPerUpdate = 150.0))
        val state = ChickenConfigUiState(game = game, isExpertMode = false)
        val expertState = state.copy(isExpertMode = true)

        // Values should be preserved, not recalculated
        assertEquals(7.0, expertState.game.zone.shrinkIntervalMinutes, 0.01)
        assertEquals(150.0, expertState.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `switching from expert to normal would need recalculation`() {
        // When switching back to normal, the ViewModel recalculates
        // Here we verify the calculation produces different values than expert
        val game = Game(id = "test", zone = Zone(radius = 1500.0, shrinkIntervalMinutes = 10.0, shrinkMetersPerUpdate = 200.0))
        val state = ChickenConfigUiState(game = game, isExpertMode = true, gameDurationMinutes = 120.0)

        // Simulate what recalculation would produce
        val (interval, decline) = calculateNormalModeSettings(state.game.zone.radius, state.gameDurationMinutes)
        // Expert had interval=10, decline=200; normal should have interval=5, different decline
        assertEquals(5.0, interval, 0.01)
        assertNotEquals(200.0, decline, 0.01)
    }

    @Test
    fun `changing duration in expert mode does not affect game parameters`() {
        val game = Game(id = "test", zone = Zone(shrinkIntervalMinutes = 10.0, shrinkMetersPerUpdate = 200.0))
        val state = ChickenConfigUiState(game = game, isExpertMode = true, gameDurationMinutes = 120.0)
        val newState = state.copy(gameDurationMinutes = 60.0)

        // Game parameters unchanged
        assertEquals(10.0, newState.game.zone.shrinkIntervalMinutes, 0.01)
        assertEquals(200.0, newState.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    @Test
    fun `changing radius in expert mode does not recalculate interval or decline`() {
        val game = Game(id = "test", zone = Zone(radius = 1500.0, shrinkIntervalMinutes = 10.0, shrinkMetersPerUpdate = 200.0))
        val state = ChickenConfigUiState(game = game, isExpertMode = true)
        val newState = state.copy(game = state.game.copy(zone = state.game.zone.copy(radius = 800.0)))

        // interval and decline unchanged
        assertEquals(10.0, newState.game.zone.shrinkIntervalMinutes, 0.01)
        assertEquals(200.0, newState.game.zone.shrinkMetersPerUpdate, 0.01)
    }

    // MARK: Zone configuration — isZoneConfigured

    @Test
    fun `isZoneConfigured is false with default location`() {
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(center = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE))
            )
        )
        assertFalse(state.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured is true with custom location in followTheChicken mode`() {
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(center = GeoPoint(48.8566, 2.3522)),
                gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue
            )
        )
        assertTrue(state.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured is false with custom location but no final zone in stayInTheZone mode`() {
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(center = GeoPoint(48.8566, 2.3522), finalCenter = null),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue
            )
        )
        assertFalse(state.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured is true with custom location and final zone in stayInTheZone mode`() {
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(center = GeoPoint(48.8566, 2.3522), finalCenter = GeoPoint(48.8600, 2.3500)),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue
            )
        )
        assertTrue(state.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured is false with default location even with final zone set`() {
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(
                    center = GeoPoint(AppConstants.DEFAULT_LATITUDE, AppConstants.DEFAULT_LONGITUDE),
                    finalCenter = GeoPoint(48.8600, 2.3500)
                ),
                gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue
            )
        )
        assertFalse(state.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured detects near-default location as default`() {
        // Within 0.001 tolerance
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(center = GeoPoint(AppConstants.DEFAULT_LATITUDE + 0.0005, AppConstants.DEFAULT_LONGITUDE - 0.0003)),
                gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue
            )
        )
        assertFalse(state.isZoneConfigured)
    }

    @Test
    fun `isZoneConfigured detects location just outside tolerance as custom`() {
        val state = ChickenConfigUiState(
            game = Game(
                id = "test",
                zone = Zone(center = GeoPoint(AppConstants.DEFAULT_LATITUDE + 0.002, AppConstants.DEFAULT_LONGITUDE)),
                gameMode = GameMod.FOLLOW_THE_CHICKEN.firestoreValue
            )
        )
        assertTrue(state.isZoneConfigured)
    }
}
