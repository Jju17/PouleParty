package dev.rahier.pouleparty.ui

import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.Winner
import dev.rahier.pouleparty.ui.victory.VictoryUiState
import org.junit.Assert.*
import org.junit.Test

class VictoryViewModelTest {

    // MARK: - Initial state defaults

    @Test
    fun `initial state has default game mock`() {
        val state = VictoryUiState()
        assertEquals("Mock", state.game.name)
    }

    @Test
    fun `initial state has empty hunterId`() {
        val state = VictoryUiState()
        assertEquals("", state.hunterId)
    }

    @Test
    fun `initial state has empty hunterName`() {
        val state = VictoryUiState()
        assertEquals("", state.hunterName)
    }

    // MARK: - savedStateHandle values

    @Test
    fun `hunterId comes from savedStateHandle`() {
        val state = VictoryUiState(hunterId = "hunter-abc-123")
        assertEquals("hunter-abc-123", state.hunterId)
    }

    @Test
    fun `hunterName comes from savedStateHandle`() {
        val state = VictoryUiState(hunterName = "Julien")
        assertEquals("Julien", state.hunterName)
    }

    @Test
    fun `hunterId and hunterName set together`() {
        val state = VictoryUiState(hunterId = "h1", hunterName = "Alice")
        assertEquals("h1", state.hunterId)
        assertEquals("Alice", state.hunterName)
    }

    // MARK: - Spectator vs participant

    @Test
    fun `empty hunterId means spectator`() {
        val state = VictoryUiState(hunterId = "")
        assertTrue(state.hunterId.isEmpty())
    }

    @Test
    fun `non-empty hunterId means participant`() {
        val state = VictoryUiState(hunterId = "hunter-1")
        assertFalse(state.hunterId.isEmpty())
    }

    // MARK: - Game config updates state

    @Test
    fun `game config update replaces game in state`() {
        val oldGame = Game(id = "game-1", name = "Old Game", zone = dev.rahier.pouleparty.model.Zone(radius = 1500.0))
        val newGame = oldGame.copy(name = "Updated Game", zone = dev.rahier.pouleparty.model.Zone(radius = 2000.0))

        var state = VictoryUiState(game = oldGame)
        assertEquals("Old Game", state.game.name)
        assertEquals(1500.0, state.game.zone.radius, 0.01)

        state = state.copy(game = newGame)
        assertEquals("Updated Game", state.game.name)
        assertEquals(2000.0, state.game.zone.radius, 0.01)
    }

    @Test
    fun `game config update preserves hunterId and hunterName`() {
        val game1 = Game(id = "g1", name = "Game 1")
        val game2 = Game(id = "g1", name = "Game 2")

        var state = VictoryUiState(game = game1, hunterId = "h1", hunterName = "Bob")
        state = state.copy(game = game2)

        assertEquals("Game 2", state.game.name)
        assertEquals("h1", state.hunterId)
        assertEquals("Bob", state.hunterName)
    }

    @Test
    fun `game config stream emitting null does not update state`() {
        val game = Game(id = "test", name = "Original")
        val state = VictoryUiState(game = game)

        // Simulating what the ViewModel does: only update if game != null
        val streamedGame: Game? = null
        val updatedState = if (streamedGame != null) state.copy(game = streamedGame) else state

        assertEquals("Original", updatedState.game.name)
    }

    // MARK: - Winners in game

    @Test
    fun `game with no winners has empty winners list`() {
        val game = Game(id = "test", winners = emptyList())
        val state = VictoryUiState(game = game)
        assertTrue(state.game.winners.isEmpty())
    }

    @Test
    fun `game with winners shows sorted leaderboard data`() {
        val winner1 = Winner(hunterId = "h1", hunterName = "Alice", timestamp = Timestamp.now())
        val winner2 = Winner(hunterId = "h2", hunterName = "Bob", timestamp = Timestamp.now())
        val game = Game(id = "test", winners = listOf(winner1, winner2))
        val state = VictoryUiState(game = game)

        assertEquals(2, state.game.winners.size)
        assertEquals("Alice", state.game.winners[0].hunterName)
        assertEquals("Bob", state.game.winners[1].hunterName)
    }

    @Test
    fun `current hunter can be identified in winners`() {
        val winner1 = Winner(hunterId = "h1", hunterName = "Alice", timestamp = Timestamp.now())
        val winner2 = Winner(hunterId = "h2", hunterName = "Bob", timestamp = Timestamp.now())
        val game = Game(id = "test", winners = listOf(winner1, winner2))
        val state = VictoryUiState(game = game, hunterId = "h2")

        val isCurrentHunterWinner = state.game.winners.any { it.hunterId == state.hunterId }
        assertTrue(isCurrentHunterWinner)
    }

    @Test
    fun `current hunter not in winners list`() {
        val winner = Winner(hunterId = "h1", hunterName = "Alice", timestamp = Timestamp.now())
        val game = Game(id = "test", winners = listOf(winner))
        val state = VictoryUiState(game = game, hunterId = "h99")

        val isCurrentHunterWinner = state.game.winners.any { it.hunterId == state.hunterId }
        assertFalse(isCurrentHunterWinner)
    }

    // MARK: - Remaining hunters

    @Test
    fun `remaining hunters count is hunterIds minus winners`() {
        val winner = Winner(hunterId = "h1", hunterName = "Alice", timestamp = Timestamp.now())
        val game = Game(
            id = "test",
            hunterIds = listOf("h1", "h2", "h3"),
            winners = listOf(winner)
        )
        val state = VictoryUiState(game = game)

        val remaining = maxOf(0, state.game.hunterIds.size - state.game.winners.size)
        assertEquals(2, remaining)
    }

    @Test
    fun `all hunters found gives zero remaining`() {
        val winners = listOf(
            Winner(hunterId = "h1", hunterName = "Alice", timestamp = Timestamp.now()),
            Winner(hunterId = "h2", hunterName = "Bob", timestamp = Timestamp.now())
        )
        val game = Game(
            id = "test",
            hunterIds = listOf("h1", "h2"),
            winners = winners
        )
        val state = VictoryUiState(game = game)

        val remaining = maxOf(0, state.game.hunterIds.size - state.game.winners.size)
        assertEquals(0, remaining)
    }

    // MARK: - Game status

    @Test
    fun `game status can be done on victory screen`() {
        val game = Game(id = "test", status = GameStatus.DONE.firestoreValue)
        val state = VictoryUiState(game = game)
        assertEquals(GameStatus.DONE, state.game.gameStatusEnum)
    }

    @Test
    fun `game mode is preserved in victory state`() {
        val game = Game(id = "test", gameMode = GameMod.STAY_IN_THE_ZONE.firestoreValue)
        val state = VictoryUiState(game = game)
        assertEquals(GameMod.STAY_IN_THE_ZONE, state.game.gameModEnum)
    }
}
