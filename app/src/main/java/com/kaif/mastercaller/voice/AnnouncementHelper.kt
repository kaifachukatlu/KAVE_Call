package com.kaif.mastercaller.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.kaif.mastercaller.data.AppSettings
import com.kaif.mastercaller.data.SettingsRepository
import com.kaif.mastercaller.data.VoicePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

data class VoiceOption(
    val name: String,
    val localeTag: String,
    val localeLabel: String,
    val gender: VoiceGender,
    val requiresNetwork: Boolean,
    val notInstalled: Boolean,
    val quality: Int,
) {
    /** "sfg" from "en-us-x-sfg-local", otherwise the raw name. */
    val shortName: String
        get() = Regex("-x-([a-z0-9]+)").find(name)?.groupValues?.get(1)?.uppercase() ?: name
}

data class EngineOption(val packageName: String, val label: String)

enum class TtsState { INITIALIZING, READY, FAILED }

data class TtsStatus(
    val state: TtsState = TtsState.INITIALIZING,
    val engineLabel: String? = null,
    val activeVoice: VoiceOption? = null,
    val missingData: Boolean = false,
    val languageUnsupported: Boolean = false,
    val message: String? = null,
)

/**
 * Female-voice caller announcements using the phone's built-in TextToSpeech engine (offline, free).
 * Main-thread only.
 */
