package dev.rahier.pouleparty

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.rahier.pouleparty.navigation.AppNavigation
import dev.rahier.pouleparty.ui.theme.CRBeige
import dev.rahier.pouleparty.ui.theme.CRDarkBackground
import dev.rahier.pouleparty.ui.theme.PoulePartyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoulePartyTheme {
                // Keep Activity window background in sync with Compose theme
                // to prevent wrong-color flash during navigation transitions
                val bgColor = if (isSystemInDarkTheme()) CRDarkBackground else CRBeige
                SideEffect {
                    window.setBackgroundDrawable(ColorDrawable(bgColor.toArgb()))
                }
                AppNavigation()
            }
        }
    }
}
