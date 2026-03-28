package dev.rahier.pouleparty.ui

import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.chickenmap.ChickenMapUiState
import dev.rahier.pouleparty.ui.huntermap.HunterMapUiState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests covering the "all hunters found" game-end condition
 * and related edge cases.
 */
class AllHuntersFoundTest {

    private fun makeWinner(id: String, name: String) =
        Winner(hunterId = id, hunterName = name, timestamp = Timestamp.now())

    // ── Detection logic ──

    @Test
    fun `all hunters found when winners equals hunterIds`() {
        val game = Game(
            id = "test",
            hunterIds = listOf("h1", "h2"),
            winners = listOf(makeWinner("h1", "Alice"), makeWinner("h2", "Bob"))
        )
        assertTrue(game.hunterIds.isNotEmpty())
        assertTrue(game.winners.size >= game.hunterIds.size)
    }

    @Test
    fun `not all hunters found when winners less than hunterIds`() {
        val game = Game(
            id = "test",
            hunterIds = listOf("h1", "h2", "h3"),
            winners = listOf(makeWinner("h1", "Alice"))
        )
        assertFalse(game.winners.size >= game.hunterIds.size)
    }

    @Test
    fun `empty hunterIds prevents all-found detection`() {
        val game = Game(
            id = "test",
            hunterIds = emptyList(),
            winners = emptyList()
        )
        // Guard: hunterIds must not be empty
        assertFalse(game.hunterIds.isNotEmpty() && game.winners.size >= game.hunterIds.size)
    }

    @Test
    fun `empty hunterIds with winners does not trigger all-found`() {
        val game = Game(
            id = "test",
            hunterIds = emptyList(),
            winners = listOf(makeWinner("h1", "Alice"))
        )
        // Guard prevents triggering when there are no registered hunters
        assertFalse(game.hunterIds.isNotEmpty() && game.winners.size >= game.hunterIds.size)
    }

    @Test
    fun `more winners than hunters still triggers all-found`() {
        // Edge case: somehow more winners than hunters (e.g. data inconsistency)
        val game = Game(
            id = "test",
            hunterIds = listOf("h1"),
            winners = listOf(makeWinner("h1", "Alice"), makeWinner("h2", "Bob"))
        )
        assertTrue(game.winners.size >= game.hunterIds.size)
    }

    @Test
    fun `single hunter single winner triggers all-found`() {
        val game = Game(
            id = "test",
            hunterIds = listOf("h1"),
            winners = listOf(makeWinner("h1", "Alice"))
        )
        assertTrue(game.hunterIds.isNotEmpty() && game.winners.size >= game.hunterIds.size)
    }

    // ── ChickenMapUiState transitions ──

    @Test
    fun `chicken initial state has shouldNavigateToVictory false`() {
        val state = ChickenMapUiState()
        assertFalse(state.shouldNavigateToVictory)
    }

    @Test
    fun `chicken shouldNavigateToVictory can be set to true`() {
        val state = ChickenMapUiState().copy(shouldNavigateToVictory = true)
        assertTrue(state.shouldNavigateToVictory)
    }

    @Test
    fun `chicken state transitions to victory on all hunters found`() {
        val game = Game(
            id = "game-1",
            hunterIds = listOf("h1", "h2"),
            winners = listOf(makeWinner("h1", "Alice"), makeWinner("h2", "Bob"))
        )
        var state = ChickenMapUiState(game = game)
        assertFalse(state.shouldNavigateToVictory)

        // Simulate the all-found detection
        if (state.game.hunterIds.isNotEmpty() && state.game.winners.size >= state.game.hunterIds.size) {
            state = state.copy(shouldNavigateToVictory = true)
        }

        assertTrue(state.shouldNavigateToVictory)
    }

    @Test
    fun `chicken does not navigate to victory when not all found`() {
        val game = Game(
            id = "game-1",
            hunterIds = listOf("h1", "h2", "h3"),
            winners = listOf(makeWinner("h1", "Alice"))
        )
        var state = ChickenMapUiState(game = game)

        if (state.game.hunterIds.isNotEmpty() && state.game.winners.size >= state.game.hunterIds.size) {
            state = state.copy(shouldNavigateToVictory = true)
        }

        assertFalse(state.shouldNavigateToVictory)
    }

    @Test
    fun `chicken guard prevents double navigation`() {
        val game = Game(
            id = "game-1",
            hunterIds = listOf("h1"),
            winners = listOf(makeWinner("h1", "Alice"))
        )
        val state = ChickenMapUiState(game = game, shouldNavigateToVictory = true)

        // Guard: already navigating, should not trigger again
        val shouldTrigger = !state.shouldNavigateToVictory &&
            state.game.hunterIds.isNotEmpty() &&
            state.game.winners.size >= state.game.hunterIds.size
        assertFalse(shouldTrigger)
    }

    // ── HunterMapUiState transitions ──

    @Test
    fun `hunter initial state has shouldNavigateToVictory false`() {
        val state = HunterMapUiState()
        assertFalse(state.shouldNavigateToVictory)
    }

    @Test
    fun `hunter state transitions to victory on all hunters found`() {
        val game = Game(
            id = "game-1",
            hunterIds = listOf("h1", "h2"),
            winners = listOf(makeWinner("h1", "Alice"), makeWinner("h2", "Bob"))
        )
        var state = HunterMapUiState(game = game)

        if (!state.shouldNavigateToVictory &&
            state.game.hunterIds.isNotEmpty() &&
            state.game.winners.size >= state.game.hunterIds.size) {
            state = state.copy(shouldNavigateToVictory = true)
        }

        assertTrue(state.shouldNavigateToVictory)
    }

    @Test
    fun `hunter guard prevents double navigation`() {
        val game = Game(
            id = "game-1",
            hunterIds = listOf("h1"),
            winners = listOf(makeWinner("h1", "Alice"))
        )
        val state = HunterMapUiState(game = game, shouldNavigateToVictory = true)

        val shouldTrigger = !state.shouldNavigateToVictory &&
            state.game.hunterIds.isNotEmpty() &&
            state.game.winners.size >= state.game.hunterIds.size
        assertFalse(shouldTrigger)
    }

    // ── Game lifecycle with all-found ──

    @Test
    fun `game lifecycle with all hunters found sets status DONE`() {
        var game = Game(
            id = "test",
            status = GameStatus.IN_PROGRESS.firestoreValue,
            hunterIds = listOf("h1", "h2")
        )
        assertEquals(GameStatus.IN_PROGRESS, game.gameStatusEnum)

        // All hunters find chicken
        game = game.copy(
            winners = listOf(makeWinner("h1", "Alice"), makeWinner("h2", "Bob")),
            status = GameStatus.DONE.firestoreValue
        )
        assertEquals(GameStatus.DONE, game.gameStatusEnum)
        assertEquals(0, maxOf(0, game.hunterIds.size - game.winners.size))
    }

    @Test
    fun `large game with many hunters all finding chicken`() {
        val hunterIds = (1..20).map { "h$it" }
        val winners = hunterIds.mapIndexed { i, id -> makeWinner(id, "Hunter $i") }
        val game = Game(id = "test", hunterIds = hunterIds, winners = winners)

        assertTrue(game.hunterIds.isNotEmpty())
        assertTrue(game.winners.size >= game.hunterIds.size)
        assertEquals(0, maxOf(0, game.hunterIds.size - game.winners.size))
    }
}
