package dev.rahier.pouleparty.data

import com.google.firebase.Timestamp
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GamePhase
import dev.rahier.pouleparty.model.GameStatus
import dev.rahier.pouleparty.model.PlayerRole
import dev.rahier.pouleparty.model.Timing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GameRepositoryLogicTest {

    private val now = Date(1_800_000_000_000L)

    private fun game(id: String, status: GameStatus, startOffsetMs: Long, endOffsetMs: Long, roles: Map<String, String> = emptyMap()) =
        Game.mock.copy(
            id = id,
            status = status.firestoreValue,
            roles = roles,
            timing = Timing(start = Timestamp(Date(now.time + startOffsetMs)), end = Timestamp(Date(now.time + endOffsetMs))),
        )

    @Test
    fun `zone circles are sorted by order and malformed entries skipped`() {
        val raw = listOf(
            mapOf("order" to 1L, "radiusMeters" to 900.0, "lat" to 50.0, "lng" to 4.0),
            mapOf("order" to 0L, "radiusMeters" to 1000L, "lat" to 50.1, "lng" to 4.1),
            mapOf("order" to 2L, "radiusMeters" to "oops", "lat" to 50.0, "lng" to 4.0),
            "not a map",
        )
        val circles = decodeZoneCircles(raw)
        assertEquals(listOf(0, 1), circles.map { it.order })
        assertEquals(1000.0, circles.first().radiusMeters, 0.0)
    }

    @Test
    fun `zone circles decode to empty for a missing field`() {
        assertTrue(decodeZoneCircles(null).isEmpty())
        assertTrue(decodeZoneCircles(mapOf("a" to 1)).isEmpty())
    }

    @Test
    fun `leaderboard sorts by points then team name then id`() {
        val entries = mapOf(
            "h3" to mapOf("teamName" to "zèbre", "totalPoints" to 10L),
            "h1" to mapOf("teamName" to "Alpha", "totalPoints" to 10L),
            "h2" to mapOf("teamName" to "alpha", "totalPoints" to 10L),
            "h4" to mapOf("teamName" to "Top", "totalPoints" to 25L),
            "h5" to "broken",
        )
        assertEquals(listOf("h4", "h1", "h2", "h3"), decodeLeaderboard(entries).map { it.hunterId })
    }

    @Test
    fun `active game prefers a running game over an upcoming one`() {
        val upcoming = game("up", GameStatus.WAITING, 60_000, 3_600_000)
        val running = game("run", GameStatus.IN_PROGRESS, -60_000, 3_600_000)
        val result = selectActiveGame(listOf(upcoming to PlayerRole.HUNTER, running to PlayerRole.CHICKEN), now)
        assertEquals("run", result?.game?.id)
        assertEquals(GamePhase.IN_PROGRESS, result?.phase)
        assertEquals(PlayerRole.CHICKEN, result?.role)
    }

    @Test
    fun `active game picks the soonest upcoming game`() {
        val later = game("later", GameStatus.WAITING, 7_200_000, 9_000_000)
        val sooner = game("sooner", GameStatus.WAITING, 600_000, 4_000_000)
        assertEquals("sooner", selectActiveGame(listOf(later to PlayerRole.HUNTER, sooner to PlayerRole.HUNTER), now)?.game?.id)
    }

    @Test
    fun `active game ignores finished and overdue games`() {
        val overdue = game("overdue", GameStatus.IN_PROGRESS, -7_200_000, -60_000)
        val done = game("done", GameStatus.DONE, -7_200_000, 3_600_000)
        val started = game("late", GameStatus.WAITING, -60_000, 3_600_000)
        assertNull(selectActiveGame(listOf(overdue to PlayerRole.HUNTER, done to PlayerRole.HUNTER, started to PlayerRole.HUNTER), now))
    }

    @Test
    fun `role resolution reads the roles map`() {
        val g = Game.mock.copy(roles = mapOf("c" to "chicken", "h" to "hunter", "gm" to "gameMaster"))
        assertEquals(PlayerRole.CHICKEN, roleOf(g, "c"))
        assertEquals(PlayerRole.HUNTER, roleOf(g, "h"))
        assertEquals(PlayerRole.GAME_MASTER, roleOf(g, "gm"))
        assertNull(roleOf(g, "stranger"))
    }
}
