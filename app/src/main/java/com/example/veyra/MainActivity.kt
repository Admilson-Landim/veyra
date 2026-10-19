package com.example.veyra

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.veyra.ui.VeyraApp
import com.example.veyra.ui.theme.VeyraTheme

class MainActivity : ComponentActivity() {

    // Incremented each time an intent asks to show the player (e.g. a tap on the notification).
    private var openPlayerRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only for now, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // On recreation (e.g. rotation) the intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            VeyraTheme {
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
        const val EXTRA_OPEN_PLAYER = "com.example.veyra.extra.OPEN_PLAYER"
    }
}
