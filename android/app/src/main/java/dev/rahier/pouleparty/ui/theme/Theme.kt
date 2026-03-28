package dev.rahier.pouleparty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CROrange,
    secondary = CRPink,
    background = CRBeige,
    surface = CRBeige,
    surfaceVariant = CRSurfaceVariant,
    onSurfaceVariant = CROnSurfaceVariant,
    secondaryContainer = CRSecondaryContainer,
    onSecondaryContainer = CROnSecondaryContainer,
    outline = CROutline,
    outlineVariant = CROutline,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

private val DarkColorScheme = darkColorScheme(
    primary = CROrangeDark,
    secondary = CRPinkDark,
    background = CRDarkBackground,
    surface = CRDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

// Gradient brushes
val GradientFire = Brush.linearGradient(listOf(CROrange, CRPink))
val GradientChicken = Brush.linearGradient(listOf(ChickenYellow, CROrange))
val GradientHunter = Brush.linearGradient(listOf(HunterRed, CRPink))
val GradientBackgroundWarmth = Brush.radialGradient(listOf(CRBeige, CRBeigeWarm))

@Composable
fun PoulePartyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
