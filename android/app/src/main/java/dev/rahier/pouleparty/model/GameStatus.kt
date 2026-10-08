package dev.rahier.pouleparty.model

enum class GameStatus(val firestoreValue: String) {
    WAITING("waiting"),
    READY_TO_LAUNCH("readyToLaunch"),
    IN_PROGRESS("inProgress"),
    DONE("done");

    companion object {
        fun fromFirestore(value: String): GameStatus =
            entries.firstOrNull { it.firestoreValue == value } ?: WAITING
    }
}
