package dev.rahier.pouleparty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.rahier.pouleparty.navigation.AppNavigation
import dev.rahier.pouleparty.ui.theme.PoulePartyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoulePartyTheme {
                AppNavigation()
            }
        }
    }
}
