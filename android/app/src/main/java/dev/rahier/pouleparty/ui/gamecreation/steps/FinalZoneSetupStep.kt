package dev.rahier.pouleparty.ui.gamecreation.steps

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
import dev.rahier.pouleparty.ui.chickenmapconfig.ChickenMapConfigScreen
import dev.rahier.pouleparty.ui.chickenmapconfig.MapConfigPinMode
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle

/**
 * PP-12 — final pin step. Only shown in `stayInTheZone` (skipped
 * entirely in `followTheChicken`). The start pin is rendered as a
 * read-only reference; the user can only edit the final pin. Next is
 * gated by `state.isFinalZoneConfigured` (distance ≥ 100 m).
 */
@Composable
fun FinalZoneSetupStep(
    game: Game,
    isFinalZoneConfigured: Boolean,
    onFinalLocationSelected: (Point?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.final_zone),
                style = bangerStyle(28),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isFinalZoneConfigured)
                    stringResource(R.string.final_zone_step_subtitle_done)
                else
                    stringResource(R.string.final_zone_step_subtitle),
                style = gameboyStyle(9),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            if (!isFinalZoneConfigured) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.set_start_and_final_zone),
                    style = gameboyStyle(8),
                    color = CROrange,
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
                onLocationSelected = { /* PP-12 never edits the start */ },
                onFinalLocationSelected = onFinalLocationSelected,
                onRadiusChanged = { /* No slider on PP-12 */ },
                isFollowMode = false,
                forcedPinMode = MapConfigPinMode.FINAL,
            )
        }
    }
}
