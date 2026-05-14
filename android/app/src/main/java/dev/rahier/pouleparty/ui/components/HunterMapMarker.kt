package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.ChickenYellow
import dev.rahier.pouleparty.ui.theme.CROrange

/**
 * Hunter avatar rendered on the chicken's map (when
 * `chickenCanSeeHunters` is on) and on the GameMaster map. Mirrors
 * iOS `HunterMapMarker`: 24 dp CROrange disc + 13 dp walk glyph,
 * with a team-name label above (white text + black halo so it reads
 * on every Mapbox tile palette).
 */
@Composable
fun HunterMapMarker(displayName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        OutlinedMapLabel(text = displayName)
        Box(
            modifier = Modifier
                .shadow(elevation = 2.dp, shape = CircleShape)
                .size(24.dp)
                .background(CROrange, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/**
 * Chicken avatar rendered on the GameMaster map (PP-24/PP-87). The
 * GM is the only role that ever sees the `invisible` state — the
 * disc fades and a dashed white outline draws around it so the
 * chicken's mid-power-up status is unambiguous.
 */
@Composable
fun GMChickenMarker(isInvisible: Boolean) {
    val emojiAlpha = if (isInvisible) 0.65f else 1f
    val discAlpha = if (isInvisible) 0.45f else 1f
    Box(
        modifier = Modifier.size(if (isInvisible) 28.dp else 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .shadow(elevation = 2.dp, shape = CircleShape)
                .size(24.dp)
                .background(ChickenYellow.copy(alpha = discAlpha), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🐔",
                fontSize = 13.sp,
                color = Color.Black.copy(alpha = emojiAlpha)
            )
        }
        if (isInvisible) {
            Canvas(modifier = Modifier.size(28.dp)) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f - 1.5f,
                    style = Stroke(
                        width = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f))
                    )
                )
            }
        }
    }
}

/**
 * White text with a 1-px black outline (four offset clones). The
 * Mapbox tile palette switches with the system theme, so any single
 * text colour breaks on one of the two — the halo keeps the label
 * legible on every background.
 */
@Composable
private fun OutlinedMapLabel(text: String) {
    val offsets = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    Box {
        offsets.forEach { (dx, dy) ->
            Text(
                text = text,
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(dx.dp, dy.dp)
            )
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}
