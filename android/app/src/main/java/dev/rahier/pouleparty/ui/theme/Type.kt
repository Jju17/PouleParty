package dev.rahier.pouleparty.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R

val BangersFont = FontFamily(
    Font(R.font.bangers_regular, FontWeight.Normal)
)

val GameBoyFont = FontFamily(
    Font(R.font.early_gameboy, FontWeight.Normal)
)

fun bangerStyle(size: Int): TextStyle = TextStyle(
    fontFamily = BangersFont,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    letterSpacing = 0.5.sp
)

fun gameboyStyle(size: Int): TextStyle = TextStyle(
    fontFamily = GameBoyFont,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp
)
