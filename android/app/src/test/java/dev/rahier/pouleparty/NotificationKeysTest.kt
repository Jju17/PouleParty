package dev.rahier.pouleparty

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NotificationKeysTest {

    @Test
    fun `every key the server sends has a translated string`() {
        val functions = listOf(File("../../functions/src"), File("../functions/src")).first { it.isDirectory }
        val serverKeys = functions.walk().filter { it.extension == "ts" }
            .flatMap { Regex("\"(notif_[a-z_]+)\"").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
        assertEquals(serverKeys, PouleFCMService.NOTIFICATION_STRINGS.keys)
    }
}
