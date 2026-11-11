package com.kaif.mastercaller.voice

import com.kaif.mastercaller.data.AppSettings

/** One piece of the spoken announcement. [english] pieces are phone digits, always read by an English voice. */
data class SpeechSegment(val text: String, val english: Boolean)

/** Builds the spoken sentence from the user's templates. Pure Kotlin, unit-tested. */
object AnnouncementText {

    const val SAMPLE_NAME = "Test Caller"

    /** User's pronunciation if set; for the untouched default title, a clearer built-in spoken form. */
    fun spokenTitle(settings: AppSettings): String = when {
        settings.titlePronunciation.isNotBlank() -> settings.titlePronunciation
        settings.personalTitle.trim() == AppSettings.KAIF_TITLE -> when (lang(settings)) {
            "hi" -> AppSettings.DEFAULT_TITLE_SPOKEN_HI
            "ur" -> AppSettings.DEFAULT_TITLE_SPOKEN_UR
            "te" -> AppSettings.DEFAULT_TITLE_SPOKEN_TE
            else -> AppSettings.DEFAULT_TITLE_SPOKEN_EN
        }
        else -> settings.personalTitle
    }

    fun render(template: String, title: String, name: String?, number: String? = null): String =
        template
            .replace("{title}", title.trim())
            .replace("{name}", name?.trim().orEmpty())
            .replace("{number}", number?.trim().orEmpty())
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.?!،۔؟।])"), "$1")
            .replace(Regex("([,،])\\s*[,،]"), "$1")
            .replace(Regex("^[\\s,،]+"), "")
            .trim()

    /**
     * Saved contact -> named template. No saved name -> unknown-caller template (never invents a name).
     * [spoken] = true: what the voice says (pronunciation title, digits spaced out for clarity).
     * [number] is only used for unknown callers, and only when "say the number" is on.
     */
    fun forCaller(settings: AppSettings, contactName: String?, spoken: Boolean = false, number: String? = null): String {
        val title = if (spoken) spokenTitle(settings) else settings.personalTitle
        if (!contactName.isNullOrBlank()) return render(settings.announcementTemplate, title, contactName)
        val num = number?.takeIf { settings.announceNumber && it.any(Char::isDigit) }
            ?.let { if (spoken) spokenDigits(it) else it }
        return render(settings.unknownTemplate, title, null, num)
    }

    /**
     * "+91 98765 43210" -> "9 8 7 6 5, 4 3 2 1 0": single digits (no "ninety-eight thousand…"),
     * grouped with short pauses. A leading country code is dropped when the number is longer than 10 digits.
     */
    fun spokenDigits(number: String): String {
        var digits = number.filter(Char::isDigit)
        if (digits.length > 10) digits = digits.takeLast(10)
        val groups = when (digits.length) {
            10 -> listOf(digits.take(5), digits.drop(5))
            in 0..5 -> listOf(digits)
            else -> digits.chunked(4)
        }
        return groups.joinToString(", ") { g -> g.toList().joinToString(" ") }
    }

    /**
     * Splits text into language pieces and digit pieces, so numbers can always be spoken in English
     * (faster to understand than numbers in Hindi/Urdu/Telugu). Includes non-Latin digits too.
     */
    fun segments(text: String): List<SpeechSegment> {
        val out = ArrayList<SpeechSegment>()
        var last = 0
        for (m in DIGIT_RUN.findAll(text)) {
            val before = text.substring(last, m.range.first)
            if (before.isNotBlank()) out += SpeechSegment(before.trim(), english = false)
            out += SpeechSegment(toAsciiDigits(m.value.trim()), english = true)
            last = m.range.last + 1
        }
        val tail = text.substring(last)
        if (tail.isNotBlank()) out += SpeechSegment(tail.trim(), english = false)
        return out
    }

    private val DIGIT_RUN = Regex("\\p{Nd}(?:[\\p{Nd} ,]*\\p{Nd})?")

    private fun toAsciiDigits(s: String): String =
        s.map { c -> if (Character.isDigit(c)) ('0' + Character.digit(c, 10)) else c }.joinToString("")

    private fun lang(settings: AppSettings) = settings.languageTag.substringBefore('-').lowercase()
}
