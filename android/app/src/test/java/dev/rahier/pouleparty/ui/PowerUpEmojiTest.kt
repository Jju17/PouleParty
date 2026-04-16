package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.model.PowerUpType
import dev.rahier.pouleparty.ui.powerupselection.powerUpColor
import dev.rahier.pouleparty.ui.powerupselection.powerUpEmoji
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests ensuring all power-up types have emoji and color mappings.
 */
class PowerUpEmojiTest {

    @Test
    fun `every power-up type has an emoji`() {
        PowerUpType.entries.forEach { type ->
            val emoji = powerUpEmoji(type)
            assertTrue("PowerUpType $type should have a non-empty emoji", emoji.isNotEmpty())
        }
    }

    @Test
    fun `every power-up type has a color`() {
        PowerUpType.entries.forEach { type ->
            val color = powerUpColor(type)
            assertNotNull("PowerUpType $type should have a color", color)
        }
    }

    @Test
    fun `all emojis are distinct`() {
        val emojis = PowerUpType.entries.map { powerUpEmoji(it) }
        assertEquals("All emojis should be unique", emojis.size, emojis.toSet().size)
    }

    @Test
    fun `known emoji mappings are correct`() {
        assertEquals("🔮", powerUpEmoji(PowerUpType.ZONE_PREVIEW))
        assertEquals("📡", powerUpEmoji(PowerUpType.RADAR_PING))
        assertEquals("👻", powerUpEmoji(PowerUpType.INVISIBILITY))
        assertEquals("❄️", powerUpEmoji(PowerUpType.ZONE_FREEZE))
        assertEquals("🎭", powerUpEmoji(PowerUpType.DECOY))
        assertEquals("📶", powerUpEmoji(PowerUpType.JAMMER))
    }
}
