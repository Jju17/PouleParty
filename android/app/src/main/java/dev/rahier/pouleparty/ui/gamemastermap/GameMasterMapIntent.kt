package dev.rahier.pouleparty.ui.gamemastermap

/** User intents emitted by the GameMaster map screen (PP-24). */
sealed class GameMasterMapIntent {
    data object InfoTapped : GameMasterMapIntent()
    data object DismissGameInfo : GameMasterMapIntent()
    data object HuntersDrawerTapped : GameMasterMapIntent()
    data object DismissHuntersDrawer : GameMasterMapIntent()
    data object LeaveGameTapped : GameMasterMapIntent()
}
