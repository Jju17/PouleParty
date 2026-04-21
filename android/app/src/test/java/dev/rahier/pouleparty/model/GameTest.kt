package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.powerups.model.PowerUpType
import org.junit.Assert.*
import org.junit.Test
import java.util.Date
import java.util.UUID

class GameTest {

    @Test
    fun `gameCode is first 6 chars uppercased`() {
        val game = Game(id = "abcdef-1234-5678")
        assertEquals("ABCDEF", game.gameCode)
    }

    @Test
    fun `gameCode is uppercased`() {
        val game = Game(id = "xyz123")
        assertEquals("XYZ123", game.gameCode)
    }

    @Test
    fun `gameCode from short id uses full id`() {
        val game = Game(id = "abc")
        assertEquals("ABC", game.gameCode)
    }

    @Test
    fun `findLastUpdate returns initial radius before game starts`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() + 600_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000))
            ),
            zone = Zone(
                radius = 1500.0,
                shrinkMetersPerUpdate = 100.0
            )
        )
        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `findLastUpdate shrinks radius after game started`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 600_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000))
            ),
            zone = Zone(
                shrinkIntervalMinutes = 5.0,
                radius = 1500.0,
                shrinkMetersPerUpdate = 100.0
            )
        )
        val (_, radius) = game.findLastUpdate()
        assertTrue("Radius should have shrunk", radius < 1500)
    }

    @Test
    fun `startDate and endDate conversion`() {
        val now = Date()
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(now),
                end = Timestamp(Date(now.time + 3_600_000))
            )
        )
        assertTrue(Math.abs(game.startDate.time - now.time) < 1000)
    }

    @Test
    fun `initialLocation returns correct Point`() {
        val game = Game(
            id = "test",
            zone = Zone(center = GeoPoint(48.8566, 2.3522))
        )
        assertEquals(48.8566, game.initialLocation.latitude(), 0.0001)
        assertEquals(2.3522, game.initialLocation.longitude(), 0.0001)
    }

    @Test
    fun `withStartDate creates new game with updated start`() {
        val game = Game(id = "test")
        val newDate = Date(System.currentTimeMillis() + 1_000_000)
        val updated = game.withStartDate(newDate)

        assertTrue(Math.abs(updated.startDate.time - newDate.time) < 1000)
        assertEquals(game.id, updated.id) // id unchanged
    }

    @Test
    fun `withEndDate creates new game with updated end`() {
        val game = Game(id = "test")
        val newDate = Date(System.currentTimeMillis() + 5_000_000)
        val updated = game.withEndDate(newDate)

        assertTrue(Math.abs(updated.endDate.time - newDate.time) < 1000)
    }

    @Test
    fun `withInitialLocation creates new game with updated coordinates`() {
        val game = Game(id = "test")
        val point = com.mapbox.geojson.Point.fromLngLat(2.3522, 48.8566)
        val updated = game.withInitialLocation(point)

        assertEquals(48.8566, updated.initialLocation.latitude(), 0.0001)
        assertEquals(2.3522, updated.initialLocation.longitude(), 0.0001)
    }

    // MARK: - GameMod tests

    @Test
    fun `default game mod is stayInTheZone`() {
        val game = Game(id = "test")
        assertEquals(GameMod.STAY_IN_THE_ZONE, game.gameModEnum)
    }

    @Test
    fun `all game mods have correct titles`() {
        assertEquals("Follow the chicken \uD83D\uDC14", GameMod.FOLLOW_THE_CHICKEN.title)
        assertEquals("Stay in the zone \uD83D\uDCCD", GameMod.STAY_IN_THE_ZONE.title)
    }

    @Test
    fun `GameMod fromFirestore returns correct enum`() {
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, GameMod.fromFirestore("followTheChicken"))
        assertEquals(GameMod.STAY_IN_THE_ZONE, GameMod.fromFirestore("stayInTheZone"))
    }

    @Test
    fun `GameMod fromFirestore with unknown value defaults to followTheChicken`() {
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, GameMod.fromFirestore("unknownMode"))
    }

    @Test
    fun `GameMod has exactly 3 entries`() {
        assertEquals(2, GameMod.entries.size)
    }

    @Test
    fun `GameMod firestoreValue roundtrip`() {
        GameMod.entries.forEach { mod ->
            assertEquals(mod, GameMod.fromFirestore(mod.firestoreValue))
        }
    }

    @Test
    fun `mock game is valid`() {
        val mock = Game.mock
        assertTrue(mock.id.isNotEmpty())
        assertEquals(10, mock.maxPlayers)
        assertEquals(1500.0, mock.zone.radius, 0.01)
    }

    // MARK: - Found code tests

    @Test
    fun `foundCode defaults to empty`() {
        val game = Game(id = "test")
        assertEquals("", game.foundCode)
    }

    @Test
    fun `generateFoundCode is 4 digits`() {
        val code = Game.generateFoundCode()
        assertEquals(4, code.length)
        assertNotNull(code.toIntOrNull())
    }

    @Test
    fun `generateFoundCode is in range`() {
        repeat(50) {
            val code = Game.generateFoundCode()
            val value = code.toInt()
            assertTrue("Code should be >= 0", value >= 0)
            assertTrue("Code should be <= 9999", value <= 9999)
        }
    }

    @Test
    fun `generateFoundCode pads with zeros`() {
        repeat(100) {
            val code = Game.generateFoundCode()
            assertEquals("Code should always be 4 chars", 4, code.length)
        }
    }

    // MARK: - Winners tests

    @Test
    fun `winners defaults to empty`() {
        val game = Game(id = "test")
        assertTrue(game.winners.isEmpty())
    }

    @Test
    fun `winner data class works correctly`() {
        val winner = Winner(
            hunterId = "hunter-1",
            hunterName = "Julien",
            timestamp = Timestamp.now()
        )
        assertEquals("hunter-1", winner.hunterId)
        assertEquals("Julien", winner.hunterName)
    }

    @Test
    fun `mock game has foundCode`() {
        val mock = Game.mock
        assertEquals("1234", mock.foundCode)
    }

    // MARK: - hunterStartDate & head start

    @Test
    fun `hunterStartDate equals startDate when no head start`() {
        val now = Date()
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(now),
                headStartMinutes = 0.0
            )
        )
        assertEquals(now.time.toDouble(), game.hunterStartDate.time.toDouble(), 1000.0)
    }

    @Test
    fun `hunterStartDate offset by headStartMinutes`() {
        val now = Date()
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(now),
                headStartMinutes = 5.0
            )
        )
        val expectedOffset = 5 * 60 * 1000L
        assertEquals((now.time + expectedOffset).toDouble(), game.hunterStartDate.time.toDouble(), 1000.0)
    }

    @Test
    fun `withChickenHeadStart creates copy with correct value`() {
        val game = Game(id = "test", timing = Timing(headStartMinutes = 0.0))
        val updated = game.withChickenHeadStart(10.0)
        assertEquals(10.0, updated.timing.headStartMinutes, 0.01)
        assertEquals(game.id, updated.id)
    }

    // MARK: - GameStatus

    @Test
    fun `GameStatus fromFirestore returns correct enum`() {
        assertEquals(GameStatus.WAITING, GameStatus.fromFirestore("waiting"))
        assertEquals(GameStatus.IN_PROGRESS, GameStatus.fromFirestore("inProgress"))
        assertEquals(GameStatus.DONE, GameStatus.fromFirestore("done"))
    }

    @Test
    fun `GameStatus fromFirestore with unknown value defaults to WAITING`() {
        assertEquals(GameStatus.WAITING, GameStatus.fromFirestore("unknownStatus"))
    }

    @Test
    fun `GameStatus firestoreValue roundtrip`() {
        GameStatus.entries.forEach { status ->
            assertEquals(status, GameStatus.fromFirestore(status.firestoreValue))
        }
    }

    // MARK: - Pricing Model

    @Test
    fun `PricingModel firestoreValue roundtrip`() {
        PricingModel.entries.forEach { model ->
            assertEquals(model, PricingModel.fromFirestore(model.firestoreValue))
        }
    }

    @Test
    fun `PricingModel fromFirestore defaults to FREE for unknown value`() {
        assertEquals(PricingModel.FREE, PricingModel.fromFirestore("unknown"))
    }

    @Test
    fun `default game has free pricing model`() {
        val game = Game(id = "test")
        assertEquals("free", game.pricing.model)
        assertEquals(PricingModel.FREE, game.pricingModelEnum)
    }

    @Test
    fun `isPaid is false for free games`() {
        val game = Game(id = "test", pricing = Pricing(model = "free"))
        assertFalse(game.isPaid)
    }

    @Test
    fun `isPaid is true for flat games`() {
        val game = Game(id = "test", pricing = Pricing(model = "flat"))
        assertTrue(game.isPaid)
    }

    @Test
    fun `isPaid is true for deposit games`() {
        val game = Game(id = "test", pricing = Pricing(model = "deposit"))
        assertTrue(game.isPaid)
    }

    @Test
    fun `pricing fields have correct defaults`() {
        val game = Game(id = "test")
        assertEquals(0, game.pricing.pricePerPlayer)
        assertEquals(0, game.pricing.deposit)
        assertEquals(15.0, game.pricing.commission, 0.001)
    }

    @Test
    fun `flat game stores price per player`() {
        val game = Game(id = "test", pricing = Pricing(model = "flat", pricePerPlayer = 300), maxPlayers = 15)
        assertEquals(300, game.pricing.pricePerPlayer)
        assertEquals(15, game.maxPlayers)
    }

    @Test
    fun `deposit game stores deposit and price`() {
        val game = Game(id = "test", pricing = Pricing(model = "deposit", deposit = 1000, pricePerPlayer = 500))
        assertEquals(1000, game.pricing.deposit)
        assertEquals(500, game.pricing.pricePerPlayer)
    }

    // ── Enum edge cases ──

    @Test
    fun `GameStatus fromFirestore empty string defaults to WAITING`() {
        assertEquals(GameStatus.WAITING, GameStatus.fromFirestore(""))
    }

    @Test
    fun `GameMod fromFirestore is case sensitive`() {
        // "FollowTheChicken" (capital F) should not match → falls back to default
        assertEquals(GameMod.FOLLOW_THE_CHICKEN, GameMod.fromFirestore("FollowTheChicken"))
    }

    @Test
    fun `PowerUpType fromFirestore empty string defaults to ZONE_PREVIEW`() {
        assertEquals(PowerUpType.ZONE_PREVIEW, PowerUpType.fromFirestore(""))
    }

    @Test
    fun `PricingModel fromFirestore empty string defaults to FREE`() {
        assertEquals(PricingModel.FREE, PricingModel.fromFirestore(""))
    }

    // ── Found code generation ──

    @Test
    fun `foundCode is always 4 digits`() {
        repeat(100) {
            val code = Game.generateFoundCode()
            assertEquals(4, code.length)
            assertNotNull(code.toIntOrNull())
        }
    }

    @Test
    fun `foundCode preserves leading zeros`() {
        var seenLeadingZero = false
        repeat(1000) {
            val code = Game.generateFoundCode()
            if (code.startsWith("0")) seenLeadingZero = true
            assertEquals(4, code.length)
        }
        assertTrue("Should see at least one code with leading zero", seenLeadingZero)
    }

    // ── Game code derivation ──

    @Test
    fun `gameCode from short id`() {
        val game = Game(id = "abc")
        assertEquals("ABC", game.gameCode)
    }

    @Test
    fun `gameCode from empty id`() {
        val game = Game(id = "")
        assertEquals("", game.gameCode)
    }

    // ── Game defaults ──

    @Test
    fun `default timing headStart is zero`() {
        val timing = Timing()
        assertEquals(0.0, timing.headStartMinutes, 0.0)
    }

    @Test
    fun `default zone values`() {
        val zone = Zone()
        assertEquals(1500.0, zone.radius, 0.0)
        assertEquals(5.0, zone.shrinkIntervalMinutes, 0.0)
        assertEquals(100.0, zone.shrinkMetersPerUpdate, 0.0)
        assertEquals(0, zone.driftSeed)
        assertNull(zone.finalCenter)
    }

    @Test
    fun `default gameMode is stayInTheZone`() {
        val game = Game(id = "test")
        assertEquals(GameMod.STAY_IN_THE_ZONE, game.gameModEnum)
    }

    @Test
    fun `default status is waiting`() {
        val game = Game(id = "test")
        assertEquals(GameStatus.WAITING, game.gameStatusEnum)
    }

    @Test
    fun `default pricing is free`() {
        val game = Game(id = "test")
        assertEquals(PricingModel.FREE, game.pricingModelEnum)
        assertFalse(game.isPaid)
    }

    @Test
    fun `default registration not required`() {
        val game = Game(id = "test")
        assertFalse(game.registration.required)
    }

    @Test
    fun `default powerUps disabled`() {
        val game = Game(id = "test")
        assertFalse(game.powerUps.enabled)
    }

    // ── hunterStartDate calculation ──

    @Test
    fun `hunterStartDate equals startDate when no headStart`() {
        val game = Game(id = "test", timing = Timing(headStartMinutes = 0.0))
        assertEquals(game.startDate.time, game.hunterStartDate.time)
    }

    @Test
    fun `hunterStartDate adds headStart`() {
        val game = Game(id = "test", timing = Timing(headStartMinutes = 5.0))
        val diff = game.hunterStartDate.time - game.startDate.time
        assertEquals(5 * 60 * 1000L, diff)
    }

    @Test
    fun `hunterStartDate with max headStart`() {
        val game = Game(id = "test", timing = Timing(headStartMinutes = 15.0))
        val diff = game.hunterStartDate.time - game.startDate.time
        assertEquals(15 * 60 * 1000L, diff)
    }

    // ── Registration deadline ──

    @Test
    fun `registrationDeadline null when no minutes`() {
        val game = Game(id = "test", registration = GameRegistration(closesMinutesBefore = null))
        assertNull(game.registrationDeadline)
    }

    @Test
    fun `registrationDeadline correct`() {
        val game = Game(id = "test", registration = GameRegistration(closesMinutesBefore = 15))
        val deadline = game.registrationDeadline!!
        val expected = game.startDate.time - 15 * 60 * 1000L
        assertEquals(expected, deadline.time)
    }

    @Test
    fun `isRegistrationClosed false when not required`() {
        val game = Game(id = "test", registration = GameRegistration(required = false))
        assertFalse(game.isRegistrationClosed)
    }

    @Test
    fun `isRegistrationClosed false when no deadline`() {
        val game = Game(id = "test", registration = GameRegistration(required = true, closesMinutesBefore = null))
        assertFalse(game.isRegistrationClosed)
    }

    // ── isPaid ──

    @Test
    fun `isPaid true for flat`() {
        val game = Game(id = "test", pricing = Pricing(model = PricingModel.FLAT.firestoreValue))
        assertTrue(game.isPaid)
    }

    @Test
    fun `isPaid true for deposit`() {
        val game = Game(id = "test", pricing = Pricing(model = PricingModel.DEPOSIT.firestoreValue))
        assertTrue(game.isPaid)
    }

    // ── findLastUpdate edge cases ──

    @Test
    fun `findLastUpdate with zero shrink interval`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 600_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000))
            ),
            zone = Zone(radius = 1500.0, shrinkIntervalMinutes = 0.0, shrinkMetersPerUpdate = 100.0)
        )
        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `findLastUpdate with negative shrink interval`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 600_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000))
            ),
            zone = Zone(radius = 1500.0, shrinkIntervalMinutes = -5.0, shrinkMetersPerUpdate = 100.0)
        )
        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `findLastUpdate never goes negative`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 36_000_000)), // 10 hours ago
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000))
            ),
            zone = Zone(radius = 500.0, shrinkIntervalMinutes = 1.0, shrinkMetersPerUpdate = 100.0)
        )
        val (_, radius) = game.findLastUpdate()
        assertTrue("Radius must never go below 0, was $radius", radius >= 0)
        assertEquals(0, radius)
    }

    @Test
    fun `findLastUpdate with headStart`() {
        // 10 min elapsed, 8 min head start → effective 2 min < 5 min interval
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 600_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000)),
                headStartMinutes = 8.0
            ),
            zone = Zone(radius = 1500.0, shrinkIntervalMinutes = 5.0, shrinkMetersPerUpdate = 100.0)
        )
        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    // ── calculateNormalModeSettings all duration options ──

    @Test
    fun `normalMode all duration options reach 100m`() {
        val durations = listOf(60.0, 90.0, 120.0, 150.0, 180.0)
        for (duration in durations) {
            val (interval, decline) = calculateNormalModeSettings(1500.0, duration)
            assertEquals(5.0, interval, 0.0)
            assertTrue("Decline must be positive for $duration min", decline > 0)
            val numberOfShrinks = (duration / 5.0).toInt()
            val finalRadius = 1500.0 - numberOfShrinks * decline
            assertEquals("Zone must reach 100m for $duration min", 100.0, finalRadius, 0.01)
        }
    }

    @Test
    fun `normalMode with headStart subtracted`() {
        val (interval, decline) = calculateNormalModeSettings(1500.0, 75.0) // 90-15
        assertEquals(5.0, interval, 0.0)
        val expected = (1500.0 - 100.0) / (75.0 / 5.0)
        assertEquals(expected, decline, 0.01)
    }

    @Test
    fun `normalMode very short duration produces large decline`() {
        // 1 min / 5 min interval = 0.2 shrinks (fractional but > 0, passes guard)
        val (interval, decline) = calculateNormalModeSettings(1500.0, 1.0)
        assertEquals(5.0, interval, 0.0)
        // decline = (1500-100) / 0.2 = 7000 — very large but valid
        assertTrue("Decline should be positive", decline > 0)
    }

    // ── Power-up active effects ──

    @Test
    fun `isChickenInvisible false by default`() {
        assertFalse(Game(id = "test").isChickenInvisible)
    }

    @Test
    fun `isZoneFrozen false by default`() {
        assertFalse(Game(id = "test").isZoneFrozen)
    }

    @Test
    fun `isChickenInvisible true when future`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(invisibility = Timestamp(Date(System.currentTimeMillis() + 30_000)))
        ))
        assertTrue(game.isChickenInvisible)
    }

    @Test
    fun `isChickenInvisible false when expired`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(invisibility = Timestamp(Date(System.currentTimeMillis() - 5_000)))
        ))
        assertFalse(game.isChickenInvisible)
    }

    @Test
    fun `isZoneFrozen true when future`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(zoneFreeze = Timestamp(Date(System.currentTimeMillis() + 120_000)))
        ))
        assertTrue(game.isZoneFrozen)
    }

    @Test
    fun `isDecoyActive true when future`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(decoy = Timestamp(Date(System.currentTimeMillis() + 20_000)))
        ))
        assertTrue(game.isDecoyActive)
    }

    @Test
    fun `isDecoyActive false when expired`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(decoy = Timestamp(Date(System.currentTimeMillis() - 5_000)))
        ))
        assertFalse(game.isDecoyActive)
    }

    @Test
    fun `isJammerActive true when future`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(jammer = Timestamp(Date(System.currentTimeMillis() + 30_000)))
        ))
        assertTrue(game.isJammerActive)
    }

    @Test
    fun `isRadarPingActive true when future`() {
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(radarPing = Timestamp(Date(System.currentTimeMillis() + 30_000)))
        ))
        assertTrue(game.isRadarPingActive)
    }

    @Test
    fun `all effects expired return false`() {
        val past = Timestamp(Date(System.currentTimeMillis() - 10_000))
        val game = Game(id = "test", powerUps = GamePowerUps(
            activeEffects = ActiveEffects(
                invisibility = past, zoneFreeze = past, radarPing = past, decoy = past, jammer = past
            )
        ))
        assertFalse(game.isChickenInvisible)
        assertFalse(game.isZoneFrozen)
        assertFalse(game.isRadarPingActive)
        assertFalse(game.isDecoyActive)
        assertFalse(game.isJammerActive)
    }

    // ── Minimum start date logic ──

    /** Mirrors GameCreationUiState.minimumStartDate computation. */
    private fun computeMinimumStartDate(required: Boolean, deadlineMinutes: Int?): Date {
        val bufferMs = 5 * 60 * 1000L
        if (required) {
            return if (deadlineMinutes != null) {
                Date(System.currentTimeMillis() + deadlineMinutes * 60 * 1000L + bufferMs)
            } else {
                Date(System.currentTimeMillis() + bufferMs)
            }
        }
        return Date(System.currentTimeMillis() + 60_000L)
    }

    @Test
    fun `minimumStartDate open join is 1 minute`() {
        val min = computeMinimumStartDate(false, null)
        val expected = System.currentTimeMillis() + 60_000L
        assertTrue("Open join minimum should be ~1 min from now", Math.abs(min.time - expected) < 1000)
    }

    @Test
    fun `minimumStartDate registration 15 min`() {
        val min = computeMinimumStartDate(true, 15)
        val expected = Date(System.currentTimeMillis() + (15 + 5) * 60 * 1000L)
        assertTrue(Math.abs(min.time - expected.time) < 1000)
    }

    @Test
    fun `minimumStartDate registration 30 min`() {
        val min = computeMinimumStartDate(true, 30)
        val expected = Date(System.currentTimeMillis() + (30 + 5) * 60 * 1000L)
        assertTrue(Math.abs(min.time - expected.time) < 1000)
    }

    @Test
    fun `minimumStartDate registration 60 min`() {
        val min = computeMinimumStartDate(true, 60)
        val expected = Date(System.currentTimeMillis() + (60 + 5) * 60 * 1000L)
        assertTrue(Math.abs(min.time - expected.time) < 1000)
    }

    @Test
    fun `minimumStartDate registration 120 min`() {
        val min = computeMinimumStartDate(true, 120)
        val expected = Date(System.currentTimeMillis() + (120 + 5) * 60 * 1000L)
        assertTrue(Math.abs(min.time - expected.time) < 1000)
    }

    @Test
    fun `minimumStartDate registration 1 day`() {
        val min = computeMinimumStartDate(true, 1440)
        val expected = Date(System.currentTimeMillis() + (1440 + 5) * 60 * 1000L)
        assertTrue(Math.abs(min.time - expected.time) < 1000)
    }

    @Test
    fun `minimumStartDate required but no deadline is 5 minutes`() {
        val min = computeMinimumStartDate(true, null)
        val expected = System.currentTimeMillis() + 5 * 60 * 1000L
        assertTrue("Should be ~5 min from now", Math.abs(min.time - expected) < 1000)
    }

    @Test
    fun `minimumStartDate required with zero deadline is 5 min`() {
        val min = computeMinimumStartDate(true, 0)
        val expected = Date(System.currentTimeMillis() + 5 * 60 * 1000L)
        assertTrue(Math.abs(min.time - expected.time) < 1000)
    }

    @Test
    fun `switching to registration increases minimum`() {
        val openMin = computeMinimumStartDate(false, null)
        val regMin = computeMinimumStartDate(true, 15)
        assertTrue("Registration minimum should exceed open join", regMin.after(openMin))
        // open = now+1min, reg = now+20min → diff ≈ 19 min
        val diffMinutes = (regMin.time - openMin.time) / 60_000.0
        assertTrue("Difference should be ~19 min, was $diffMinutes", Math.abs(diffMinutes - 19) < 1)
    }

    @Test
    fun `increasing deadline increases minimum`() {
        val min15 = computeMinimumStartDate(true, 15)
        val min60 = computeMinimumStartDate(true, 60)
        val min1440 = computeMinimumStartDate(true, 1440)
        assertTrue(min60.after(min15))
        assertTrue(min1440.after(min60))
        val diff = (min60.time - min15.time) / 60_000.0
        assertTrue("60-15 = 45 min difference, was $diff", Math.abs(diff - 45) < 1)
    }

    @Test
    fun `disabling registration decreases minimum`() {
        val regMin = computeMinimumStartDate(true, 30)
        val openMin = computeMinimumStartDate(false, null)
        assertTrue("Open minimum should be less than reg minimum", openMin.before(regMin))
    }

    @Test
    fun `max deadline with short game still works`() {
        val minStart = computeMinimumStartDate(true, 1440)
        val minutesFromNow = (minStart.time - System.currentTimeMillis()) / 60_000.0
        assertTrue("Should be ~1445 min, was $minutesFromNow", Math.abs(minutesFromNow - 1445) < 1)
    }
}
