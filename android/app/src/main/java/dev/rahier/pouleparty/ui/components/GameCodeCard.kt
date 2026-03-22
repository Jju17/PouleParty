package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.theme.*

@Composable
fun GameCodeCard(
    gameCode: String,
    codeCopied: Boolean,
    onCodeCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                gameCode,
                fontFamily = GameBoyFont,
                fontSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                copyToClipboard(context, "Game Code", gameCode)
                onCodeCopied()
            }) {
                Icon(
                    imageVector = if (codeCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.copy_game_code),
                    tint = if (codeCopied) Success else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}
