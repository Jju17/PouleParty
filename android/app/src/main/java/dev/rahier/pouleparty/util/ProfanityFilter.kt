package dev.rahier.pouleparty.util

/**
 * Simple local profanity filter for nickname validation.
 * Checks against a list of blocked words (FR + EN) with basic leetspeak normalization.
 */
object ProfanityFilter {

    private val blockedWords = setOf(
        // English
        "fuck", "shit", "ass", "asshole", "bitch", "bastard", "dick", "cock",
        "pussy", "cunt", "whore", "slut", "fag", "faggot", "nigger", "nigga",
        "retard", "rape", "rapist", "nazi", "penis", "vagina", "dildo",
        "wanker", "twat", "bollocks", "prick", "motherfucker",
        // French
        "merde", "putain", "salope", "pute", "connard", "connasse", "enculer",
        "encule", "encule", "nique", "ntm", "fdp", "pd", "tapette", "gouine",
        "batard", "batard", "bordel", "bite", "couille", "branleur",
        "branleuse", "tg", "negro", "negre", "pede", "pedale",
    )

    /** Returns `true` when the text contains a blocked word. */
    fun containsProfanity(text: String): Boolean {
        val cleaned = text.lowercase()
            .replace("0", "o")
            .replace("1", "i")
            .replace("3", "e")
            .replace("4", "a")
            .replace("5", "s")
            .replace("@", "a")
            .replace("$", "s")
            // Strip common accents
            .let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")

        return blockedWords.any { cleaned.contains(it) }
    }
}
