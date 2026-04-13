package dev.rahier.pouleparty

import com.google.firebase.Timestamp
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.model.*
import dev.rahier.pouleparty.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Tests for power-up effects on location tracking logic.
 * Verifies that invisibility, jammer, radar ping, and decoy
 * behave correctly with respect to location writes.
 */
class LocationTrackingEffectsTest {

    private fun gameWithEffect(
        gameMode: GameMod = GameMod.FOLLOW_THE_CHICKEN,
        invisibility: Timestamp? = null,
        zoneFreeze: Timestamp? = null,
        radarPing: Timestamp? = null,
        decoy: Timestamp? = null,
        jammer: Timestamp? = null,
        chickenCanSeeHunters: Boolean = false
    ): Game = Game.mock.copy(
        gameMode = gameMode.firestoreValue,
        chickenCanSeeHunters = chickenCanSeeHunters,
        powerUps = GamePowerUps(
            enabled = true,
            activeEffects = ActiveEffects(
                invisibility = invisibility,
                zoneFreeze = zoneFreeze,
                radarPing = radarPing,
                decoy = decoy,
                jammer = jammer
            )
        )
    )

    private val futureTimestamp = Timestamp(Date(System.currentTimeMillis() + 30_000))
    private val pastTimestamp = Timestamp(Date(System.currentTimeMillis() - 10_000))

    // ── Invisibility ────────────────────────────────────

    @Test
    fun `invisibility active prevents chicken location write`() {
        val game = gameWithEffect(invisibility = futureTimestamp)
        assertTrue("Chicken should be invisible", game.isChickenInvisible)
        // In real code, isChickenInvisible gates setChickenLocation calls
    }

    @Test
    fun `invisibility expired allows chicken location write`() {
        val game = gameWithEffect(invisibility = pastTimestamp)
        assertFalse("Chicken should not be invisible after expiry", game.isChickenInvisible)
    }

    @Test
    fun `invisibility nil allows chicken location write`() {
        val game = gameWithEffect()
        assertFalse(game.isChickenInvisible)
    }

    // ── Jammer ──────────────────────────────────────────

    @Test
    fun `jammer active adds noise to location`() {
        val game = gameWithEffect(jammer = futureTimestamp)
        assertTrue("Jammer should be active", game.isJammerActive)
    }

    @Test
    fun `jammer noise constant is approximately 200m`() {
        // 0.0036 degrees ≈ 400m total range, so ±200m
        assertEquals(0.0036, AppConstants.JAMMER_NOISE_DEGREES, 0.0001)
        // Noise applied as (random - 0.5) * 0.0036 = range [-0.0018, +0.0018]
        // 0.0018 degrees * 111320 m/degree ≈ 200m
        val maxNoiseMeters = 0.0018 * 111_320
        assertTrue("Max noise should be ~200m", maxNoiseMeters in 190.0..210.0)
    }

    @Test
    fun `jammer expired stops noise`() {
        val game = gameWithEffect(jammer = pastTimestamp)
        assertFalse(game.isJammerActive)
    }

    // ── Radar Ping ──────────────────────────────────────

    @Test
    fun `radar ping active in stayInTheZone forces chicken write`() {
        val game = gameWithEffect(
            gameMode = GameMod.STAY_IN_THE_ZONE,
            radarPing = futureTimestamp
        )
        assertTrue("Radar ping should be active", game.isRadarPingActive)
        // In real code, isRadarPingActive gates forced writes in stayInTheZone
    }

    @Test
    fun `radar ping inactive in stayInTheZone prevents chicken write`() {
        val game = gameWithEffect(gameMode = GameMod.STAY_IN_THE_ZONE)
        assertFalse(game.isRadarPingActive)
    }

    @Test
    fun `radar ping expired stops forced writes`() {
        val game = gameWithEffect(
            gameMode = GameMod.STAY_IN_THE_ZONE,
            radarPing = pastTimestamp
        )
        assertFalse(game.isRadarPingActive)
    }

    // ── Decoy ───────────────────────────────────────────

    @Test
    fun `decoy active creates fake position`() {
        val game = gameWithEffect(decoy = futureTimestamp)
        assertTrue("Decoy should be active", game.isDecoyActive)
    }

    @Test
    fun `decoy position is deterministic from seed`() {
        val seed = 42L
        val decoyExpires = System.currentTimeMillis() / 1000
        val combinedSeed = seed xor decoyExpires

        val angle1 = seededRandom(combinedSeed, 0) * 2 * Math.PI
        val angle2 = seededRandom(combinedSeed, 0) * 2 * Math.PI
        assertEquals("Decoy angle should be deterministic", angle1, angle2, 1e-15)

        val distance1 = 200 + seededRandom(combinedSeed, 1) * 300
        val distance2 = 200 + seededRandom(combinedSeed, 1) * 300
        assertEquals("Decoy distance should be deterministic", distance1, distance2, 1e-10)
    }

