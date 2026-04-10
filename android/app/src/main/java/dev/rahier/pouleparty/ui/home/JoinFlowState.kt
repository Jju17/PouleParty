package dev.rahier.pouleparty.ui.home

import dev.rahier.pouleparty.model.Game

sealed class JoinFlowStep {
    object EnteringCode : JoinFlowStep()
    object Validating : JoinFlowStep()
    data class CodeValidated(val game: Game, val alreadyRegistered: Boolean) : JoinFlowStep()
    object CodeNotFound : JoinFlowStep()
    object NetworkError : JoinFlowStep()
    data class RegistrationClosed(val game: Game) : JoinFlowStep()
    data class Registering(val game: Game) : JoinFlowStep()
    data class SubmittingRegistration(val game: Game) : JoinFlowStep()
}

data class PendingRegistration(
    val gameId: String,
    val gameCode: String,
    val teamName: String,
    val startMs: Long,
    val isFinished: Boolean = false
)
