package dev.rahier.pouleparty.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CreatePartyUrlTest {

    @Test
    fun `french locale resolves to creer-une-partie`() {
        assertEquals(
            "https://pouleparty.be/creer-une-partie",
            CreatePartyUrl.forLanguage("fr")
        )
    }

    @Test
    fun `dutch locale resolves to een-feestje-organiseren`() {
        assertEquals(
            "https://pouleparty.be/een-feestje-organiseren",
            CreatePartyUrl.forLanguage("nl")
        )
    }

    @Test
    fun `english locale resolves to create-a-party`() {
        assertEquals(
            "https://pouleparty.be/create-a-party",
            CreatePartyUrl.forLanguage("en")
        )
    }

    @Test
    fun `unknown locale falls back to english`() {
        assertEquals(
            "https://pouleparty.be/create-a-party",
            CreatePartyUrl.forLanguage("de")
        )
    }

    @Test
    fun `null locale falls back to english`() {
        assertEquals(
            "https://pouleparty.be/create-a-party",
            CreatePartyUrl.forLanguage(null)
        )
    }

    @Test
    fun `mixed case locale falls back to english`() {
        // `Locale.getDefault().language` returns lowercase ISO 639-1; anything
        // else hits the fallback rather than matching unexpectedly.
        assertEquals(
            "https://pouleparty.be/create-a-party",
            CreatePartyUrl.forLanguage("FR")
        )
    }
}
