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
     * Hidden admin-mode entry: long-press the Create Party button to open
     * the admin code dialog (PP-45). The password isn't advertised via a
     * visible button so Apple reviewers don't surface it.
     */
    object CreatePartyLongPressed : HomeIntent
    object JoinSheetDismissed : HomeIntent
    object ToggleMusic : HomeIntent
    object ActiveGameDismissed : HomeIntent
    object RejoinActiveGameTapped : HomeIntent
    /** PP-90: on the CodeValidated step, user taps "Rejoindre la partie". */
    object JoinAsHunterTapped : HomeIntent
    /** PP-90: user taps Submit on the teamName entry step. */
    object SubmitJoinTapped : HomeIntent
    object RefreshActiveGame : HomeIntent
    /** PP-45: user dismissed the admin-code dialog (Cancel). */
    object AdminCodeDismissed : HomeIntent
    /** PP-45: user dismissed the wrong-code error alert. */
    object AdminCodeErrorDismissed : HomeIntent
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
