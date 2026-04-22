package dev.rahier.pouleparty.ui.settings

import dev.rahier.pouleparty.model.MyGame
import dev.rahier.pouleparty.ui.victory.LeaderboardEntry

/**
 * User-initiated actions on the settings screen. Single entry point via
 * [SettingsViewModel.onIntent] — mirrors the [dev.rahier.pouleparty.ui.home.HomeIntent]
 * pattern.
 */
sealed interface SettingsIntent {
    object DismissGameDetail : SettingsIntent
    object ShowLeaderboard : SettingsIntent
    object DismissLeaderboard : SettingsIntent
    object SaveNickname : SettingsIntent
    object DismissNicknameSaved : SettingsIntent
    object DismissProfanityAlert : SettingsIntent
    object DeleteDataTapped : SettingsIntent
    object DeleteDismissed : SettingsIntent
    object ConfirmDelete : SettingsIntent
    object DeleteSuccessDismissed : SettingsIntent
    object DeleteErrorDismissed : SettingsIntent
    object ReportDismissed : SettingsIntent
    object ReportConfirmed : SettingsIntent
    object ReportResultDismissed : SettingsIntent
    data class NicknameChanged(val name: String) : SettingsIntent
    data class GameSelected(val game: MyGame) : SettingsIntent
    data class ReportInitiated(val gameId: String, val entry: LeaderboardEntry) : SettingsIntent
}
