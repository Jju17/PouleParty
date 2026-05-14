package dev.rahier.pouleparty.ui.home

/**
 * Every user-initiated action the [HomeViewModel] can process.
 * Mirrors the iOS TCA nested `Action.View` pattern — the screen
 * dispatches exactly one `HomeIntent` per interaction, avoiding
 * scattered method calls.
 */
sealed interface HomeIntent {
    object StartButtonTapped : HomeIntent
    object RulesTapped : HomeIntent
    object RulesDismissed : HomeIntent
    object GameNotFoundDismissed : HomeIntent
    object LocationRequiredDismissed : HomeIntent
    object LocationPermissionDenied : HomeIntent
    object CreatePartyTapped : HomeIntent
    /**
     * Long-press easter egg on the Create Party button. Spawns a preset
     * stayInTheZone game (1 min start, 1 h long, current location or
     * Brussels fallback as both start and final center) and drops the
     * user straight onto the chicken map with every future shrunk circle
     * pre-rendered in its own color for side-by-side drift inspection.
     */
    object CreatePartyLongPressed : HomeIntent
    object JoinSheetDismissed : HomeIntent
    object ToggleMusic : HomeIntent
    object ActiveGameDismissed : HomeIntent
    object RejoinActiveGameTapped : HomeIntent
    object RegisterTapped : HomeIntent
    object SubmitRegistrationTapped : HomeIntent
    object JoinValidatedGameTapped : HomeIntent
    object PendingRegistrationJoinTapped : HomeIntent
    object RefreshActiveGame : HomeIntent
    /** PP-45: opens the admin-code dialog on Home. */
    object AdminModeTapped : HomeIntent
    /** PP-45: user dismissed the admin-code dialog (Cancel). */
    object AdminCodeDismissed : HomeIntent
    /** PP-45: user dismissed the wrong-code error alert. */
    object AdminCodeErrorDismissed : HomeIntent
    /** Placeholder for the "Envie de créer une partie ?" web CTA. Wired in PP-46. */
    object WebCreatePartyTapped : HomeIntent
    data class GameCodeChanged(val code: String) : HomeIntent
    data class TeamNameChanged(val name: String) : HomeIntent
    data class AdminCodeChanged(val code: String) : HomeIntent
    /** PP-88: user picked "Join as GameMaster" on the CodeValidated step. */
    object JoinAsGameMasterTapped : HomeIntent
    /** PP-88: user typed into the 4-digit GM password field. */
    data class GameMasterPasswordChanged(val code: String) : HomeIntent
    /** PP-88: user tapped Submit on the GM password entry step. */
    object SubmitGameMasterPasswordTapped : HomeIntent
}
