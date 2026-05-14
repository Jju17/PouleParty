package dev.rahier.pouleparty.ui.gamecreation

enum class GameCreationStep {
    PARTICIPATION,
    CHICKEN_SELECTION,
    MAX_PLAYERS,
    GAME_MODE,
    /** PP-70 / PP-88: chicken sets the optional 4-digit GameMaster password. */
    GAME_MASTER_PASSWORD,
    ZONE_SETUP,
    START_TIME,
    DURATION,
    HEAD_START,
    POWER_UPS,
    CHICKEN_SEES_HUNTERS,
    REGISTRATION,
    RECAP
}
