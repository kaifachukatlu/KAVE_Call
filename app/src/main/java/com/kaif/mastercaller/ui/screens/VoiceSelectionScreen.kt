package com.kaif.mastercaller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.data.AppSettings
import com.kaif.mastercaller.data.SUPPORTED_LANGUAGES
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.components.ScreenHeader
import com.kaif.mastercaller.ui.components.SecondaryButton
import com.kaif.mastercaller.ui.components.SectionLabel
import com.kaif.mastercaller.ui.components.StatusChip
import com.kaif.mastercaller.ui.theme.McColors
import com.kaif.mastercaller.voice.AnnouncementHelper
import com.kaif.mastercaller.voice.TtsState
import com.kaif.mastercaller.voice.VoiceGender
import com.kaif.mastercaller.voice.VoiceOption
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceSelectionScreen(onBack: () -> Unit) {
    val app = MasterCallerApp.instance
    val context = LocalContext.current
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
    val voices by app.announcer.voices.collectAsStateWithLifecycle()
    val tts by app.announcer.status.collectAsStateWithLifecycle()
    val save: ((AppSettings) -> AppSettings) -> Unit = { t -> app.appScope.launch { app.settingsRepository.update(t) } }
    val languageLabel = SUPPORTED_LANGUAGES.firstOrNull { it.tag == settings.languageTag }?.label
        ?: Locale.forLanguageTag(settings.languageTag).getDisplayName(Locale.ENGLISH)

    // Coming back from Google's download / remove screen: show the new state straight away.
    LifecycleResumeEffect(Unit) {
        app.announcer.refresh()
        onPauseOrDispose { }
    }

    val ready = voices.filter { !it.notInstalled }
    val downloadable = voices.filter { it.notInstalled }
    val openDownload = { safeStart(context, app.announcer.installVoiceDataIntent()) }

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
                    Text("Voice data", style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Voices are downloaded and removed by your phone's speech engine (Speech Services by Google). " +
                            "These buttons open it; come back here when you're done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.TextSecondary,
                    )
                    if (tts.state == TtsState.FAILED) {
                        Spacer(Modifier.height(8.dp))
                        Text(tts.message ?: "Text-to-speech unavailable", color = McColors.Warning, style = MaterialTheme.typography.bodySmall)
                    }
                    if (tts.missingData) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The $languageLabel voice isn't downloaded yet.",
                            color = McColors.Warning,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Download", onClick = openDownload, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f))
                        SecondaryButton(
                            "Remove",
                            onClick = { safeStart(context, app.announcer.manageVoicesIntent()) },
                            icon = Icons.Rounded.Delete,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item { SectionLabel("Ready to use") }
            item {
                VoiceRow(
                    title = "Automatic (recommended)",
                    subtitle = "Best downloaded ${if (settings.voicePreference.name == "MALE") "male" else "female"} voice" +
                        (tts.activeVoice?.takeIf { settings.voiceName == null }?.let { " · now ${it.shortName}" } ?: ""),
                    selected = settings.voiceName == null,
                    chips = emptyList(),
                    onSelect = { save { it.copy(voiceName = null) } },
                    onPreview = {
                        app.announcer.speak(app.announcer.previewSpeech(settings), AnnouncementHelper.Purpose.PREVIEW) {}
                    },
                )
            }
            if (ready.isEmpty() && tts.state == TtsState.READY) {
                item {
                    Text(
                        "No $languageLabel voices downloaded yet. Tap Download above, or pick one below.",
                        color = McColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(ready, key = { it.name }) { v ->
                VoiceRow(
                    title = "${v.shortName} · ${v.localeLabel}",
                    subtitle = v.name,
                    selected = settings.voiceName == v.name,
                    chips = chipsFor(v),
                    onSelect = { save { it.copy(voiceName = v.name) } },
                    onPreview = {
                        app.announcer.speak(app.announcer.previewSpeech(settings), AnnouncementHelper.Purpose.PREVIEW, voiceOverride = v.name) {}
                    },
                )
            }

            if (downloadable.isNotEmpty()) {
                item { SectionLabel("Available to download") }
                items(downloadable, key = { it.name }) { v ->
                    DownloadRow(v, onDownload = openDownload)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun chipsFor(v: VoiceOption): List<Pair<String, Boolean?>> = buildList {
    when (v.gender) {
        VoiceGender.FEMALE -> add("Female" to true)
        VoiceGender.MALE -> add("Male" to null)
        VoiceGender.UNKNOWN -> {}
    }
    add(if (v.requiresNetwork) "Online" to false else "Offline" to true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    chips: List<Pair<String, Boolean?>>,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
) {
    McCard(onClick = onSelect, highlight = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = McColors.Accent),
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = McColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // Wraps onto a second line instead of overflowing.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { (text, ok) -> StatusChip(text, ok) }
                    }
                }
            }
            FilledTonalIconButton(
                onClick = onPreview,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = McColors.Surface3, contentColor = McColors.Accent),
            ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview") }
        }
    }
}

@Composable
private fun DownloadRow(v: VoiceOption, onDownload: () -> Unit) {
    McCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${v.shortName} · ${v.localeLabel}", style = MaterialTheme.typography.titleSmall, color = McColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (v.gender) {
                        VoiceGender.FEMALE -> "Female voice"
                        VoiceGender.MALE -> "Male voice"
                        VoiceGender.UNKNOWN -> "Voice"
                    } + " · not downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = McColors.TextMuted,
                )
            }
            Spacer(Modifier.width(10.dp))
            SecondaryButton("Download", onClick = onDownload, icon = Icons.Rounded.Download)
        }
    }
}
