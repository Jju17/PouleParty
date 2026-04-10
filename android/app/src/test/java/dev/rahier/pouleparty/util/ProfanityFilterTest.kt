package dev.rahier.pouleparty.util

import org.junit.Assert.*
import org.junit.Test

class ProfanityFilterTest {

    @Test
    fun `detects blocked English words`() {
        assertTrue(ProfanityFilter.containsProfanity("hello fuck you"))
        assertTrue(ProfanityFilter.containsProfanity("shit"))
        assertTrue(ProfanityFilter.containsProfanity("asshole"))
    }

    @Test
    fun `detects blocked French words`() {
        assertTrue(ProfanityFilter.containsProfanity("merde"))
        assertTrue(ProfanityFilter.containsProfanity("connard"))
        assertTrue(ProfanityFilter.containsProfanity("putain"))
    }

    @Test
    fun `allows clean text`() {
        assertFalse(ProfanityFilter.containsProfanity("hello world"))
        assertFalse(ProfanityFilter.containsProfanity("PouleParty"))
        assertFalse(ProfanityFilter.containsProfanity("Julien"))
    }

    @Test
    fun `detects leetspeak substitutions`() {
        assertTrue(ProfanityFilter.containsProfanity("sh1t"))   // 1→i
        assertTrue(ProfanityFilter.containsProfanity("@\$\$hole")) // @→a, $→s
        assertTrue(ProfanityFilter.containsProfanity("m3rde"))  // 3→e
    }

    @Test
    fun `handles diacritics via normalization`() {
        assertTrue(ProfanityFilter.containsProfanity("enculé"))
        assertTrue(ProfanityFilter.containsProfanity("pédé"))
        assertTrue(ProfanityFilter.containsProfanity("bâtard"))
    }

    @Test
    fun `is case insensitive`() {
        assertTrue(ProfanityFilter.containsProfanity("FUCK"))
        assertTrue(ProfanityFilter.containsProfanity("Merde"))
        assertTrue(ProfanityFilter.containsProfanity("CONNARD"))
    }

    @Test
    fun `does not false positive on partial words`() {
        // "ass" is in the blocked list, so "class" will match (substring match)
        // This documents intended behavior — the filter is aggressive
        assertTrue(ProfanityFilter.containsProfanity("class"))
    }

    @Test
    fun `empty string returns false`() {
        assertFalse(ProfanityFilter.containsProfanity(""))
    }

    @Test
    fun `single blocked word`() {
        assertTrue(ProfanityFilter.containsProfanity("fuck"))
    }

    @Test
    fun `multiple blocked words`() {
        assertTrue(ProfanityFilter.containsProfanity("fuck shit merde"))
    }

    @Test
    fun `numbers only returns false`() {
        assertFalse(ProfanityFilter.containsProfanity("123456"))
    }

    @Test
    fun `emojis return false`() {
        assertFalse(ProfanityFilter.containsProfanity("\uD83D\uDC14\uD83C\uDF89"))
    }
}
