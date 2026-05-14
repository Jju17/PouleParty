package dev.rahier.pouleparty.ui.gamemastermap

/** User intents emitted by the GameMaster map screen (PP-24). */
sealed class GameMasterMapIntent {
    data object InfoTapped : GameMasterMapIntent()
    data object DismissGameInfo : GameMasterMapIntent()
    data object HuntersDrawerTapped : GameMasterMapIntent()
    data object DismissHuntersDrawer : GameMasterMapIntent()
    data object LeaveGameTapped : GameMasterMapIntent()
    /** PP-86: GM tapped a registered hunter row → open confirmation alert. */
    data class DesignateHunterTapped(val registration: dev.rahier.pouleparty.model.Registration) : GameMasterMapIntent()
    /** PP-86: confirm the pending designation. */
    data object DesignateConfirmTapped : GameMasterMapIntent()
    /** PP-86: dismiss the confirmation alert without acting. */
    data object DesignateCancelTapped : GameMasterMapIntent()
    /** PP-86: dismiss the post-failure error alert. */
    data object DesignationErrorDismissed : GameMasterMapIntent()
}
