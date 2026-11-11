package com.kaif.mastercaller.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.rounded.SystemUpdate
import com.kaif.mastercaller.ui.components.ControlTile
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.History
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
    var hour by remember { mutableStateOf(currentHour()) }
    LifecycleResumeEffect(Unit) {
        snap = SetupStatus.snapshot(context)
        hour = currentHour() // refresh the greeting whenever the app comes back to the front
        app.updates.check() // free GitHub update check, at most every few hours
        onPauseOrDispose { app.speech.cancel() }
    }

    McBackground {
      Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
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
            val greeting = greetingFor(hour)
            Text(
                // Same greeting on every phone; the personal title is only used in the spoken announcement.
                "$greeting,\nBoss",
                style = MaterialTheme.typography.displaySmall,
                color = McColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "BEYOND THE ORDINARY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = McColors.TextMuted,
            )

            UpdateCard()

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
                        if (voice.gender == VoiceGender.MALE) append(" · likely male")
                        tts.engineLabel?.let { append("\n$it") }
                    }
                } + if (tts.missingData) "\nVoice data missing, tap to fix in Settings" else "",
                ok = voice != null && !tts.missingData,
                chip = "Change",
                onClick = onOpenVoices,
            )

            Spacer(Modifier.height(24.dp))
        }
        HomeBottomBar(
            onRecents = { context.startActivity(DialerActivity.intent(context, DialerActivity.TAB_RECENTS)) },
            onContacts = { context.startActivity(DialerActivity.intent(context, DialerActivity.TAB_CONTACTS)) },
            onKeypad = { context.startActivity(DialerActivity.intent(context, DialerActivity.TAB_KEYPAD)) },
            onWallpapers = onOpenWallpapers,
        )
      }
    }
}

/** Fixed bottom bar for quick access, like a phone app's tabs. */
@Composable
private fun HomeBottomBar(onRecents: () -> Unit, onContacts: () -> Unit, onKeypad: () -> Unit, onWallpapers: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(McColors.Surface)
            .border(1.dp, McColors.Outline, shape)
            .padding(6.dp),
    ) {
        listOf(
            Triple(Icons.Rounded.History, "Recents", onRecents),
            Triple(Icons.Rounded.Contacts, "Contacts", onContacts),
            Triple(Icons.Rounded.Dialpad, "Keypad", onKeypad),
            Triple(Icons.Rounded.Wallpaper, "Wallpapers", onWallpapers),
        ).forEach { (icon, label, action) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .clickable(onClick = action)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, tint = McColors.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = McColors.TextSecondary, maxLines = 1)
            }
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

private fun currentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

/** Morning 5-11, afternoon 12-16, evening 17-20, night 21-4. */
internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

/** "Update available" card, shown only when GitHub has a newer release (or while downloading). */
@Composable
private fun UpdateCard() {
    val updates = MasterCallerApp.instance.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val s = state
    val update = when (s) {
        is com.kaif.mastercaller.update.UpdateState.Available -> s.update
        is com.kaif.mastercaller.update.UpdateState.Downloading -> s.update
        is com.kaif.mastercaller.update.UpdateState.ReadyToInstall -> s.update
        else -> null
    }
    if (update == null && s !is com.kaif.mastercaller.update.UpdateState.Failed) return
    Spacer(Modifier.height(20.dp))
    McCard(highlight = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SystemUpdate, tint = McColors.Accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (update != null) "Update available · v${update.version}" else "Update",
                    style = MaterialTheme.typography.titleMedium,
                    color = McColors.TextPrimary,
                )
                Text(
                    when (s) {
                        is com.kaif.mastercaller.update.UpdateState.Downloading -> "Downloading… ${(s.progress * 100).toInt()}%"
                        is com.kaif.mastercaller.update.UpdateState.ReadyToInstall -> "Downloaded. Tap Install."
                        is com.kaif.mastercaller.update.UpdateState.Failed -> s.message
                        else -> update?.notes?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
                            ?: "You're on v${updates.currentVersion}. Your settings are kept."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = McColors.TextSecondary,
                    maxLines = 2,
                )
            }
        }
        if (s is com.kaif.mastercaller.update.UpdateState.Downloading) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth(),
                color = McColors.Accent,
                trackColor = McColors.Surface3,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                s is com.kaif.mastercaller.update.UpdateState.ReadyToInstall ->
                    PrimaryButton("Install", onClick = { updates.install() })
                s is com.kaif.mastercaller.update.UpdateState.Failed ->
                    SecondaryButton("Try again", onClick = { updates.check(force = true) })
                update != null && s !is com.kaif.mastercaller.update.UpdateState.Downloading ->
                    PrimaryButton("Update now", onClick = { updates.downloadAndInstall(update) })
            }
            if (s !is com.kaif.mastercaller.update.UpdateState.Downloading) {
                androidx.compose.material3.TextButton(onClick = { updates.dismiss() }) {
                    Text("Later", color = McColors.TextMuted)
                }
            }
        }
    }
}
