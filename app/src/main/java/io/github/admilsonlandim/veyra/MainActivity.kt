package io.github.admilsonlandim.veyra

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admilsonlandim.veyra.data.settings.AppPreferences
import io.github.admilsonlandim.veyra.data.settings.AppearanceMode
import io.github.admilsonlandim.veyra.ui.VeyraApp
import io.github.admilsonlandim.veyra.ui.theme.VeyraTheme

// AppCompatActivity (a ComponentActivity) applies the per-app language on Android 12 and older.
class MainActivity : AppCompatActivity() {

    // Incremented each time an intent asks to show the player (e.g. a tap on the notification).
    private var openPlayerRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On recreation (e.g. rotation) the intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        val appearanceFlow = AppPreferences.get(this).appearance
        setContent {
            val appearance by appearanceFlow.collectAsStateWithLifecycle(AppearanceMode.System)
            val dark = when (appearance) {
                AppearanceMode.System -> isSystemInDarkTheme()
                AppearanceMode.Dark -> true
                AppearanceMode.Light -> false
            }
            // System bar icons follow the app theme: light icons on dark, dark icons on light.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            VeyraTheme(darkTheme = dark) {
                VeyraApp(openPlayerRequests = openPlayerRequests)
            }
        }
    }

    // launchMode="singleTop": a notification tap while the app is open arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Reopening from Recents replays the original intent; that is not a fresh request.
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false) && !fromHistory) {
            openPlayerRequests++
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "io.github.admilsonlandim.veyra.extra.OPEN_PLAYER"
    }
}
