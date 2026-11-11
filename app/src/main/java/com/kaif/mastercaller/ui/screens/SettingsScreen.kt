package com.kaif.mastercaller.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaif.mastercaller.MasterCallerApp
import com.kaif.mastercaller.data.AppSettings
import com.kaif.mastercaller.data.SUPPORTED_LANGUAGES
import com.kaif.mastercaller.data.VoicePreference
import com.kaif.mastercaller.data.WordingPresets
import com.kaif.mastercaller.data.withVoicePreference
import com.kaif.mastercaller.data.withDefaultWording
import com.kaif.mastercaller.data.withLanguage
import com.kaif.mastercaller.ui.components.McBackground
import com.kaif.mastercaller.ui.components.McCard
import com.kaif.mastercaller.ui.components.PrimaryButton
import com.kaif.mastercaller.ui.components.ScreenHeader
import com.kaif.mastercaller.ui.components.SecondaryButton
import com.kaif.mastercaller.ui.components.SectionLabel
import com.kaif.mastercaller.ui.components.SettingSwitchRow
import com.kaif.mastercaller.ui.theme.McColors
import com.kaif.mastercaller.voice.AnnouncementHelper
import com.kaif.mastercaller.voice.AnnouncementText
import com.kaif.mastercaller.voice.TtsState
import com.kaif.mastercaller.voice.VoiceCommandParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenVoices: () -> Unit, onOpenSetup: () -> Unit, onOpenWallpapers: () -> Unit) {
    val app = MasterCallerApp.instance
    val context = LocalContext.current
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle()
    val tts by app.announcer.status.collectAsStateWithLifecycle()
    val engines by app.announcer.engines.collectAsStateWithLifecycle()
    val save: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        app.appScope.launch { app.settingsRepository.update(transform) }
    }

    McBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ScreenHeader("Settings", "Saved on this phone only", onBack)

            // --- Personalisation -----------------------------------------------------------------
            SectionLabel("Announcement")
            McCard {
                DebouncedTextField(
                    label = "What should she call you?",
                    value = settings.personalTitle,
                    singleLine = true,
                    supporting = "Anything you like. Or tap a quick choice below.",
                    onSave = { v -> save { it.copy(personalTitle = v) } },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Boss", "Sir", "Madam", "Hero", "Captain", "Legend", "Bro").forEach { choice ->
                        val selected = settings.personalTitle == choice
                        Text(
                            choice,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) McColors.Ink else McColors.TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) McColors.Accent else McColors.Surface2)
                                .border(1.dp, if (selected) McColors.Accent else McColors.Outline, RoundedCornerShape(50))
                                .clickable { save { it.copy(personalTitle = choice, titlePronunciation = "") } }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                DebouncedTextField(
                    label = "Announcement (saved contacts)",
                    value = settings.announcementTemplate,
                    supporting = "Use {title} and {name}",
                    onSave = { v -> save { it.copy(announcementTemplate = v) } },
                )
                Spacer(Modifier.height(12.dp))
                DebouncedTextField(
                    label = "Announcement (unknown numbers)",
                    value = settings.unknownTemplate,
                    supporting = "Use {title} and {number}",
                    onSave = { v -> save { it.copy(unknownTemplate = v) } },
                )
                TextButton(onClick = { save { it.withDefaultWording() } }) {
                    Icon(Icons.Rounded.Replay, null, tint = McColors.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset wording", color = McColors.TextSecondary)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = McColors.Outline)
                SettingSwitchRow(
                    "Caller announcements",
                    "Speak who is calling",
                    settings.announcementsEnabled,
                    { v -> save { it.copy(announcementsEnabled = v) } },
                )
                SettingSwitchRow(
                    "Announce unknown numbers",
                    "Say \"an unknown number is calling\"",
                    settings.announceUnknownCallers,
                    { v -> save { it.copy(announceUnknownCallers = v) } },
                    enabled = settings.announcementsEnabled,
                )
                SettingSwitchRow(
                    "Say the number",
                    "For unknown callers, read the number digit by digit, always in English",
                    settings.announceNumber,
                    { v -> save { it.copy(announceNumber = v) } },
                    enabled = settings.announcementsEnabled && settings.announceUnknownCallers,
                )
            }

            // --- Voice ---------------------------------------------------------------------------
            SectionLabel("Voice")
            McCard {
                Text("Announcer", style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, McColors.Outline, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                ) {
                    VoicePreference.entries.forEach { pref ->
                        val selected = settings.voicePreference == pref
                        Text(
                            if (pref == VoicePreference.FEMALE) "Female" else "Male",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) McColors.Ink else McColors.TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) McColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { save { it.withVoicePreference(pref) } }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = McColors.Outline)
                NavRow(
                    title = "Choose a specific voice",
                    value = tts.activeVoice?.let { "${it.localeLabel} · ${it.shortName}" + if (settings.voiceName == null) " (auto)" else "" }
                        ?: if (tts.state == TtsState.READY) "Engine default" else "Loading…",
                    onClick = onOpenVoices,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = McColors.Outline)
                EngineSelector(
                    current = settings.enginePackage,
                    currentLabel = tts.engineLabel,
                    engines = engines.map { it.packageName to it.label },
                    onSelect = { pkg -> save { it.copy(enginePackage = pkg, voiceName = null) } },
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = McColors.Outline)
                FloatSlider(
                    label = "Speed",
                    value = settings.speechRate,
                    range = 0.5f..1.8f,
                    format = { "%.2fx".format(it) },
                    onSave = { v -> save { it.copy(speechRate = v) } },
                )
                FloatSlider(
                    label = "Pitch",
                    value = settings.pitch,
                    range = 0.6f..1.6f,
                    format = { "%.2f".format(it) },
                    onSave = { v -> save { it.copy(pitch = v) } },
                )
                if (tts.missingData || tts.languageUnsupported) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (tts.missingData) "Voice data for this language isn't downloaded yet."
                        else "This engine doesn't support the selected language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.Warning,
                    )
                    TextButton(onClick = { safeStart(context, app.announcer.installVoiceDataIntent()) }) {
                        Text("Install voice data", color = McColors.Accent)
                    }
                }
            }

            // --- Language ------------------------------------------------------------------------
            SectionLabel("Language")
            McCard {
                SUPPORTED_LANGUAGES.forEach { lang ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { save { it.withLanguage(lang.tag) } }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.languageTag == lang.tag,
                            onClick = { save { it.withLanguage(lang.tag) } },
                            colors = RadioButtonDefaults.colors(selectedColor = McColors.Accent),
                        )
                        Text(lang.label, color = McColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Used for the spoken voice and for voice commands. Unless you've customised the wording, it switches to match (Hindi: \"…कॉल कर रहे हैं। उठाऊँ या नहीं?\").",
                    style = MaterialTheme.typography.bodySmall,
                    color = McColors.TextMuted,
                )
            }

            // --- Calls ---------------------------------------------------------------------------
            SectionLabel("During calls")
            McCard {
                SettingSwitchRow(
                    "Automatic speaker",
                    "Request speakerphone right after you pick up (skipped if a headset is connected)",
                    settings.autoSpeaker,
                    { v -> save { it.copy(autoSpeaker = v) } },
                )
                SettingSwitchRow(
                    "Vibrate while ringing",
                    "In addition to the ringtone",
                    settings.vibrateWhileRinging,
                    { v -> save { it.copy(vibrateWhileRinging = v) } },
                )
                SettingSwitchRow(
                    "Touch controls",
                    "Always on, so you can always answer or reject by tapping",
                    checked = true,
                    onCheckedChange = {},
                    enabled = false,
                )
            }

            // --- Voice commands ------------------------------------------------------------------
            SectionLabel("Voice commands")
            McCard {
                SettingSwitchRow(
                    "Voice commands",
                    "Listen briefly after the announcement",
                    settings.voiceCommandsEnabled,
                    { v -> save { it.copy(voiceCommandsEnabled = v) } },
                )
                IntSlider(
                    label = "Listening window",
                    value = settings.listenSeconds,
                    range = AppSettings.MIN_LISTEN_SECONDS..AppSettings.MAX_LISTEN_SECONDS,
                    suffix = "s",
                    onSave = { v -> save { it.copy(listenSeconds = v) } },
                )
                SettingSwitchRow(
                    "Prefer offline recognition",
                    "Uses the on-device language pack when installed",
                    settings.preferOfflineRecognition,
                    { v -> save { it.copy(preferOfflineRecognition = v) } },
                )
                Spacer(Modifier.height(6.dp))
                Text("Answer: " + VoiceCommandParser.ANSWER_PHRASES.take(5).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = McColors.TextSecondary)
                Text("Reject: " + VoiceCommandParser.REJECT_PHRASES.take(5).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = McColors.TextSecondary)
                if (!app.speech.isRecognitionAvailable()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No speech recognition service found on this phone. Install/enable the Google app. Touch controls still work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = McColors.Warning,
                    )
                }
            }

            SectionLabel("Call screen")
            SecondaryButton("Call wallpapers", onClick = onOpenWallpapers, modifier = Modifier.fillMaxWidth())

            SectionLabel("About")
            McCard {
                val updates = app.updates
                val upd by updates.state.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("KAVÉ Call v${updates.currentVersion}", style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                        Text(
                            when (val u = upd) {
                                com.kaif.mastercaller.update.UpdateState.Checking -> "Checking…"
                                com.kaif.mastercaller.update.UpdateState.UpToDate -> "You have the latest version"
                                is com.kaif.mastercaller.update.UpdateState.Available -> "v${u.update.version} is available. See the dashboard."
                                is com.kaif.mastercaller.update.UpdateState.Downloading -> "Downloading update…"
                                is com.kaif.mastercaller.update.UpdateState.ReadyToInstall -> "Update downloaded"
                                is com.kaif.mastercaller.update.UpdateState.Failed -> u.message
                                com.kaif.mastercaller.update.UpdateState.Idle -> "Updates come free from GitHub Releases"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = McColors.TextSecondary,
                        )
                    }
                    TextButton(onClick = { updates.check(force = true) }) { Text("Check", color = McColors.Accent) }
                }
            }

            SectionLabel("Setup")
            SecondaryButton("Permissions & default phone app", onClick = onOpenSetup, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Done", onClick = onBack, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Text field that saves after typing pauses, and follows external changes (e.g. Reset). */
@Composable
private fun DebouncedTextField(
    label: String,
    value: String,
    onSave: (String) -> Unit,
    singleLine: Boolean = false,
    supporting: String? = null,
    allowBlank: Boolean = false,
) {
    var text by remember { mutableStateOf(value) }
    var lastSaved by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastSaved) {
            text = value
            lastSaved = value
        }
    }
    LaunchedEffect(text) {
        delay(450)
        if (text != lastSaved && (allowBlank || text.isNotBlank())) {
            lastSaved = text
            onSave(text)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = McColors.Accent,
            unfocusedBorderColor = McColors.Outline,
            focusedLabelColor = McColors.Accent,
            cursorColor = McColors.Accent,
        ),
    )
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
            Text(value, style = MaterialTheme.typography.bodySmall, color = McColors.TextSecondary)
        }
        @Suppress("DEPRECATION")
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = McColors.TextSecondary)
    }
}

@Composable
private fun EngineSelector(
    current: String?,
    currentLabel: String?,
    engines: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = engines.isNotEmpty()) { expanded = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Speech engine", style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary)
                Text(
                    (currentLabel ?: "System default") + if (current == null) " (system default)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = McColors.TextSecondary,
                )
            }
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = McColors.TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("System default") }, onClick = {
                expanded = false
                onSelect(null)
            })
            engines.forEach { (pkg, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    onSelect(pkg)
                })
            }
        }
    }
}

@Composable
private fun FloatSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onSave: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.labelLarge, color = McColors.Accent)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onSave(local) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = McColors.Accent,
                activeTrackColor = McColors.Accent,
                inactiveTrackColor = McColors.Surface3,
            ),
        )
    }
}

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, suffix: String, onSave: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(top = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, color = McColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()}$suffix", style = MaterialTheme.typography.labelLarge, color = McColors.Accent)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onSave(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = McColors.Accent,
                activeTrackColor = McColors.Accent,
                inactiveTrackColor = McColors.Surface3,
            ),
        )
    }
}
