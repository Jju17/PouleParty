package dev.rahier.pouleparty.util

import kotlin.random.Random

object RandomNickname {
    private val adjectives = listOf(
        "Brave", "Quick", "Sneaky", "Wild", "Bold", "Sly", "Swift", "Fierce",
        "Clever", "Speedy", "Sharp", "Lucky", "Witty", "Funky", "Crazy", "Spicy",
        "Smooth", "Cosmic", "Mighty", "Tiny", "Mega", "Super", "Ninja", "Stealth",
        "Shadow", "Lightning", "Thunder", "Mystic", "Royal", "Lazy", "Cosy",
        "Feathered", "Fluffy", "Grumpy", "Happy", "Jolly", "Loud", "Rapid",
    )
    private val nouns = listOf(
        "Chicken", "Hunter", "Fox", "Wolf", "Bear", "Eagle", "Tiger", "Lion",
        "Panda", "Penguin", "Falcon", "Hawk", "Shark", "Dragon", "Phoenix",
        "Knight", "Ghost", "Wizard", "Ranger", "Pilot", "Pirate", "Hero",
        "Captain", "Rebel", "Rocket", "Comet", "Star", "Storm", "Bolt", "Coop",
        "Beak", "Feather", "Wing", "Egg", "Hen", "Rooster",
    )

    /**
     * Returns a fresh "AdjectiveNoun##" pseudonym for users who skip the
     * nickname step in onboarding. Capped at 20 chars to match the manual
     * input contract (NICKNAME_MAX_LENGTH).
     */
    fun generate(): String {
        val adj = adjectives.random()
        val noun = nouns.random()
        val suffix = Random.nextInt(0, 100)
        val candidate = "$adj$noun$suffix"
        return if (candidate.length <= 20) candidate else candidate.substring(0, 20)
    }
}
