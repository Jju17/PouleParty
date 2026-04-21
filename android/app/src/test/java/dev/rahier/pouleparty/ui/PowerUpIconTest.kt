package dev.rahier.pouleparty.ui

import dev.rahier.pouleparty.powerups.model.PowerUpType
import dev.rahier.pouleparty.powerups.selection.powerUpIcon
import org.junit.Assert.*
import org.junit.Test

class PowerUpIconTest {

    @Test
    fun `every power-up type has an icon`() {
        PowerUpType.entries.forEach { type ->
            val icon = powerUpIcon(type)
            assertNotNull("PowerUpType $type should have an icon", icon)
        }
    }

    @Test
    fun `all icons are distinct`() {
        val icons = PowerUpType.entries.map { powerUpIcon(it) }
        assertEquals("All icons should be unique", icons.size, icons.toSet().size)
    }

    @Test
    fun `icon count matches power-up type count`() {
        val iconCount = PowerUpType.entries.map { powerUpIcon(it) }.size
        assertEquals(PowerUpType.entries.size, iconCount)
    }
}
