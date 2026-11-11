package com.kaif.mastercaller.data

/** Which kind of voice the announcer should pick automatically. */
enum class VoicePreference { FEMALE, MALE }

/** All user preferences. Defaults match the product spec. */
data class AppSettings(
    val personalTitle: String = DEFAULT_TITLE,
    /** Optional spoken spelling of the title (no longer shown in the UI; kept for existing users). */
    val titlePronunciation: String = "",
    val announcementTemplate: String = DEFAULT_TEMPLATE,
    val unknownTemplate: String = DEFAULT_UNKNOWN_TEMPLATE,
    val announcementsEnabled: Boolean = true,
    val announceUnknownCallers: Boolean = true,
    /** Read the caller's number aloud for unknown callers (always in English digits). */
    val announceNumber: Boolean = true,
    val autoSpeaker: Boolean = true,
    val voiceCommandsEnabled: Boolean = true,
    val vibrateWhileRinging: Boolean = true,
    val languageTag: String = "en-US",
    val voicePreference: VoicePreference = VoicePreference.FEMALE,
    /** Exact TTS voice name; null = pick a voice automatically from [voicePreference]. */
    val voiceName: String? = null,
    /** TTS engine package; null = system default engine. */
    val enginePackage: String? = null,
    val speechRate: Float = SWEET_RATE,
    val pitch: Float = SWEET_PITCH,
    val listenSeconds: Int = 6,
    val preferOfflineRecognition: Boolean = true,
    /** When a wallpaper is set for a contact, also save it as their photo in Contacts / Google Phone. */
    val syncWallpaperToContacts: Boolean = true,
) {
    companion object {
        const val DEFAULT_TITLE = "Master Kaif"
        const val DEFAULT_TEMPLATE = "{title}, {name} is calling. Would you like to pick up or not?"
        const val DEFAULT_UNKNOWN_TEMPLATE =
            "{title}, an unknown number {number} is calling. Would you like to pick up or not?"

        /** Wording shipped before {number} existed; upgraded automatically. */
        const val LEGACY_UNKNOWN_EN = "{title}, an unknown number is calling. Would you like to pick up or not?"
        const val LEGACY_UNKNOWN_HI = "{title}, एक अनजान नंबर से कॉल आ रहा है। उठाऊँ या नहीं?"

        /** "Sweet voice": a little slower and a little higher than the engine default -> softer, clearer. */
        const val SWEET_RATE = 0.9f
        const val SWEET_PITCH = 1.12f

        /** Natural settings for a male voice. */
        const val MALE_RATE = 0.95f
        const val MALE_PITCH = 0.95f

        /** Spoken forms of the default title: a pause after "Master" so it isn't heard as "Mastercard". */
        const val DEFAULT_TITLE_SPOKEN_EN = "Master, Kaif"
        const val DEFAULT_TITLE_SPOKEN_HI = "मास्टर कैफ़"
        const val DEFAULT_TITLE_SPOKEN_UR = "ماسٹر کیف"
        const val DEFAULT_TITLE_SPOKEN_TE = "మాస్టర్ కైఫ్"
        const val MIN_LISTEN_SECONDS = 4
        const val MAX_LISTEN_SECONDS = 10
    }
}

/** Ready-made announcement wording per language, plus the command hint shown while listening. */
data class WordingPreset(
    val template: String,
    val unknownTemplate: String,
    val answerHint: String,
    val rejectHint: String,
)

object WordingPresets {
    val ENGLISH = WordingPreset(
        template = AppSettings.DEFAULT_TEMPLATE,
        unknownTemplate = AppSettings.DEFAULT_UNKNOWN_TEMPLATE,
        answerHint = "Pick up",
        rejectHint = "Reject",
    )

    /** "Kaif, XYZ call kar rahe hain. Uthaun ya nahi?" (Devanagari so Hindi voices read it naturally). */
    val HINDI = WordingPreset(
        template = "{title}, {name} कॉल कर रहे हैं। उठाऊँ या नहीं?",
        unknownTemplate = "{title}, एक अनजान नंबर {number} से कॉल आ रहा है। उठाऊँ या नहीं?",
        answerHint = "उठाओ / Uthao",
        rejectHint = "नहीं / Nahi",
    )

    /** Same sentence in Urdu script. */
    val URDU = WordingPreset(
        template = "{title}، {name} کال کر رہے ہیں۔ اٹھاؤں یا نہیں؟",
        unknownTemplate = "{title}، ایک نامعلوم نمبر {number} سے کال آ رہی ہے۔ اٹھاؤں یا نہیں؟",
        answerHint = "اٹھاؤ / Uthao",
        rejectHint = "نہیں / Nahi",
    )

    /** "Kaif, XYZ is calling. Shall I pick up or not?" in Telugu. */
    val TELUGU = WordingPreset(
        template = "{title}, {name} కాల్ చేస్తున్నారు. ఎత్తమంటారా, వద్దా?",
        unknownTemplate = "{title}, తెలియని నంబర్ {number} నుండి కాల్ వస్తోంది. ఎత్తమంటారా, వద్దా?",
        answerHint = "ఎత్తు / Ettu",
        rejectHint = "వద్దు / Vaddu",
    )

    private val ALL = listOf(ENGLISH, HINDI, URDU, TELUGU)

    fun forLanguage(tag: String): WordingPreset = when (tag.substringBefore('-').lowercase()) {
        "hi" -> HINDI
        "ur" -> URDU
        "te" -> TELUGU
        else -> ENGLISH
    }

    /** True if the wording is one of the built-in presets (i.e. the user hasn't customised it). */
    fun isPreset(s: AppSettings): Boolean =
        ALL.any { it.template == s.announcementTemplate && it.unknownTemplate == s.unknownTemplate }
}

/** Switch language; built-in wording follows the language, customised wording is kept. */
fun AppSettings.withLanguage(tag: String): AppSettings {
    val switched = copy(languageTag = tag, voiceName = null)
    if (!WordingPresets.isPreset(this)) return switched
    val preset = WordingPresets.forLanguage(tag)
    return switched.copy(announcementTemplate = preset.template, unknownTemplate = preset.unknownTemplate)
}

/** Switch between a female and a male announcer (auto-picked), with natural speed/pitch for each. */
fun AppSettings.withVoicePreference(pref: VoicePreference): AppSettings = copy(
    voicePreference = pref,
    voiceName = null,
    speechRate = if (pref == VoicePreference.MALE) AppSettings.MALE_RATE else AppSettings.SWEET_RATE,
    pitch = if (pref == VoicePreference.MALE) AppSettings.MALE_PITCH else AppSettings.SWEET_PITCH,
)

/** Reset the wording to the built-in preset for the current language. */
fun AppSettings.withDefaultWording(): AppSettings {
    val preset = WordingPresets.forLanguage(languageTag)
    return copy(
        personalTitle = AppSettings.DEFAULT_TITLE,
        titlePronunciation = "",
        announcementTemplate = preset.template,
        unknownTemplate = preset.unknownTemplate,
    )
}

data class LanguageOption(val tag: String, val label: String)

val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("en-IN", "English (India)"),
    LanguageOption("en-US", "English (United States)"),
    LanguageOption("en-GB", "English (United Kingdom)"),
    LanguageOption("hi-IN", "Hindi · हिंदी"),
    LanguageOption("ur-IN", "Urdu · اردو"),
    LanguageOption("te-IN", "Telugu · తెలుగు"),
)
