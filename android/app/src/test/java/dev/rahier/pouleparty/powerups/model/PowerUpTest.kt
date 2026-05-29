package dev.rahier.pouleparty.powerups.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GamePowerUps
import dev.rahier.pouleparty.ui.gamelogic.availablePowerUpTypes
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class PowerUpTest {

    @Test
    fun `typeEnum returns correct PowerUpType from firestoreValue`() {
        val powerUp = PowerUp(type = "radarPing")
        assertEquals(PowerUpType.RADAR_PING, powerUp.typeEnum)
    }

    @Test
    fun `typeEnum defaults to ZONE_PREVIEW for unknown value`() {
        val powerUp = PowerUp(type = "unknown")
        assertEquals(PowerUpType.ZONE_PREVIEW, powerUp.typeEnum)
    }

    @Test
    fun `isCollected returns true when collectedBy is set`() {
        val powerUp = PowerUp(collectedBy = "user123")
        assertTrue(powerUp.isCollected)
    }

    @Test
    fun `isCollected returns false when collectedBy is null`() {
        val powerUp = PowerUp()
        assertFalse(powerUp.isCollected)
    }

    @Test
    fun `isActivated returns true when activatedAt is set`() {
        val powerUp = PowerUp(activatedAt = Timestamp.now())
        assertTrue(powerUp.isActivated)
    }

    @Test
    fun `isActivated returns false when activatedAt is null`() {
        val powerUp = PowerUp()
        assertFalse(powerUp.isActivated)
    }

    @Test
    fun `hunter power-ups are correctly classified`() {
        assertTrue(PowerUpType.ZONE_PREVIEW.isHunterPowerUp)
        assertTrue(PowerUpType.RADAR_PING.isHunterPowerUp)
        assertFalse(PowerUpType.INVISIBILITY.isHunterPowerUp)
        assertFalse(PowerUpType.ZONE_FREEZE.isHunterPowerUp)
    }

    @Test
    fun `duration values are correct`() {
        assertNull(PowerUpType.ZONE_PREVIEW.durationSeconds)
        assertEquals(3L, PowerUpType.RADAR_PING.durationSeconds)
        assertEquals(30L, PowerUpType.INVISIBILITY.durationSeconds)
        assertEquals(120L, PowerUpType.ZONE_FREEZE.durationSeconds)
    }

    @Test
    fun `firestoreValue round-trips correctly`() {
        PowerUpType.entries.forEach { type ->
            assertEquals(type, PowerUpType.fromFirestore(type.firestoreValue))
        }
    }

    @Test
    fun `locationPoint returns correct Point`() {
        val powerUp = PowerUp(location = GeoPoint(50.8466, 4.3528))
        val point = powerUp.locationPoint
        assertEquals(50.8466, point.latitude(), 0.0001)
        assertEquals(4.3528, point.longitude(), 0.0001)
    }

    @Test
    fun `mock is valid`() {
        val mock = PowerUp.mock
        assertTrue(mock.id.isNotEmpty())
        assertEquals(PowerUpType.RADAR_PING, mock.typeEnum)
        assertFalse(mock.isCollected)
        assertFalse(mock.isActivated)
    }

    // MARK: - Firestore decoding contract (regression for v1.6.2)

    /**
     * FirestoreRepository.powerUpsFlow relies on the data class tolerating
     * a missing `id` field: `doc.toObject(PowerUp::class.java)` falls back to
     * the default "" then `.copy(id = doc.id)` injects the document name.
     * Locking that contract in so no-one makes `id` non-defaulted.
     */
    @Test
    fun `id has safe default so toObject never fails when Firestore lacks id`() {
        val withoutId = PowerUp(
            type = "radarPing",
            location = GeoPoint(50.8466, 4.3528),
            spawnedAt = Timestamp(Date(1_800_000_000_000L))
        )
        assertEquals("", withoutId.id)
        val injected = withoutId.copy(id = "pu-0-0-1529788")
        assertEquals("pu-0-0-1529788", injected.id)
        assertEquals(PowerUpType.RADAR_PING, injected.typeEnum)
    }

    /**
     * Server 1.6.2+ writes an explicit `id` field. Round-trip must preserve it
     * even when `.copy(id = doc.id)` is chained afterwards — both should agree.
     */
    @Test
    fun `explicit id from Firestore is preserved through copy`() {
        val fromServer = PowerUp(
            id = "pu-0-0-1529788",
            type = "radarPing",
            location = GeoPoint(50.8466, 4.3528),
            spawnedAt = Timestamp(Date(1_800_000_000_000L))
        )
        val afterCopy = fromServer.copy(id = "pu-0-0-1529788")
        assertEquals("pu-0-0-1529788", afterCopy.id)
    }

    // MARK: PP-35 availablePowerUpTypes helper

    /**
     * `FOLLOW_THE_CHICKEN` keeps every power-up type. Ordering must match the
     * enum declaration so the wizard and the iOS sibling render the cards
     * in the same order.
     */
    @Test
    fun `availablePowerUpTypes for FOLLOW_THE_CHICKEN includes every type`() {
        val expected = listOf(
            PowerUpType.ZONE_PREVIEW,
            PowerUpType.RADAR_PING,
            PowerUpType.INVISIBILITY,
            PowerUpType.ZONE_FREEZE,
            PowerUpType.DECOY,
            PowerUpType.JAMMER,
        )
        assertEquals(expected, availablePowerUpTypes(GameMod.FOLLOW_THE_CHICKEN))
    }

    /**
     * `STAY_IN_THE_ZONE` strips the positional power-ups (invisibility /
     * decoy / jammer) because the chicken does not broadcast its position.
     * Order must mirror the Kotlin enum declaration AND the iOS sibling.
     */
    @Test
    fun `availablePowerUpTypes for STAY_IN_THE_ZONE strips positional types`() {
        val expected = listOf(
            PowerUpType.ZONE_PREVIEW,
            PowerUpType.RADAR_PING,
            PowerUpType.ZONE_FREEZE,
        )
        assertEquals(expected, availablePowerUpTypes(GameMod.STAY_IN_THE_ZONE))
    }

    /**
     * Defaults shipped to players: every power-up type is enabled. The
     * server-side `stayInTheZone` filter still strips the position-only
     * ones at spawn time. Keep this golden in lockstep with the iOS
     * sibling so a one-platform regression never sneaks in.
     */
    @Test
    fun `default enabledTypes cover every type`() {
        val defaults = GamePowerUps().enabledTypes
        assertEquals(PowerUpType.entries.map { it.firestoreValue }, defaults)
    }

    // MARK: PP-37 parity goldens (PP-35 follow-up)

    /**
     * Strict count parity: 6 types in FOLLOW_THE_CHICKEN vs 3 in
     * STAY_IN_THE_ZONE. Mirrors the iOS
     * `availablePowerUpTypesCountsMatchParityMatrix` test — a silent enum
     * addition that bypassed the positional filter would fail this on
     * one platform without the other and surface the divergence loudly.
     */
    @Test
    fun `availablePowerUpTypes counts match parity matrix`() {
        assertEquals(6, availablePowerUpTypes(GameMod.FOLLOW_THE_CHICKEN).size)
        assertEquals(3, availablePowerUpTypes(GameMod.STAY_IN_THE_ZONE).size)
    }

    /**
     * `STAY_IN_THE_ZONE` MUST NOT contain any positional power-up — the
     * chicken does not broadcast its position in that mode so spawning
     * invisibility / decoy / jammer is wasted. Mirrors the iOS
     * `availablePowerUpTypesStayInTheZoneExcludesEveryPositionalType`
     * test.
     */
    @Test
    fun `STAY_IN_THE_ZONE excludes every positional power-up`() {
        val stay = availablePowerUpTypes(GameMod.STAY_IN_THE_ZONE)
        assertFalse(stay.contains(PowerUpType.INVISIBILITY))
        assertFalse(stay.contains(PowerUpType.DECOY))
        assertFalse(stay.contains(PowerUpType.JAMMER))
    }

    /**
     * `STAY_IN_THE_ZONE` MUST contain every non-positional power-up.
     * Mirrors the iOS
     * `availablePowerUpTypesStayInTheZoneIncludesEveryNonPositionalType`
     * test.
     */
    @Test
    fun `STAY_IN_THE_ZONE keeps every non-positional power-up`() {
        val stay = availablePowerUpTypes(GameMod.STAY_IN_THE_ZONE)
        assertTrue(stay.contains(PowerUpType.ZONE_PREVIEW))
        assertTrue(stay.contains(PowerUpType.RADAR_PING))
        assertTrue(stay.contains(PowerUpType.ZONE_FREEZE))
    }

    /**
     * `FOLLOW_THE_CHICKEN` is a passthrough — every enum case lands in
     * the returned list. Mirrors the iOS
     * `availablePowerUpTypesFollowTheChickenIsPassthrough` test.
     */
    @Test
    fun `FOLLOW_THE_CHICKEN is a passthrough of every enum case`() {
        val follow = availablePowerUpTypes(GameMod.FOLLOW_THE_CHICKEN)
        PowerUpType.entries.forEach { type ->
            assertTrue("Expected $type in FOLLOW_THE_CHICKEN", follow.contains(type))
        }
    }

    /**
     * The Firestore wire format for `enabledTypes` is a `List<String>` of
     * `firestoreValue`s. The strings used by iOS, Android and the TS
     * server MUST match exactly — a typo on one platform silently breaks
     * the `stayInTheZone` filter on the server. Locks the wire contract.
     */
    @Test
    fun `PowerUpType firestoreValues match Firestore wire contract`() {
        val expected = mapOf(
            PowerUpType.ZONE_PREVIEW to "zonePreview",
            PowerUpType.RADAR_PING to "radarPing",
            PowerUpType.INVISIBILITY to "invisibility",
            PowerUpType.ZONE_FREEZE to "zoneFreeze",
            PowerUpType.DECOY to "decoy",
            PowerUpType.JAMMER to "jammer",
        )
        expected.forEach { (type, raw) ->
            assertEquals("Wire raw value drift on $type", raw, type.firestoreValue)
        }
    }

    /**
     * The defaults shipped to players (`zoneFreeze` + `zonePreview`)
     * must be available in BOTH modes — a player who keeps the defaults
     * in `stayInTheZone` should still see both power-up types spawn.
     * Guards against a future filter change that would accidentally
     * strip a default type.
     */
    @Test
    fun `default enabledTypes are available in both modes`() {
        val follow = availablePowerUpTypes(GameMod.FOLLOW_THE_CHICKEN)
        val stay = availablePowerUpTypes(GameMod.STAY_IN_THE_ZONE)
        assertTrue(follow.contains(PowerUpType.ZONE_FREEZE))
        assertTrue(follow.contains(PowerUpType.ZONE_PREVIEW))
        assertTrue(stay.contains(PowerUpType.ZONE_FREEZE))
        assertTrue(stay.contains(PowerUpType.ZONE_PREVIEW))
    }
}
