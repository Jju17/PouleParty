package dev.rahier.pouleparty.model

enum class GameStatus(val firestoreValue: String) {
    WAITING("waiting"),
    /**
     * PP-71: only reached when `manualStartEnabled == true`. Set at
     * `timing.start`; the `launchGame` callable advances it to
     * `IN_PROGRESS` and stamps `timing.actualStart`.
     */
    READY_TO_LAUNCH("readyToLaunch"),
    IN_PROGRESS("inProgress"),
    DONE("done");

    companion object {
        fun fromFirestore(value: String): GameStatus =
            entries.firstOrNull { it.firestoreValue == value } ?: WAITING
    }
}
