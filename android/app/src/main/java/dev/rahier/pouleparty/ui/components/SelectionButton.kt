package dev.rahier.pouleparty.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.ui.theme.GameBoyFont

@Composable
fun SelectionButton(
    text: String,
    color: Color = Color.Black,
    lineWidth: Float = 2f,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = color
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .border(lineWidth.dp, color, RoundedCornerShape(10.dp))
    ) {
        Text(
            text = text,
            fontFamily = GameBoyFont,
            fontSize = 20.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}
