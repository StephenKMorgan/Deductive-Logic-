package com.therefore.logic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.therefore.logic.data.Settings
import com.therefore.logic.data.ThemeMode
import com.therefore.logic.reminder.Reminders
import com.therefore.logic.ui.GameViewModel
import com.therefore.logic.ui.ThereforeApp
import com.therefore.logic.ui.theme.DarkPalette
import com.therefore.logic.ui.theme.LightPalette
import com.therefore.logic.ui.theme.ThereforeTheme

class MainActivity : ComponentActivity() {
    private val game: GameViewModel by viewModels()
    private var openDaily by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDaily = intent?.getStringExtra(Reminders.EXTRA_OPEN) == Reminders.OPEN_DAILY
        val settingsFlow = (application as TheApp).container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(Settings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(DarkPalette.surface.toArgb())
                else SystemBarStyle.light(LightPalette.bg.toArgb(), DarkPalette.bg.toArgb())
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                onDispose {}
            }
            ThereforeTheme(settings.theme, settings.plainText, settings.accent, settings.textSize) {
                ThereforeApp(game, settings, openDaily)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(Reminders.EXTRA_OPEN) == Reminders.OPEN_DAILY) {
            openDaily = false
            openDaily = true
        }
    }
}
