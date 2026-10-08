package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.GameBoyFont
import dev.rahier.pouleparty.ui.theme.GradientFire
import dev.rahier.pouleparty.ui.theme.MinTouchTarget

private val PillShape = RoundedCornerShape(50)

/** Main call to action: black text on the fire gradient (AA contrast), grey when disabled. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val active = enabled && !isLoading
    TextButton(
        onClick = onClick,
        enabled = active,
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .background(if (active) GradientFire else SolidColor(Color.Gray.copy(alpha = 0.3f)), PillShape)
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
            } else {
                Text(text, fontFamily = GameBoyFont, fontSize = 18.sp, color = Color.Black.copy(alpha = if (active) 1f else 0.4f))
            }
        }
    }
}

/** Alternative action, outlined in the text colour. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = MinTouchTarget),
        shape = PillShape,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
    ) {
        Text(text, fontFamily = GameBoyFont, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Destructive action, always placed last in its row. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = MinTouchTarget)
            .background(MaterialTheme.colorScheme.error, PillShape)
            .padding(horizontal = 16.dp),
    ) {
        Text(text, fontFamily = GameBoyFont, fontSize = 14.sp, color = MaterialTheme.colorScheme.onError)
    }
}
