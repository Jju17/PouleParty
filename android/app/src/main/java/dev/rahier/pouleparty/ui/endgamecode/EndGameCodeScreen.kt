package dev.rahier.pouleparty.ui.endgamecode

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.GameBoyFont

/**
 * Displays the found code for the chicken to show hunters.
 */
@Composable
fun EndGameCodeContent(foundCode: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Show this code\nto the hunter",
            fontFamily = GameBoyFont,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = foundCode,
            fontFamily = GameBoyFont,
            fontSize = 48.sp,
            letterSpacing = 8.sp
        )

        Text(
            text = "The hunter must enter this code to prove they found you!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