class AnnouncementHelper(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    enum class Purpose { CALL, PREVIEW }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsState = TtsState.INITIALIZING
    private var enginePackage: String? = null
    private val waiting = ArrayList<(Boolean) -> Unit>()
    private val callbacks = HashMap<String, (Boolean) -> Unit>()
    private var counter = 0
    private var platformVoices: List<Voice> = emptyList()

    private val _status = MutableStateFlow(TtsStatus())
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private val _engines = MutableStateFlow<List<EngineOption>>(emptyList())
    val engines: StateFlow<List<EngineOption>> = _engines.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settingsFlow.map { it.enginePackage }.distinctUntilChanged()
                .collect { pkg -> initEngine(pkg) }
        }
        scope.launch {
            settingsRepository.settingsFlow
                .distinctUntilChanged { a, b ->
                    a.languageTag == b.languageTag && a.voiceName == b.voiceName &&
                        a.enginePackage == b.enginePackage && a.voicePreference == b.voicePreference
                }
                .collect { refreshVoices(it) }
        }
    }

    // --- public API ----------------------------------------------------------------------------

    fun speak(text: String, purpose: Purpose, voiceOverride: String? = null, onDone: (Boolean) -> Unit) {
        whenReady { ready ->
            val engine = tts
            if (!ready || engine == null) {
                onDone(false)
                return@whenReady
            }
            val settings = settingsRepository.settings.value
            engine.setAudioAttributes(attributesFor(purpose))
            // In non-English languages, phone digits are spoken by an English voice (quicker to understand).
            val english = settings.languageTag.startsWith("en", ignoreCase = true)
            val parts = if (english) {
                listOf(SpeechSegment(text, english = false))
            } else {
                AnnouncementText.segments(text).ifEmpty { listOf(SpeechSegment(text, english = false)) }
            }
            val base = "mc-utt-${++counter}"
            val lastId = "$base-${parts.lastIndex}"
            callbacks[lastId] = onDone
            parts.forEachIndexed { i, part ->
                if (part.english && !english) applyEnglishDigits(engine, settings) else applySettings(engine, settings, voiceOverride)
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                if (engine.speak(part.text, mode, Bundle(), "$base-$i") != TextToSpeech.SUCCESS) {
                    callbacks.remove(lastId)?.invoke(false)
                    return@whenReady
                }
            }
            // Safety net: some engines never report completion.
            handler.postDelayed({ callbacks.remove(lastId)?.invoke(false) }, SAFETY_TIMEOUT_MS)
        }
    }

    suspend fun speakAwait(text: String, purpose: Purpose): Boolean = suspendCancellableCoroutine { cont ->
        speak(text, purpose) { ok -> if (cont.isActive) cont.resume(ok) }
        cont.invokeOnCancellation { handler.post { stop() } }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
        }
        val pending = callbacks.values.toList()
        callbacks.clear()
        pending.forEach { it(false) }
    }

    /** What the preview shows on screen. */
    fun previewText(settings: AppSettings): String =
        AnnouncementText.forCaller(settings, AnnouncementText.SAMPLE_NAME)

    /** What the preview voice actually says (uses the title pronunciation). */
    fun previewSpeech(settings: AppSettings): String =
        AnnouncementText.forCaller(settings, AnnouncementText.SAMPLE_NAME, spoken = true)

    fun installVoiceDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            enginePackage?.let { setPackage(it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // --- engine lifecycle ----------------------------------------------------------------------

    private fun initEngine(pkg: String?) {
        val old = tts
        tts = null
        stop()
        try {
            old?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "shutdown failed", e)
        }
        enginePackage = pkg
        ttsState = TtsState.INITIALIZING
        _status.value = _status.value.copy(state = TtsState.INITIALIZING, message = null)

        var created: TextToSpeech? = null
        // Posting defers onInit until `created` is assigned (some engines call back synchronously).
        val listener = TextToSpeech.OnInitListener { result -> handler.post { onInit(created, result) } }
        created = try {
            if (pkg.isNullOrBlank()) TextToSpeech(appContext, listener) else TextToSpeech(appContext, listener, pkg)
        } catch (e: Exception) {
            Log.e(TAG, "TTS construction failed", e)
            null
        }
        tts = created
        if (created == null) failInit("Could not start the text-to-speech engine.")
    }

    private fun onInit(engine: TextToSpeech?, result: Int) {
        if (engine == null || engine !== tts) return // stale engine
        if (result != TextToSpeech.SUCCESS) {
            failInit("No working text-to-speech engine. Install or enable \"Speech Services by Google\".")
            return
        }
        ttsState = TtsState.READY
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId, true)

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) = finish(utteranceId, false)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
        })
        _engines.value = try {
            engine.engines.map { EngineOption(it.name, it.label) }
        } catch (e: Exception) {
            emptyList()
        }
        refreshVoices()
        flushWaiting(true)
    }

    private fun failInit(message: String) {
        ttsState = TtsState.FAILED
        _status.value = TtsStatus(state = TtsState.FAILED, message = message)
        flushWaiting(false)
    }

    private fun whenReady(block: (Boolean) -> Unit) {
        when (ttsState) {
            TtsState.READY -> block(true)
            TtsState.FAILED -> block(false)
            TtsState.INITIALIZING -> waiting.add(block)
        }
    }

    private fun flushWaiting(ready: Boolean) {
        val pending = waiting.toList()
        waiting.clear()
        pending.forEach { it(ready) }
    }

    private fun finish(utteranceId: String?, ok: Boolean) {
        handler.post { utteranceId?.let { callbacks.remove(it) }?.invoke(ok) }
    }

    // --- voices --------------------------------------------------------------------------------

    private fun refreshVoices(settings: AppSettings = settingsRepository.settings.value) {
        val engine = tts ?: return
        if (ttsState != TtsState.READY) return
        val locale = Locale.forLanguageTag(settings.languageTag)

        platformVoices = try {
            engine.voices?.toList().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "getVoices failed", e)
            emptyList()
        }
        val options = platformVoices
            .filter { it.locale.language == locale.language }
            .map { it.toOption() }
            .sortedWith(
                compareByDescending<VoiceOption> { it.localeTag.equals(locale.toLanguageTag(), ignoreCase = true) }
                    .thenByDescending { it.gender == wantedGender(settings) }
                    .thenBy { it.notInstalled }
                    .thenBy { it.requiresNetwork }
                    .thenByDescending { it.quality }
                    .thenBy { it.name },
            )
        _voices.value = options

        val availability = try {
            engine.isLanguageAvailable(locale)
        } catch (e: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        val engineLabel = _engines.value.firstOrNull { it.packageName == (enginePackage ?: engine.defaultEngine) }?.label
        _status.value = TtsStatus(
            state = TtsState.READY,
            engineLabel = engineLabel,
            activeVoice = pickVoice(settings, options),
            missingData = availability == TextToSpeech.LANG_MISSING_DATA,
            languageUnsupported = availability == TextToSpeech.LANG_NOT_SUPPORTED,
        )
    }

    private fun wantedGender(settings: AppSettings): VoiceGender =
        if (settings.voicePreference == VoicePreference.MALE) VoiceGender.MALE else VoiceGender.FEMALE

    /** User's saved voice if still installed, else the best on-device voice of the chosen gender. */
    private fun pickVoice(settings: AppSettings, options: List<VoiceOption>): VoiceOption? {
        val tag = Locale.forLanguageTag(settings.languageTag).toLanguageTag()
        val want = wantedGender(settings)
        val usable = options.filter { !it.notInstalled }
        return options.firstOrNull { it.name == settings.voiceName && !it.notInstalled }
            ?: usable.firstOrNull { it.gender == want && !it.requiresNetwork && it.localeTag.equals(tag, true) }
            ?: usable.firstOrNull { it.gender == want && !it.requiresNetwork }
            ?: usable.firstOrNull { it.gender == want }
            ?: usable.firstOrNull { it.gender == VoiceGender.UNKNOWN && !it.requiresNetwork }
            ?: usable.firstOrNull { !it.requiresNetwork && it.localeTag.equals(tag, true) }
    }

    /** English voice (same gender, Indian English preferred) used only for reading phone digits. */
    private fun applyEnglishDigits(engine: TextToSpeech, settings: AppSettings) {
        try {
            val want = wantedGender(settings)
            val english = platformVoices
                .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                .filter { v -> !(v.features ?: emptySet()).contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                .sortedWith(
                    compareByDescending<Voice> { it.locale.country == "IN" }
                        .thenByDescending { VoiceGenderGuess.guess(it.name, it.features ?: emptySet()) == want }
                        .thenByDescending { it.quality },
                )
                .firstOrNull()
            if (english != null) engine.setVoice(english) else engine.setLanguage(Locale.forLanguageTag("en-IN"))
            engine.setSpeechRate(settings.speechRate)
            engine.setPitch(settings.pitch)
        } catch (e: Exception) {
            Log.w(TAG, "English digit voice failed", e)
        }
    }

    private fun applySettings(engine: TextToSpeech, settings: AppSettings, voiceOverride: String?) {
        try {
            engine.setLanguage(Locale.forLanguageTag(settings.languageTag))
            val chosen = voiceOverride ?: pickVoice(settings, _voices.value)?.name
            platformVoices.firstOrNull { it.name == chosen }?.let { engine.setVoice(it) }
            engine.setSpeechRate(settings.speechRate)
            engine.setPitch(settings.pitch)
        } catch (e: Exception) {
            Log.w(TAG, "Applying TTS settings failed", e)
        }
    }

    private fun attributesFor(purpose: Purpose): AudioAttributes = when (purpose) {
        // With Bluetooth headphones / car audio connected, speak through them (media route);
        // otherwise play on the ring stream so it follows ring volume while the phone is ringing.
        Purpose.CALL -> AudioAttributes.Builder()
            .setUsage(if (bluetoothAudioConnected()) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        Purpose.PREVIEW -> AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    /** True when Bluetooth earbuds / headset / car audio are connected as an output. */
    private fun bluetoothAudioConnected(): Boolean = try {
        val am = appContext.getSystemService(android.media.AudioManager::class.java)
        val bt = buildSet {
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                add(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        am?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in bt } == true
    } catch (e: Exception) {
        false
    }

    private fun Voice.toOption(): VoiceOption {
        val feats = try {
            features.orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        return VoiceOption(
            name = name,
            localeTag = locale.toLanguageTag(),
            localeLabel = locale.getDisplayName(Locale.ENGLISH),
            gender = VoiceGenderGuess.guess(name, feats),
            requiresNetwork = isNetworkConnectionRequired,
            notInstalled = feats.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
            quality = quality,
        )
    }

    private companion object {
        const val TAG = "AnnouncementHelper"
        const val SAFETY_TIMEOUT_MS = 20_000L
    }
}
