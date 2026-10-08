package dev.rahier.pouleparty.model

import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSerializationTest {

    @Suppress("UNCHECKED_CAST")
    private fun serialize(game: Game): Map<String, Any?> =
        CustomClassMapper.convertToPlainJavaTypes(game) as Map<String, Any?>

    @Test
    fun `writes exactly the keys the rules and the server expect`() {
        val keys = serialize(Game.mock).keys
        assertEquals(
            setOf(
                "id", "name", "maxPlayers", "gameMode", "chickenCanSeeHunters", "foundCode",
                "status", "winners", "creatorId", "roles", "hasGameMasterPassword", "timing",
                "zone", "powerUps", "isAdminCreation", "manualStartEnabled", "isDebugGame",
                "registrationBatchId", "gameCode",
            ),
            keys,
        )
    }

    @Test
    fun `uses the iOS names for the admin and debug flags`() {
        val map = serialize(Game.mock.copy(isAdminCreation = true, isDebugGame = true))
        assertEquals(true, map["isAdminCreation"])
        assertEquals(true, map["isDebugGame"])
        assertFalse(map.containsKey("adminCreation"))
        assertFalse(map.containsKey("debugGame"))
    }

    @Test
    fun `reads the flags written by iOS`() {
        val game = CustomClassMapper.convertToCustomClass(
            mapOf("isAdminCreation" to true, "isDebugGame" to true, "name" to "x"),
            Game::class.java,
            null,
        )
        assertTrue(game.isAdminCreation)
        assertTrue(game.isDebugGame)
    }

    @Test
    fun `decodes a 64-bit drift seed written by released iOS versions`() {
        val game = CustomClassMapper.convertToCustomClass(
            mapOf("zone" to mapOf("driftSeed" to 5_764_607_523_034_234_880L)),
            Game::class.java,
            null,
        )
        assertEquals(5_764_607_523_034_234_880L, game.zone.driftSeed)
    }

    @Test
    fun `derives the game code from the document id`() {
        assertEquals("ABCDEF", serialize(Game.mock.copy(id = "abcdefGHIJ"))["gameCode"])
    }
}
