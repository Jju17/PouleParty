package dev.rahier.pouleparty.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.common.ActionErrorDialog
import dev.rahier.pouleparty.ui.common.ConnectionLostBanner
import dev.rahier.pouleparty.ui.theme.PoulePartyTheme

@Preview(name = "Buttons, light")
@Preview(name = "Buttons, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ButtonsPreview() {
    PoulePartyTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(text = "Join", onClick = {})
                PrimaryButton(text = "Join", onClick = {}, enabled = false)
                PrimaryButton(text = "Join", onClick = {}, isLoading = true)
                SecondaryButton(text = "Join as GameMaster", onClick = {})
                DangerButton(text = "Delete account", onClick = {})
                ConnectionLostBanner()
                HunterMapMarker(displayName = "Les Renards")
            }
        }
    }
}

@Preview(name = "Action error, light")
@Preview(name = "Action error, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActionErrorPreview() {
    PoulePartyTheme {
        ActionErrorDialog(
            title = R.string.leave_game_failed_title,
            message = R.string.api_error_network,
            onRetry = {},
            onDismiss = {},
        )
    }
}
