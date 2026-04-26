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
    object PaymentVerificationDismissed : HomeIntent
    data class GameCodeChanged(val code: String) : HomeIntent
    data class TeamNameChanged(val name: String) : HomeIntent
}
