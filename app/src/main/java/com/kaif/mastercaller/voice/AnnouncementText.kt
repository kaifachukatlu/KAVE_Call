package com.kaif.mastercaller.voice

import com.kaif.mastercaller.data.AppSettings

/** Builds the spoken sentence from the user's templates. Pure Kotlin, unit-tested. */
object AnnouncementText {

    const val SAMPLE_NAME = "Test Caller"

    /** User's pronunciation if set; for the untouched default title, a clearer built-in spoken form. */
    fun spokenTitle(settings: AppSettings): String = when {
        settings.titlePronunciation.isNotBlank() -> settings.titlePronunciation
        settings.personalTitle.trim() == AppSettings.DEFAULT_TITLE ->
            if (settings.languageTag.startsWith("hi", ignoreCase = true)) {
                AppSettings.DEFAULT_TITLE_SPOKEN_HI
            } else {
                AppSettings.DEFAULT_TITLE_SPOKEN_EN
            }
        else -> settings.personalTitle
    }

    fun render(template: String, title: String, name: String?): String =
        template
            .replace("{title}", title.trim())
            .replace("{name}", name?.trim().orEmpty())
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.?!])"), "$1")
            .replace(Regex("^[\\s,]+"), "")
            .trim()

    /**
     * Saved contact -> named template. No saved name -> unknown-caller template (never invents a name).
     * [spoken] = true uses the pronunciation spelling of the title (what the TTS voice should say).
     */
    fun forCaller(settings: AppSettings, contactName: String?, spoken: Boolean = false): String {
        val title = if (spoken) spokenTitle(settings) else settings.personalTitle
        return if (!contactName.isNullOrBlank()) {
            render(settings.announcementTemplate, title, contactName)
        } else {
            render(settings.unknownTemplate, title, null)
        }
    }
}
