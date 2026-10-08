package dev.rahier.pouleparty.ui.gamemastermap

sealed class GameMasterMapIntent {
    data object InfoTapped : GameMasterMapIntent()
    data object DismissGameInfo : GameMasterMapIntent()
    data object HuntersDrawerTapped : GameMasterMapIntent()
    data object DismissHuntersDrawer : GameMasterMapIntent()
    data object LeaveGameTapped : GameMasterMapIntent()
    data object RetryLoad : GameMasterMapIntent()
    data object DismissLeaveError : GameMasterMapIntent()
    data object ValidationQueueTapped : GameMasterMapIntent()
    data class DesignateHunterTapped(val registration: dev.rahier.pouleparty.model.Registration) : GameMasterMapIntent()
    data object DesignateConfirmTapped : GameMasterMapIntent()
    data object DesignateCancelTapped : GameMasterMapIntent()
    data object DesignationErrorDismissed : GameMasterMapIntent()
    data object LaunchTapped : GameMasterMapIntent()
    data object LaunchErrorDismissed : GameMasterMapIntent()
    /** Banner tap at game-end → navigate to the Victory / leaderboard page. */
    data object ViewLeaderboardTapped : GameMasterMapIntent()
    /** Game code "copy to clipboard" tap inside the info dialog. */
    data object CodeCopied : GameMasterMapIntent()
    /** QA panel (debug games only): force the game to end now. */
    data object DebugEndNowTapped : GameMasterMapIntent()
    /**
     * QA panel (debug games only): advance one lifecycle step
     * (launch / shrink+spawn / collapse) without waiting on the clock.
     */
    data object DebugAdvanceStepTapped : GameMasterMapIntent()
}
