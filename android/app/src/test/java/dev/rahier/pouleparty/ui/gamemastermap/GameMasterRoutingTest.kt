package dev.rahier.pouleparty.ui.gamemastermap

import dev.rahier.pouleparty.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PP-66 / PP-107 — Role routing tests for the GameMaster.
 *
 * The actual `findActiveGame` query lives in `FirestoreRepository`
 * and resolves each membership's role from the authoritative `roles`
 * map (PP-107). The decision that lands a user on `GameMasterMapScreen`
 * (rather than `ChickenMapScreen` / `HunterMapScreen`) boils down to
 * the pure `Game.isChicken` / `isHunter` / `isGameMaster` predicates
 * derived from `roles`. We pin those predicates here so any drift
 * between iOS and Android shows up at unit-test time.
 *
 * Since `roles` maps each uid to exactly one role, the old
 * "same uid in two buckets" ambiguity is now impossible by
 * construction — a uid is chicken XOR hunter XOR gameMaster.
 */
class GameMasterRoutingTest {

    // ── isChicken predicate ─────────────────────────────

    @Test
    fun `isChicken returns true only for the designated chicken uid`() {
        val game = Game.mock.copy(
            creatorId = "creator-uid",
            roles = mapOf(
                "designated-uid" to "chicken",   // PP-26: GM-re-designated chicken
                "gm-uid" to "gameMaster",
                "hunter-uid" to "hunter",
            ),
        )

        assertTrue(game.isChicken("designated-uid"))
        // Creator stays the admin owner; the chicken is the designated UID.
        assertFalse(game.isChicken("creator-uid"))
        assertFalse(game.isChicken("gm-uid"))
        assertFalse(game.isChicken("hunter-uid"))
        assertFalse(game.isChicken(""))
    }

    @Test
    fun `isChicken empty uid never matches even when roles is empty`() {
        // Guards against the both-empty-strings ambiguity that would
        // otherwise route un-authenticated users to the chicken map.
        val game = Game.mock.copy(roles = emptyMap())
        assertFalse(game.isChicken(""))
        assertFalse(game.isChicken("anyone"))
    }

    // ── Roles are mutually exclusive by design ──────────

    @Test
    fun `creator who is also chicken is identified as chicken not as a hunter`() {
        // At creation `roles == { creatorId: "chicken" }` by the
        // firestore.rules `allow create` clause. The creator cannot
        // self-join as a hunter (rule denies it).
        val game = Game.mock.copy(
            creatorId = "creator-uid",
            roles = mapOf("creator-uid" to "chicken"),
        )
        assertTrue(game.isChicken("creator-uid"))
        assertFalse(game.hunterIds.contains("creator-uid"))
        assertFalse(game.gameMasterIds.contains("creator-uid"))
    }

    @Test
    fun `gameMaster role identifies the GameMaster`() {
        val game = Game.mock.copy(
            creatorId = "creator-uid",
            roles = mapOf("creator-uid" to "chicken", "gm-uid" to "gameMaster"),
        )
        assertTrue(game.gameMasterIds.contains("gm-uid"))
        assertTrue(game.isGameMaster("gm-uid"))
        // The GM is not the chicken and not a hunter.
        assertFalse(game.isChicken("gm-uid"))
        assertFalse(game.hunterIds.contains("gm-uid"))
    }

    // ── teamName-everywhere (PP-90 / 2026-05-08) ────────

    @Test
    fun `chicken cannot appear in hunterIds`() {
        // A uid has exactly one role in `roles`, so the chicken can
        // never also be a hunter. We pin the model expectation so the
        // GameMaster drawer / validation queue never accidentally lists
        // the chicken as a hunter.
        val game = Game.mock.copy(
            roles = mapOf(
                "the-chicken-uid" to "chicken",
                "hunter-a" to "hunter",
                "hunter-b" to "hunter",
            ),
        )
        assertFalse(game.hunterIds.contains(game.chickenId))
    }

    @Test
    fun `chicken hunter and gameMaster sets are disjoint`() {
        // The `roles` map guarantees one role per uid; the derived
        // chicken / hunter / GM sets are disjoint by construction.
        val game = Game.mock.copy(
            roles = mapOf(
                "the-chicken" to "chicken",
                "h1" to "hunter",
                "h2" to "hunter",
                "gm-1" to "gameMaster",
                "gm-2" to "gameMaster",
            ),
        )
        val chickenSet = setOf(game.chickenId)
        val hunterSet = game.hunterIds.toSet()
        val gmSet = game.gameMasterIds.toSet()
        assertTrue("chicken vs hunter intersect must be empty", chickenSet.intersect(hunterSet).isEmpty())
        assertTrue("chicken vs gm intersect must be empty", chickenSet.intersect(gmSet).isEmpty())
        assertTrue("hunter vs gm intersect must be empty", hunterSet.intersect(gmSet).isEmpty())
    }

    // ── Routing-target shape (cross-platform parity sanity) ───────

    @Test
    fun `chicken hunter and gameMaster are the three routable roles`() {
        // The Android PlayerRole enum and iOS GameRole enum each list
        // exactly these three cases. Locking the count here so a
        // future addition (e.g. SPECTATOR / OBSERVER) forces a
        // conscious update to both platforms.
        val expected = setOf("CHICKEN", "HUNTER", "GAME_MASTER")
        val actual = dev.rahier.pouleparty.ui.gamelogic.PlayerRole.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
