package dev.rahier.pouleparty.model

enum class GameMod(val firestoreValue: String, val title: String) {
    FOLLOW_THE_CHICKEN("followTheChicken", "Follow the chicken \uD83D\uDC14"),
    STAY_IN_THE_ZONE("stayInTheZone", "Stay in the zone \uD83D\uDCCD");

    companion object {
        fun fromFirestore(value: String): GameMod =
            entries.firstOrNull { it.firestoreValue == value } ?: FOLLOW_THE_CHICKEN
    }
}
