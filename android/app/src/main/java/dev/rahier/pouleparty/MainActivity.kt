package dev.rahier.pouleparty

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.rahier.pouleparty.data.DeeplinkBus
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
        // PP-52 — capture any App Link the activity was launched with.
        handleDeeplink(intent)
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

    /** PP-52 — App Link arriving while the Activity is already alive
     *  (warm resume) gets handled here. `singleTop` isn't configured;
     *  the default launch mode delivers fresh Intents through this
     *  hook when the activity is brought to the foreground. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeeplink(intent)
    }

    private fun handleDeeplink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data: Uri = intent.data ?: return
        if (data.host != "pouleparty.be") return
        val path = data.path ?: return
        if (path != "/join" && !path.startsWith("/join/")) return
        val code = data.getQueryParameter("code") ?: return
        DeeplinkBus.postValidationCode(code)
    }
}
