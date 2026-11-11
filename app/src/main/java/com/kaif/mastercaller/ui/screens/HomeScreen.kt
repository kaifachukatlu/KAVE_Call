package com.kaif.mastercaller.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.data.WordingPresets
import com.kaif.mastercaller.ui.components.AppLogo
import com.kaif.mastercaller.ui.components.IconBadge
import com.kaif.mastercaller.ui.components.ListeningOrb
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.components.PrimaryButton
import com.kaif.mastercaller.ui.components.SecondaryButton
import com.kaif.mastercaller.ui.components.SectionLabel
import com.kaif.mastercaller.ui.components.StatusChip
import com.kaif.mastercaller.ui.dialer.DialerActivity
import com.kaif.mastercaller.ui.theme.McColors
import com.kaif.mastercaller.voice.AnnouncementHelper
import com.kaif.mastercaller.voice.TtsState
import com.kaif.mastercaller.voice.VoiceCommand
import com.kaif.mastercaller.voice.VoiceGender

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onOpenSetup: () -> Unit, onOpenVoices: () -> Unit, onOpenWallpapers: () -> Unit) {
    val context = LocalContext.current
    val app = MasterCallerApp.instance
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
    val tts by app.announcer.status.collectAsStateWithLifecycle()

    var snap by remember { mutableStateOf(SetupStatus.snapshot(context)) }
    LifecycleResumeEffect(Unit) {
        snap = SetupStatus.snapshot(context)
        onPauseOrDispose { app.speech.cancel() }
    }

    McBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.kaif.mastercaller.R.drawable.kave_wordmark),
                        contentDescription = "KAVÉ",
                        modifier = Modifier.width(92.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("CALL", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 5.sp), color = McColors.AccentSoft)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = McColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(36.dp))
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greeting = when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            Text(
                "$greeting,\n${settings.personalTitle.ifBlank { "there" }}",
                style = MaterialTheme.typography.displaySmall,
                color = McColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "BEYOND THE ORDINARY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = McColors.TextMuted,
            )

            SectionLabel("Status")
            StatusCard(
                icon = Icons.Rounded.Call,
                hue = McColors.Accent,
                title = "Default phone app",
                detail = if (snap.isDefaultDialer) {
                    "KAVÉ Call handles your incoming calls"
                } else {
                    "Not set. Calls still use your old phone app"
                },
                ok = snap.isDefaultDialer,
                chip = if (snap.isDefaultDialer) "Active" else "Set up",
                onClick = onOpenSetup,
            )
            Spacer(Modifier.height(12.dp))
            val assistantOk = settings.announcementsEnabled && tts.state == TtsState.READY
            StatusCard(
                icon = Icons.Rounded.RecordVoiceOver,
                hue = McColors.Accent,
                title = "Voice assistant",
                detail = buildString {
                    append(if (settings.announcementsEnabled) "Announcements on" else "Announcements off")
                    append(" · ")
                    append(
                        when {
                            !settings.voiceCommandsEnabled -> "voice commands off"
                            !snap.microphone -> "mic permission needed"
                            else -> "voice commands on"
                        },
                    )
                    if (!snap.contacts) append(" · contacts permission needed")
                    if (tts.state == TtsState.FAILED) append("\n" + (tts.message ?: "Text-to-speech unavailable"))
                },
                ok = assistantOk && snap.contacts,
                chip = when {
                    tts.state == TtsState.INITIALIZING -> "Starting"
                    assistantOk && snap.contacts -> "Ready"
                    else -> "Check"
                },
                onClick = if (snap.contacts && snap.microphone) onOpenSettings else onOpenSetup,
            )
            Spacer(Modifier.height(12.dp))
            val voice = tts.activeVoice
            StatusCard(
                icon = Icons.Rounded.GraphicEq,
                hue = McColors.Accent,
                title = "Announcement voice",
                detail = when {
                    voice == null && tts.state == TtsState.READY -> "Engine default voice${tts.engineLabel?.let { " · $it" } ?: ""}"
                    voice == null -> "Loading voices…"
                    else -> buildString {
                        append(if (settings.voiceName == null) "Auto · " else "")
                        append("${voice.localeLabel} · ${voice.shortName}")
                        if (voice.gender == VoiceGender.FEMALE) append(" · likely female")
                        tts.engineLabel?.let { append("\n$it") }
                    }
                } + if (tts.missingData) "\nVoice data missing, tap to fix in Settings" else "",
                ok = voice != null && !tts.missingData,
                chip = "Change",
                onClick = onOpenVoices,
            )

            SectionLabel("Shortcuts")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    "Phone",
                    onClick = { context.startActivity(Intent(context, DialerActivity::class.java)) },
                    icon = Icons.Rounded.Dialpad,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton("Wallpapers", onClick = onOpenWallpapers, icon = Icons.Rounded.Wallpaper, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    detail: String,
    ok: Boolean,
    hue: androidx.compose.ui.graphics.Color,
    chip: String,
    onClick: () -> Unit,
) {
    McCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = if (ok) hue else McColors.Warning)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = McColors.TextPrimary)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = McColors.TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(chip, ok)
        }
    }
}
