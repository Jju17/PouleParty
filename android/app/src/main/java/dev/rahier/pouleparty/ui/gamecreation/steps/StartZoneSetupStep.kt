package dev.rahier.pouleparty.ui.gamecreation.steps

import dev.rahier.pouleparty.ui.theme.AccentText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.model.Game
import dev.rahier.pouleparty.model.GameMod
import dev.rahier.pouleparty.ui.chickenmapconfig.ChickenMapConfigScreen
import dev.rahier.pouleparty.ui.chickenmapconfig.MapConfigPinMode
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

@Composable
fun StartZoneSetupStep(
    game: Game,
    isStartZoneConfigured: Boolean,
    onLocationSelected: (Point) -> Unit,
    onRadiusChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFollowMode = game.gameModEnum == GameMod.FOLLOW_THE_CHICKEN
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.start_zone),
                style = bangerStyle(28),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isFollowMode)
                    stringResource(R.string.start_zone_step_subtitle_follow)
                else
                    stringResource(R.string.start_zone_step_subtitle_stay),
                style = gameboyStyle(10),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            if (!isStartZoneConfigured) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.set_start_zone),
                    style = gameboyStyle(10),
                    color = AccentText,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ChickenMapConfigScreen(
                initialRadius = game.zone.radius,
                finalMarker = game.finalLocation,
                onLocationSelected = onLocationSelected,
                onFinalLocationSelected = {  },
                onRadiusChanged = onRadiusChanged,
                isFollowMode = isFollowMode,
                forcedPinMode = MapConfigPinMode.START,
            )
        }
    }
}
