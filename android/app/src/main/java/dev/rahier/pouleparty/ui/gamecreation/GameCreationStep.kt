package dev.rahier.pouleparty.ui.gamecreation

enum class GameCreationStep {
    PARTICIPATION,
    CHICKEN_SELECTION,
    MAX_PLAYERS,
    GAME_MODE,
    /** PP-70 / PP-88: chicken sets the optional 4-digit GameMaster password. */
    GAME_MASTER_PASSWORD,
    /**
     * PP-11: start pin. In `stayInTheZone` the radius is left at its
     * default and recomputed at the recap step (PP-13). In
     * `followTheChicken` a small / medium / large picker sets it.
     */
    START_ZONE_SETUP,
    /**
     * PP-12: final pin (stayInTheZone only). Skipped entirely in
     * `followTheChicken` — the zone follows the chicken's live position
     * and has no `finalCenter`.
     */
    FINAL_ZONE_SETUP,
    /**
     * PP-13: zone recap. Computes the initial radius from the placed
     * pins (stayInTheZone) or echoes the picked size (followTheChicken),
     * then renders every future shrunk circle so the chicken can
     * preview the trajectory. PP-14: a Shuffle button on the same
     * screen regenerates `driftSeed`. Hidden in followTheChicken
     * (no drift in that mode).
     */
    ZONES_RECAP,
    START_TIME,
    DURATION,
    HEAD_START,
    POWER_UPS,
    CHICKEN_SEES_HUNTERS,
    RECAP
}
