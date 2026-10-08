package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.gamecreation.StepContainer

@Composable
fun OptionsStep(
    isGameMasterEnabled: Boolean,
    gameMasterPassword: String,
    powerUpsEnabled: Boolean,
    enabledPowerUpTypes: List<String>,
    gameMod: GameMod,
    chickenCanSeeHunters: Boolean,
    onGameMasterEnabledChanged: (Boolean) -> Unit,
    onGameMasterPasswordChanged: (String) -> Unit,
    onTogglePowerUps: (Boolean) -> Unit,
    onPowerUpSelectionTapped: () -> Unit,
    onChickenVisibilityChanged: (Boolean) -> Unit,
) {
    StepContainer(
        title = stringResource(R.string.wizard_options_title),
        subtitle = stringResource(R.string.wizard_options_subtitle),
    ) {
        GameMasterSection(isGameMasterEnabled, gameMasterPassword, onGameMasterEnabledChanged, onGameMasterPasswordChanged)
        HorizontalDivider()
        PowerUpsSection(powerUpsEnabled, enabledPowerUpTypes, gameMod, onTogglePowerUps, onPowerUpSelectionTapped)
        HorizontalDivider()
        ChickenVisibilitySection(chickenCanSeeHunters, onChickenVisibilityChanged)
    }
}
