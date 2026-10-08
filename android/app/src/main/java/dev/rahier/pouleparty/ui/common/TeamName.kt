package dev.rahier.pouleparty.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R

/** Players always pick a team name; this covers old records that have none. */
@Composable
fun teamNameOrDefault(name: String): String = name.ifBlank { stringResource(R.string.default_team_name) }
