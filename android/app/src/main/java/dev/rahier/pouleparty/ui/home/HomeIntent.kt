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
    object JoinSheetDismissed : HomeIntent
    object ToggleMusic : HomeIntent
    object ActiveGameDismissed : HomeIntent
    object RejoinActiveGameTapped : HomeIntent
    object RegisterTapped : HomeIntent
    object SubmitRegistrationTapped : HomeIntent
    object JoinValidatedGameTapped : HomeIntent
    object PendingRegistrationJoinTapped : HomeIntent
    object RefreshActiveGame : HomeIntent
    data class GameCodeChanged(val code: String) : HomeIntent
    data class TeamNameChanged(val name: String) : HomeIntent
}
