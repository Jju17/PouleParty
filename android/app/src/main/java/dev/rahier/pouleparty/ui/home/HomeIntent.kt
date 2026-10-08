package dev.rahier.pouleparty.ui.home

/**
 * Every user-initiated action the [HomeViewModel] can process.
 * Mirrors the iOS TCA nested `Action.View` pattern, the screen
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
    object CreatePartyLongPressed : HomeIntent
    object JoinSheetDismissed : HomeIntent
    object ToggleMusic : HomeIntent
    object ActiveGameDismissed : HomeIntent
    object RejoinActiveGameTapped : HomeIntent
    object JoinAsHunterTapped : HomeIntent
    data class ValidationCodeChanged(val code: String) : HomeIntent
    object SubmitValidationCodeTapped : HomeIntent
    object SubmitJoinTapped : HomeIntent
    object RefreshActiveGame : HomeIntent
    object AdminCodeDismissed : HomeIntent
    object AdminCodeErrorDismissed : HomeIntent
    data class GameCodeChanged(val code: String) : HomeIntent
    data class TeamNameChanged(val name: String) : HomeIntent
    data class AdminCodeChanged(val code: String) : HomeIntent
    object JoinAsGameMasterTapped : HomeIntent
    data class GameMasterPasswordChanged(val code: String) : HomeIntent
    object SubmitGameMasterPasswordTapped : HomeIntent
    /**
     * Hidden demo-mode entry: long-press the START button to open the
     * App Review code dialog. Reviewers tap through gameplay screens
     * with mocked data instead of needing a real game / GPS fix.
     */
    object StartButtonLongPressed : HomeIntent
    object DemoCodeDismissed : HomeIntent
    object DemoCodeErrorDismissed : HomeIntent
    data class DemoCodeChanged(val code: String) : HomeIntent
}
