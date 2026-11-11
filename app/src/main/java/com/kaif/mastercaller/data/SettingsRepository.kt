package com.kaif.mastercaller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "master_caller_settings")

/** Local-only preference storage (Jetpack DataStore). Nothing here ever leaves the phone. */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val TITLE = stringPreferencesKey("personal_title")
        val TITLE_SPOKEN = stringPreferencesKey("title_pronunciation")
        val TEMPLATE = stringPreferencesKey("announcement_template")
        val UNKNOWN_TEMPLATE = stringPreferencesKey("unknown_template")
        val ANNOUNCE = booleanPreferencesKey("announcements_enabled")
        val ANNOUNCE_UNKNOWN = booleanPreferencesKey("announce_unknown")
        val AUTO_SPEAKER = booleanPreferencesKey("auto_speaker")
        val VOICE_COMMANDS = booleanPreferencesKey("voice_commands")
        val VIBRATE = booleanPreferencesKey("vibrate_while_ringing")
        val LANGUAGE = stringPreferencesKey("language_tag")
        val VOICE = stringPreferencesKey("voice_name")
        val ENGINE = stringPreferencesKey("engine_package")
        val RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
        val LISTEN = intPreferencesKey("listen_seconds")
        val OFFLINE = booleanPreferencesKey("prefer_offline")
        val STYLE_VERSION = intPreferencesKey("style_version")
    }

    /** Cold flow straight from disk; first emission is the real stored value. */
    val settingsFlow: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Hot snapshot for synchronous reads (starts with defaults until disk is read). */
    val settings: StateFlow<AppSettings> =
        settingsFlow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    init {
        // One-time upgrade (v2): anyone still on the old flat 1.0 speed/pitch gets the softer "sweet" voice.
        scope.launch {
            try {
                store.edit { prefs ->
                    if ((prefs[Keys.STYLE_VERSION] ?: 1) < 2) {
                        if ((prefs[Keys.RATE] ?: 1f) == 1f) prefs[Keys.RATE] = AppSettings.SWEET_RATE
                        if ((prefs[Keys.PITCH] ?: 1f) == 1f) prefs[Keys.PITCH] = AppSettings.SWEET_PITCH
                        prefs[Keys.STYLE_VERSION] = 2
                    }
                }
            } catch (e: IOException) {
                // Non-fatal: defaults still apply.
            }
        }
    }

    suspend fun current(): AppSettings = settingsFlow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            write(prefs, updated)
        }
    }

    private fun write(prefs: MutablePreferences, s: AppSettings) {
        prefs[Keys.TITLE] = s.personalTitle
        prefs[Keys.TITLE_SPOKEN] = s.titlePronunciation
        prefs[Keys.TEMPLATE] = s.announcementTemplate
        prefs[Keys.UNKNOWN_TEMPLATE] = s.unknownTemplate
        prefs[Keys.ANNOUNCE] = s.announcementsEnabled
        prefs[Keys.ANNOUNCE_UNKNOWN] = s.announceUnknownCallers
        prefs[Keys.AUTO_SPEAKER] = s.autoSpeaker
        prefs[Keys.VOICE_COMMANDS] = s.voiceCommandsEnabled
        prefs[Keys.VIBRATE] = s.vibrateWhileRinging
        prefs[Keys.LANGUAGE] = s.languageTag
        val voice = s.voiceName
        if (voice != null) prefs[Keys.VOICE] = voice else prefs.remove(Keys.VOICE)
        val engine = s.enginePackage
        if (engine != null) prefs[Keys.ENGINE] = engine else prefs.remove(Keys.ENGINE)
        prefs[Keys.RATE] = s.speechRate
        prefs[Keys.PITCH] = s.pitch
        prefs[Keys.LISTEN] = s.listenSeconds
        prefs[Keys.OFFLINE] = s.preferOfflineRecognition
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            personalTitle = this[Keys.TITLE] ?: d.personalTitle,
            titlePronunciation = this[Keys.TITLE_SPOKEN] ?: d.titlePronunciation,
            announcementTemplate = this[Keys.TEMPLATE]?.takeIf { it.isNotBlank() } ?: d.announcementTemplate,
            unknownTemplate = this[Keys.UNKNOWN_TEMPLATE]?.takeIf { it.isNotBlank() } ?: d.unknownTemplate,
            announcementsEnabled = this[Keys.ANNOUNCE] ?: d.announcementsEnabled,
            announceUnknownCallers = this[Keys.ANNOUNCE_UNKNOWN] ?: d.announceUnknownCallers,
            autoSpeaker = this[Keys.AUTO_SPEAKER] ?: d.autoSpeaker,
            voiceCommandsEnabled = this[Keys.VOICE_COMMANDS] ?: d.voiceCommandsEnabled,
            vibrateWhileRinging = this[Keys.VIBRATE] ?: d.vibrateWhileRinging,
            languageTag = this[Keys.LANGUAGE] ?: d.languageTag,
            voiceName = this[Keys.VOICE],
            enginePackage = this[Keys.ENGINE],
            speechRate = (this[Keys.RATE] ?: d.speechRate).coerceIn(0.4f, 2.0f),
            pitch = (this[Keys.PITCH] ?: d.pitch).coerceIn(0.5f, 2.0f),
            listenSeconds = (this[Keys.LISTEN] ?: d.listenSeconds)
                .coerceIn(AppSettings.MIN_LISTEN_SECONDS, AppSettings.MAX_LISTEN_SECONDS),
            preferOfflineRecognition = this[Keys.OFFLINE] ?: d.preferOfflineRecognition,
        )
    }
}