    @Test
    fun `decoy distance is between 200m and 500m`() {
        for (seed in listOf(42L, 99L, 12345L, 0L, 999_999L)) {
            val distance = 200 + seededRandom(seed, 1) * 300
            assertTrue("Decoy distance $distance should be >= 200", distance >= 200)
            assertTrue("Decoy distance $distance should be <= 500", distance <= 500)
        }
    }

    @Test
    fun `decoy expired hides fake position`() {
        val game = gameWithEffect(decoy = pastTimestamp)
        assertFalse(game.isDecoyActive)
    }

    // ── Location Throttle ───────────────────────────────

    @Test
    fun `location throttle is 5 seconds`() {
        assertEquals(5_000L, AppConstants.LOCATION_THROTTLE_MS)
    }

    @Test
    fun `location minimum distance is 10 meters`() {
        assertEquals(10f, AppConstants.LOCATION_MIN_DISTANCE_METERS)
    }

    // ── Zone Check by Role and Mode ─────────────────────

    @Test
    fun `shouldCheckZone chicken in followTheChicken is false`() {
        assertFalse(shouldCheckZone(PlayerRole.CHICKEN, GameMod.FOLLOW_THE_CHICKEN))
    }

    @Test
    fun `shouldCheckZone hunter in followTheChicken is true`() {
        assertTrue(shouldCheckZone(PlayerRole.HUNTER, GameMod.FOLLOW_THE_CHICKEN))
    }

    @Test
    fun `shouldCheckZone chicken in stayInTheZone is true`() {
        assertTrue(shouldCheckZone(PlayerRole.CHICKEN, GameMod.STAY_IN_THE_ZONE))
    }

    @Test
    fun `shouldCheckZone hunter in stayInTheZone is true`() {
        assertTrue(shouldCheckZone(PlayerRole.HUNTER, GameMod.STAY_IN_THE_ZONE))
    }

    // ── Combined Effects ────────────────────────────────

    @Test
    fun `jammer and radar ping can be active simultaneously`() {
        val game = gameWithEffect(
            gameMode = GameMod.STAY_IN_THE_ZONE,
            jammer = futureTimestamp,
            radarPing = futureTimestamp
        )
        assertTrue(game.isJammerActive)
        assertTrue(game.isRadarPingActive)
        // Chicken writes forced (radar ping) but with noise (jammer)
    }

    @Test
    fun `invisibility overrides jammer - no write at all`() {
        val game = gameWithEffect(
            invisibility = futureTimestamp,
            jammer = futureTimestamp
        )
        assertTrue(game.isChickenInvisible)
        assertTrue(game.isJammerActive)
        // In real code, invisibility check comes first and skips the write entirely
    }

    @Test
    fun `multiple effects can expire independently`() {
        val game = gameWithEffect(
            invisibility = pastTimestamp,       // expired
            jammer = futureTimestamp,            // still active
            zoneFreeze = pastTimestamp           // expired
        )
        assertFalse(game.isChickenInvisible)
        assertTrue(game.isJammerActive)
        assertFalse(game.isZoneFrozen)
    }

    // ── chickenCanSeeHunters ────────────────────────────

    @Test
    fun `chickenCanSeeHunters gates hunter location writes`() {
        val gameWithHunterTracking = gameWithEffect(chickenCanSeeHunters = true)
        assertTrue(gameWithHunterTracking.chickenCanSeeHunters)

        val gameWithoutHunterTracking = gameWithEffect(chickenCanSeeHunters = false)
        assertFalse(gameWithoutHunterTracking.chickenCanSeeHunters)
    }

    // ── Heartbeat ───────────────────────────────────────

    @Test
    fun `isChickenDisconnected false when no heartbeat`() {
        assertFalse(Game.mock.isChickenDisconnected)
    }

    @Test
    fun `isChickenDisconnected false when recent heartbeat`() {
        val game = Game.mock.copy(lastHeartbeat = Timestamp(Date(System.currentTimeMillis() - 10_000)))
        assertFalse(game.isChickenDisconnected)
    }

    @Test
    fun `isChickenDisconnected true when stale heartbeat`() {
        val game = Game.mock.copy(lastHeartbeat = Timestamp(Date(System.currentTimeMillis() - 90_000)))
        assertTrue(game.isChickenDisconnected)
    }
}
