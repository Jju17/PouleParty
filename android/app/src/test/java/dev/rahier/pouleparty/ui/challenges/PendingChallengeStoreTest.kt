package dev.rahier.pouleparty.ui.challenges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingChallengeStoreTest {

    private lateinit var prefs: FakePreferences
    private lateinit var store: PendingChallengeStore

    @Before
    fun setUp() {
        prefs = FakePreferences()
        store = PendingChallengeStore(prefs)
    }

    @Test
    fun `ids returns empty set when nothing persisted`() {
        assertTrue(store.ids("game-1").isEmpty())
    }

    @Test
    fun `add then ids round-trips`() {
        store.add("c1", "game-1")
        assertEquals(setOf("c1"), store.ids("game-1"))
    }

    @Test
    fun `add is idempotent`() {
        store.add("c1", "game-1")
        store.add("c1", "game-1")
        assertEquals(setOf("c1"), store.ids("game-1"))
    }

    @Test
    fun `ids are scoped per game`() {
        store.add("c1", "game-A")
        store.add("c2", "game-B")
        assertEquals(setOf("c1"), store.ids("game-A"))
        assertEquals(setOf("c2"), store.ids("game-B"))
    }

    @Test
    fun `remove drops a single id while keeping others`() {
        store.add("c1", "game-1")
        store.add("c2", "game-1")
        store.remove("c1", "game-1")
        assertEquals(setOf("c2"), store.ids("game-1"))
    }

    @Test
    fun `remove of last id leaves an empty set`() {
        store.add("c1", "game-1")
        store.remove("c1", "game-1")
        assertTrue(store.ids("game-1").isEmpty())
    }

    @Test
    fun `clear drops every id for the game`() {
        store.add("c1", "game-1")
        store.add("c2", "game-1")
        store.clear("game-1")
        assertTrue(store.ids("game-1").isEmpty())
    }

    @Test
    fun `clear of one game does not affect another`() {
        store.add("c1", "game-A")
        store.add("c2", "game-B")
        store.clear("game-A")
        assertTrue(store.ids("game-A").isEmpty())
        assertEquals(setOf("c2"), store.ids("game-B"))
    }

    @Test
    fun `a fresh store on the same backing reads the persisted ids`() {
        store.add("c1", "game-1")
        store.add("c2", "game-1")

        val fresh = PendingChallengeStore(prefs)
        assertEquals(setOf("c1", "c2"), fresh.ids("game-1"))
    }
}
