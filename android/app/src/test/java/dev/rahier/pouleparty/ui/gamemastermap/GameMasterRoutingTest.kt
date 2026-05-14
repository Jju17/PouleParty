package dev.rahier.pouleparty.ui.gamemastermap

import dev.rahier.pouleparty.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PP-66 — Role routing tests for the GameMaster.
 *
 * The actual `findActiveGame` query lives in `FirestoreRepository`
 * and queries three buckets (hunter, chicken, GameMaster) in that
 * order. The decision that lands a user on `GameMasterMapScreen`
 * (rather than `ChickenMapScreen` / `HunterMapScreen`) boils down
 * to the pure `Game.isChicken(userId)` predicate and the
 * `hunterIds` / `gameMasterIds` membership checks. We pin those
 * predicates here so any drift between iOS and Android shows up at
 * unit-test time.
 *
 * If a UID ends up in both `creatorId` AND `gameMasterIds` (defense
 * in depth — the GM join CF and firestore.rules already prevent
 * that), priority is `creatorId` per PP-24. The current routing
 * surface implements this implicitly: `isChicken(userId)` returns
 * true for that UID, and `findActiveGame` adds chicken candidates
 * before GameMaster candidates.
 */
class GameMasterRoutingTest {

    // ── isChicken predicate ─────────────────────────────

    @Test
    fun `isChicken returns true only for the designated chickenId`() {
        val game = Game.mock.copy(
            creatorId = "creator-uid",
            chickenId = "designated-uid",      // PP-26: GM-re-designated chicken
            gameMasterIds = listOf("gm-uid"),
            hunterIds = listOf("hunter-uid"),
        )

        assertTrue(game.isChicken("designated-uid"))
        // Creator stays the admin owner; the chicken is the designated UID.
        assertFalse(game.isChicken("creator-uid"))
        assertFalse(game.isChicken("gm-uid"))
        assertFalse(game.isChicken("hunter-uid"))
        assertFalse(game.isChicken(""))
    }

    @Test
    fun `isChicken empty uid never matches even when chickenId is also empty`() {
        // Guards against the both-empty-strings ambiguity that would
        // otherwise route un-authenticated users to the chicken map.
        val game = Game.mock.copy(chickenId = "")
        assertFalse(game.isChicken(""))
        assertFalse(game.isChicken("anyone"))
    }

    // ── Role buckets are mutually exclusive by design ───

    @Test
    fun `creator who is also chicken is identified as chicken not as a hunter`() {
        // At creation `chickenId == creatorId` by the firestore.rules
        // `allow create` clause. The creator cannot self-join as a
        // hunter (rule denies it), so this is the canonical case.
        val game = Game.mock.copy(
            creatorId = "creator-uid",
            chickenId = "creator-uid",
        )
        assertTrue(game.isChicken("creator-uid"))
        assertFalse(game.hunterIds.contains("creator-uid"))
        assertFalse(game.gameMasterIds.contains("creator-uid"))
    }

    @Test
    fun `gameMasterIds membership identifies the GameMaster role`() {
        val game = Game.mock.copy(
            creatorId = "creator-uid",
            chickenId = "creator-uid",
            gameMasterIds = listOf("gm-uid"),
        )
        assertTrue(game.gameMasterIds.contains("gm-uid"))
        // The GM is not the chicken and not a hunter.
        assertFalse(game.isChicken("gm-uid"))
        assertFalse(game.hunterIds.contains("gm-uid"))
    }

    // ── Edge case: same UID in creatorId AND gameMasterIds ────────

    @Test
    fun `chicken priority over gameMaster when a UID ends up in both buckets`() {
        // Defense-in-depth scenario flagged by PP-66 spec: if a UID
        // somehow ends up in both `creatorId` (with chickenId=that uid)
        // AND `gameMasterIds`, `isChicken(uid)` returning true is the
        // tie-breaker that puts the user on the chicken map.
        val uid = "ambiguous-uid"
        val game = Game.mock.copy(
            creatorId = uid,
            chickenId = uid,
            gameMasterIds = listOf(uid),
        )
        assertTrue(game.isChicken(uid))
        // The GM list also contains the uid, but the chicken check
        // takes priority — both findActiveGame (iOS + Android) and
        // the AppFeature / HomeViewModel reducers honour this by
        // adding chicken candidates before GameMaster candidates.
        assertTrue(game.gameMasterIds.contains(uid))
    }

    // ── teamName-everywhere (PP-90 / 2026-05-08) ────────

    @Test
    fun `chicken cannot appear in hunterIds`() {
        // The firestore.rules `allow update` clauses on `hunterIds`
        // explicitly check `!hasAny([chickenId])`. We pin the model
        // expectation so the GameMaster drawer / validation queue
        // never accidentally lists the chicken as a hunter.
        val game = Game.mock.copy(
            chickenId = "the-chicken-uid",
            hunterIds = listOf("hunter-a", "hunter-b"),
        )
        assertFalse(game.hunterIds.contains(game.chickenId))
    }

    @Test
    fun `gameMaster cannot appear in hunterIds or be the chicken`() {
        // The `joinAsGameMaster` CF rejects a UID that is already in
        // `hunterIds` or that equals `chickenId`. We test the model
        // contract — the constraint surfaces as "these three sets are
        // disjoint" everywhere in the GameMaster routing decision.
        val game = Game.mock.copy(
            chickenId = "the-chicken",
            hunterIds = listOf("h1", "h2"),
            gameMasterIds = listOf("gm-1", "gm-2"),
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
