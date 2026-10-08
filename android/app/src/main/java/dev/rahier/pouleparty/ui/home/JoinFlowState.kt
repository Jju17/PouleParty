package dev.rahier.pouleparty.ui.home

import dev.rahier.pouleparty.model.Game

sealed class JoinFlowStep {
    object EnteringCode : JoinFlowStep()
    object Validating : JoinFlowStep()
    data class CodeValidated(val game: Game) : JoinFlowStep()
    object CodeNotFound : JoinFlowStep()
    /** The resolved game already has `hunterIds.size >= maxPlayers` and the
     *  user isn't a member, terminal "party full" state. */
    object GameFull : JoinFlowStep()
    object NetworkError : JoinFlowStep()
    data class ValidationCodeEntry(val game: Game) : JoinFlowStep()
    data class SubmittingValidationCode(val game: Game) : JoinFlowStep()
    data class JoiningWithTeamName(val game: Game) : JoinFlowStep()
    data class SubmittingJoin(val game: Game) : JoinFlowStep()
    data class GameMasterPasswordEntry(val game: Game) : JoinFlowStep()
    data class SubmittingGameMasterPassword(val game: Game) : JoinFlowStep()
}
