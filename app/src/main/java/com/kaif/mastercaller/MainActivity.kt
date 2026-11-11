package com.kaif.mastercaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kaif.mastercaller.ui.screens.HomeScreen
import com.kaif.mastercaller.ui.screens.SettingsScreen
import com.kaif.mastercaller.ui.screens.SetupScreen
import com.kaif.mastercaller.ui.screens.SetupStatus
import com.kaif.mastercaller.ui.screens.VoiceSelectionScreen
import com.kaif.mastercaller.ui.screens.WallpapersScreen
import com.kaif.mastercaller.ui.theme.MasterCallerTheme

enum class Screen { HOME, SETUP, SETTINGS, VOICES, WALLPAPERS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MasterCallerTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    // First experience: go straight to setup until the essentials are granted.
    var screen by rememberSaveable {
        mutableStateOf(if (SetupStatus.snapshot(context).essentialsComplete) Screen.HOME else Screen.SETUP)
    }
    var previous by rememberSaveable { mutableStateOf(Screen.HOME) }
    fun go(to: Screen) {
        previous = screen
        screen = to
    }
    val back: () -> Unit = {
        screen = when (screen) {
            Screen.VOICES, Screen.WALLPAPERS -> if (previous == Screen.SETTINGS) Screen.SETTINGS else Screen.HOME
            else -> Screen.HOME
        }
    }
    BackHandler(enabled = screen != Screen.HOME, onBack = back)

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "screens",
    ) { s ->
        when (s) {
            Screen.HOME -> HomeScreen(
                onOpenSettings = { go(Screen.SETTINGS) },
                onOpenSetup = { go(Screen.SETUP) },
                onOpenVoices = { go(Screen.VOICES) },
                onOpenWallpapers = { go(Screen.WALLPAPERS) },
            )
            Screen.SETUP -> SetupScreen(onBack = back, onDone = { screen = Screen.HOME })
            Screen.SETTINGS -> SettingsScreen(
                onBack = back,
                onOpenVoices = { go(Screen.VOICES) },
                onOpenSetup = { go(Screen.SETUP) },
                onOpenWallpapers = { go(Screen.WALLPAPERS) },
            )
            Screen.VOICES -> VoiceSelectionScreen(onBack = back)
            Screen.WALLPAPERS -> WallpapersScreen(onBack = back)
        }
    }
}
