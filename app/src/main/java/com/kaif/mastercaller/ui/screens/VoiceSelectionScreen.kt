package com.kaif.mastercaller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.data.AppSettings
import com.kaif.mastercaller.data.SUPPORTED_LANGUAGES
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.components.ScreenHeader
import com.kaif.mastercaller.ui.components.StatusChip
import com.kaif.mastercaller.ui.theme.McColors
import com.kaif.mastercaller.voice.AnnouncementHelper
import com.kaif.mastercaller.voice.TtsState
import com.kaif.mastercaller.voice.VoiceGender
import com.kaif.mastercaller.voice.VoiceOption
import kotlinx.coroutines.launch

@Composable
fun VoiceSelectionScreen(onBack: () -> Unit) {
    val app = MasterCallerApp.instance
    val context = LocalContext.current
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
    val voices by app.announcer.voices.collectAsStateWithLifecycle()
    val tts by app.announcer.status.collectAsStateWithLifecycle()
    val save: ((AppSettings) -> AppSettings) -> Unit = { t -> app.appScope.launch { app.settingsRepository.update(t) } }
    val languageLabel = SUPPORTED_LANGUAGES.firstOrNull { it.tag == settings.languageTag }?.label ?: settings.languageTag

    McBackground {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader("Choose a voice", languageLabel, onBack)
                McCard {
                    Text(
                        "Android doesn't label voices by gender, and every engine names them differently. " +
                            "\"Likely female\" is a best guess, so tap ▶ to preview before choosing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.TextSecondary,
                    )
                    if (tts.state == TtsState.FAILED) {
                        Spacer(Modifier.height(8.dp))
                        Text(tts.message ?: "Text-to-speech unavailable", color = McColors.Warning, style = MaterialTheme.typography.bodySmall)
                    }
                    if (tts.missingData) {
                        TextButton(onClick = { safeStart(context, app.announcer.installVoiceDataIntent()) }) {
                            Text("Download voice data", color = McColors.Accent)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            item {
                VoiceRow(
                    title = "Automatic (recommended)",
                    subtitle = "Best on-device female-sounding voice for $languageLabel" +
                        (tts.activeVoice?.takeIf { settings.voiceName == null }?.let { " · now ${it.shortName}" } ?: ""),
                    selected = settings.voiceName == null,
                    chips = emptyList(),
                    onSelect = { save { it.copy(voiceName = null) } },
                    onPreview = {
                        app.announcer.speak(app.announcer.previewSpeech(settings), AnnouncementHelper.Purpose.PREVIEW) {}
                    },
                )
            }

            if (voices.isEmpty() && tts.state == TtsState.READY) {
                item {
                    Text(
                        "No voices for this language in the current engine. Try another language or engine in Settings.",
                        color = McColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            items(voices, key = { it.name }) { v ->
                VoiceRow(
                    title = "${v.shortName} · ${v.localeLabel}",
                    subtitle = v.name,
                    selected = settings.voiceName == v.name,
                    chips = chipsFor(v),
                    enabled = !v.notInstalled,
                    onSelect = { save { it.copy(voiceName = v.name) } },
                    onPreview = {
                        app.announcer.speak(
                            app.announcer.previewSpeech(settings),
                            AnnouncementHelper.Purpose.PREVIEW,
                            voiceOverride = v.name,
                        ) {}
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun chipsFor(v: VoiceOption): List<Pair<String, Boolean?>> = buildList {
    when (v.gender) {
        VoiceGender.FEMALE -> add("Likely female" to true)
        VoiceGender.MALE -> add("Likely male" to null)
        VoiceGender.UNKNOWN -> add("Gender unknown" to null)
    }
    add(if (v.requiresNetwork) "Needs internet" to false else "On-device" to true)
    if (v.notInstalled) add("Not downloaded" to false)
}

@Composable
private fun VoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    chips: List<Pair<String, Boolean?>>,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    enabled: Boolean = true,
) {
    McCard(onClick = if (enabled) onSelect else null, highlight = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = if (enabled) onSelect else null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = McColors.Accent),
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted, maxLines = 1)
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { (text, ok) -> StatusChip(text, ok) }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = onPreview,
                enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = McColors.Surface3,
                    contentColor = McColors.Accent,
                ),
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview") }
        }
    }
}
