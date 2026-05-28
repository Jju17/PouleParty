package dev.rahier.pouleparty.ui.gamemastermap

/** User intents emitted by the GameMaster map screen (PP-24). */
sealed class GameMasterMapIntent {
    data object InfoTapped : GameMasterMapIntent()
    data object DismissGameInfo : GameMasterMapIntent()
    data object HuntersDrawerTapped : GameMasterMapIntent()
    data object DismissHuntersDrawer : GameMasterMapIntent()
    data object LeaveGameTapped : GameMasterMapIntent()
    data object ValidationQueueTapped : GameMasterMapIntent()
    /** PP-86: GM tapped a registered hunter row → open confirmation alert. */
    data class DesignateHunterTapped(val registration: dev.rahier.pouleparty.model.Registration) : GameMasterMapIntent()
    /** PP-86: confirm the pending designation. */
    data object DesignateConfirmTapped : GameMasterMapIntent()
    /** PP-86: dismiss the confirmation alert without acting. */
    data object DesignateCancelTapped : GameMasterMapIntent()
    /** PP-86: dismiss the post-failure error alert. */
    data object DesignationErrorDismissed : GameMasterMapIntent()
    /** PP-71: GM taps LAUNCH when status == READY_TO_LAUNCH. */
    data object LaunchTapped : GameMasterMapIntent()
    /** PP-71: dismisses the error alert after a failed launchGame call. */
    data object LaunchErrorDismissed : GameMasterMapIntent()
    /** Banner tap at game-end → navigate to the Victory / leaderboard page. */
    data object ViewLeaderboardTapped : GameMasterMapIntent()
    /** Game code "copy to clipboard" tap inside the info dialog. */
    data object CodeCopied : GameMasterMapIntent()
}
