package dev.rahier.pouleparty.model

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Integration-style tests validating complete game lifecycle flows
 * at the model and pure-function level.
 */
class GameFlowTest {

    @Test
    fun `full game lifecycle - create, join, find winner`() {
        // 1. Chicken creates game
        val game = Game(
            id = "test-game-id",
            name = "Test Game",
            foundCode = Game.generateFoundCode(),
            status = GameStatus.WAITING.firestoreValue
        )
        assertEquals(GameStatus.WAITING, game.gameStatusEnum)
        assertTrue(game.foundCode.length == 4)
        assertEquals("TEST-G", game.gameCode)

        // 2. Game starts
        val startedGame = game.copy(status = GameStatus.IN_PROGRESS.firestoreValue)
        assertEquals(GameStatus.IN_PROGRESS, startedGame.gameStatusEnum)

        // 3. Hunter joins
        val withHunter = startedGame.copy(hunterIds = listOf("hunter-1"))
        assertEquals(1, withHunter.hunterIds.size)

        // 4. Hunter finds chicken, adds winner
        val winner = Winner("hunter-1", "Alice", Timestamp.now())
        val withWinner = withHunter.copy(winners = listOf(winner))
        assertEquals(1, withWinner.winners.size)
        assertEquals("Alice", withWinner.winners.first().hunterName)

        // 5. Game ends
        val endedGame = withWinner.copy(status = GameStatus.DONE.firestoreValue)
        assertEquals(GameStatus.DONE, endedGame.gameStatusEnum)
    }

    @Test
    fun `game code is consistent between platforms`() {
        val game = Game(id = "abcdef-1234-5678")
        assertEquals("ABCDEF", game.gameCode)
        assertEquals(6, game.gameCode.length)
    }

    @Test
    fun `radius shrinks correctly over time`() {
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
        assertTrue("Radius should have shrunk from 1500", radius < 1500)
    }

    @Test
    fun `radius stays at initial when game not started`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() + 600_000)),
                end = Timestamp(Date(System.currentTimeMillis() + 3_600_000))
            ),
            zone = Zone(
                shrinkIntervalMinutes = 5.0,
                radius = 1500.0,
                shrinkMetersPerUpdate = 100.0
            )
        )

        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `zero radius interval does not cause infinite loop`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 600_000))
            ),
            zone = Zone(
                shrinkIntervalMinutes = 0.0,
                radius = 1500.0
            )
        )

        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `negative radius interval does not cause infinite loop`() {
        val game = Game(
            id = "test",
            timing = Timing(
                start = Timestamp(Date(System.currentTimeMillis() - 600_000))
            ),
            zone = Zone(
                shrinkIntervalMinutes = -5.0,
                radius = 1500.0
            )
        )

        val (_, radius) = game.findLastUpdate()
        assertEquals(1500, radius)
    }

    @Test
    fun `multiple winners accumulate correctly`() {
        val game = Game(id = "test")
        val w1 = Winner("h1", "Alice", Timestamp.now())
        val w2 = Winner("h2", "Bob", Timestamp.now())

        val g1 = game.copy(winners = listOf(w1))
        val g2 = g1.copy(winners = listOf(w1, w2))

        assertEquals(1, g1.winners.size)
        assertEquals(2, g2.winners.size)
        assertEquals("Bob", g2.winners.last().hunterName)
    }

    @Test
    fun `all game modes serialize correctly`() {
        for (mod in GameMod.entries) {
            val game = Game(id = "test", gameMode = mod.firestoreValue)
            assertEquals(mod, game.gameModEnum)
        }
    }

    @Test
    fun `all game statuses serialize correctly`() {
        for (status in GameStatus.entries) {
            val game = Game(id = "test", status = status.firestoreValue)
            assertEquals(status, game.gameStatusEnum)
        }
    }

    @Test
    fun `chicken head start offsets hunter start date`() {
        val game = Game(
            id = "test",
            timing = Timing(
                headStartMinutes = 5.0,
                start = Timestamp(Date(1000000))
            )
        )
        val expectedHunterStart = 1000000L + (5 * 60 * 1000)
        assertEquals(expectedHunterStart, game.hunterStartDate.time)
    }
}
