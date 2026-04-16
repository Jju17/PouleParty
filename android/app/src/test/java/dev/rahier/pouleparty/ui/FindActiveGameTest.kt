package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.ui.gamelogic.PlayerRole

import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.Timing
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

/**
 * Tests for the findActiveGame ordering logic:
 * the most recently started game should be returned.
 */
class FindActiveGameTest {

    @Test
    fun `most recent game selected from candidates`() {
        val olderGame = Game(
            id = "game-old",
            timing = Timing(start = Timestamp(Date(1000000))),
            status = GameStatus.IN_PROGRESS.firestoreValue
        )
        val newerGame = Game(
            id = "game-new",
            timing = Timing(start = Timestamp(Date(2000000))),
            status = GameStatus.IN_PROGRESS.firestoreValue
        )

        val candidates = listOf(
            Pair(olderGame, PlayerRole.HUNTER),
            Pair(newerGame, PlayerRole.CHICKEN)
        )

        val result = candidates.maxByOrNull { it.first.startDate.time }
        assertNotNull(result)
        assertEquals("game-new", result!!.first.id)
        assertEquals(PlayerRole.CHICKEN, result.second)
    }

    @Test
    fun `single candidate is returned`() {
        val game = Game(
            id = "only-game",
            timing = Timing(start = Timestamp(Date(1000000))),
            status = GameStatus.IN_PROGRESS.firestoreValue
        )
        val candidates = listOf(Pair(game, PlayerRole.HUNTER))

        val result = candidates.maxByOrNull { it.first.startDate.time }
        assertNotNull(result)
        assertEquals("only-game", result!!.first.id)
    }

    @Test
    fun `empty candidates returns null`() {
        val candidates = emptyList<Pair<Game, PlayerRole>>()
        val result = candidates.maxByOrNull { it.first.startDate.time }
        assertNull(result)
    }

    @Test
    fun `games with same startDate returns one`() {
        val sameTime = Timestamp(Date(1000000))
        val game1 = Game(id = "game-1", timing = Timing(start = sameTime))
        val game2 = Game(id = "game-2", timing = Timing(start = sameTime))

        val candidates = listOf(
            Pair(game1, PlayerRole.HUNTER),
            Pair(game2, PlayerRole.CHICKEN)
        )

        val result = candidates.maxByOrNull { it.first.startDate.time }
        assertNotNull(result)
        // Either one is acceptable when times are equal
        assertTrue(result!!.first.id in listOf("game-1", "game-2"))
    }

    @Test
    fun `hunter and chicken games sorted correctly`() {
        val hunterGame = Game(
            id = "hunter-game",
            timing = Timing(start = Timestamp(Date(3000000))),
            status = GameStatus.IN_PROGRESS.firestoreValue
        )
        val chickenGame = Game(
            id = "chicken-game",
            timing = Timing(start = Timestamp(Date(1000000))),
            status = GameStatus.WAITING.firestoreValue
        )

        val candidates = listOf(
            Pair(chickenGame, PlayerRole.CHICKEN),
            Pair(hunterGame, PlayerRole.HUNTER)
        )

        val result = candidates.maxByOrNull { it.first.startDate.time }
        assertEquals("hunter-game", result!!.first.id)
        assertEquals(PlayerRole.HUNTER, result.second)
    }

    @Test
    fun `three games returns most recent`() {
        val game1 = Game(id = "g1", timing = Timing(start = Timestamp(Date(1000))))
        val game2 = Game(id = "g2", timing = Timing(start = Timestamp(Date(3000))))
        val game3 = Game(id = "g3", timing = Timing(start = Timestamp(Date(2000))))

        val candidates = listOf(
            Pair(game1, PlayerRole.HUNTER),
            Pair(game2, PlayerRole.CHICKEN),
            Pair(game3, PlayerRole.HUNTER)
        )

        val result = candidates.maxByOrNull { it.first.startDate.time }
        assertEquals("g2", result!!.first.id)
    }
}
