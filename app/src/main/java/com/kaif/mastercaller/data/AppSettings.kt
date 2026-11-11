package com.kaif.mastercaller.data

/** All user preferences. Defaults match the product spec. */
data class AppSettings(
    val personalTitle: String = DEFAULT_TITLE,
    /** How the voice should say the title (e.g. "Master Kaeef"); blank = speak [personalTitle]. */
    val titlePronunciation: String = "",
    val announcementTemplate: String = DEFAULT_TEMPLATE,
    val unknownTemplate: String = DEFAULT_UNKNOWN_TEMPLATE,
    val announcementsEnabled: Boolean = true,
    val announceUnknownCallers: Boolean = true,
    val autoSpeaker: Boolean = true,
    val voiceCommandsEnabled: Boolean = true,
    val vibrateWhileRinging: Boolean = true,
    val languageTag: String = "en-US",
    /** Exact TTS voice name; null = pick a female-sounding voice automatically. */
    val voiceName: String? = null,
    /** TTS engine package; null = system default engine. */
    val enginePackage: String? = null,
    val speechRate: Float = SWEET_RATE,
    val pitch: Float = SWEET_PITCH,
    val listenSeconds: Int = 6,
    val preferOfflineRecognition: Boolean = true,
) {
    companion object {
        const val DEFAULT_TITLE = "Master Kaif"
        const val DEFAULT_TEMPLATE = "{title}, {name} is calling. Would you like to pick up or not?"
        const val DEFAULT_UNKNOWN_TEMPLATE =
            "{title}, an unknown number is calling. Would you like to pick up or not?"
        /** "Sweet voice": a little slower and a little higher than the engine default -> softer, clearer. */
        const val SWEET_RATE = 0.9f
        const val SWEET_PITCH = 1.12f

        /** Spoken forms of the default title: a pause after "Master" so it isn't heard as "Mastercard". */
        const val DEFAULT_TITLE_SPOKEN_EN = "Master, Kaif"
        const val DEFAULT_TITLE_SPOKEN_HI = "मास्टर कैफ़"
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

    /** "Kaif, XYZ call kar rahe hain. Uthaun ya nahi?" written in Devanagari so Hindi voices read it naturally. */
    val HINDI = WordingPreset(
        template = "{title}, {name} कॉल कर रहे हैं। उठाऊँ या नहीं?",
        unknownTemplate = "{title}, एक अनजान नंबर से कॉल आ रहा है। उठाऊँ या नहीं?",
        answerHint = "उठाओ / Uthao",
        rejectHint = "नहीं / Nahi",
    )

    private val ALL = listOf(ENGLISH, HINDI)

    fun forLanguage(tag: String): WordingPreset = if (tag.startsWith("hi", ignoreCase = true)) HINDI else ENGLISH

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
    LanguageOption("en-US", "English (United States)"),
    LanguageOption("en-GB", "English (United Kingdom)"),
    LanguageOption("en-IN", "English (India)"),
    LanguageOption("en-AU", "English (Australia)"),
    LanguageOption("hi-IN", "Hindi (India) · हिंदी"),
)
