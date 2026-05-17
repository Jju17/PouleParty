package dev.rahier.pouleparty.ui.home

import dev.rahier.pouleparty.model.Game

sealed class JoinFlowStep {
    object EnteringCode : JoinFlowStep()
    object Validating : JoinFlowStep()
    data class CodeValidated(val game: Game) : JoinFlowStep()
    object CodeNotFound : JoinFlowStep()
    object NetworkError : JoinFlowStep()
    /** PP-90: collect teamName before joining; required for every hunter. */
    data class JoiningWithTeamName(val game: Game) : JoinFlowStep()
    /** PP-90: registration doc + join in flight. */
    data class SubmittingJoin(val game: Game) : JoinFlowStep()
    /** PP-88: chicken enabled GM role; user picked "Join as GM". */
    data class GameMasterPasswordEntry(val game: Game) : JoinFlowStep()
    /** PP-88: joinAsGameMaster CF in flight. */
    data class SubmittingGameMasterPassword(val game: Game) : JoinFlowStep()
    /** PP-52: resolved game has a `registrationBatchId`; gate the join
     *  on the validation code the hunter received by email. */
    data class ValidationCodeEntry(val game: Game) : JoinFlowStep()
    /** PP-52: `validateRegistrationCode` Firestore query in flight. */
    data class SubmittingValidationCode(val game: Game) : JoinFlowStep()
    /** PP-52 deeplink — `lookupGameByValidationCode` in flight. */
    data class ResolvingDeeplink(val code: String) : JoinFlowStep()
    /** PP-52 deeplink — inscription is valid but the in-app Game hasn't
     *  been created yet (D-Day day-of before the chicken configures
     *  the party). */
    data class DeeplinkGameNotYetReady(val code: String) : JoinFlowStep()
    /** PP-52 deeplink — no `eventRegistration` matches the code. */
    object DeeplinkInvalidCode : JoinFlowStep()
}
