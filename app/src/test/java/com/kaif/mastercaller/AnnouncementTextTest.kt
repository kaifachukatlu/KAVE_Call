package com.kaif.mastercaller

import com.kaif.mastercaller.data.AppSettings
import com.kaif.mastercaller.data.withLanguage
import com.kaif.mastercaller.voice.AnnouncementText
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementTextTest {

    private val defaults = AppSettings()

    @Test
    fun knownContactUsesDefaultWording() {
        assertEquals(
            "Master Kaif, Ayesha Khan is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(defaults, "Ayesha Khan"),
        )
    }

    @Test
    fun unknownCallerNeverInventsAName() {
        assertEquals(
            "Master Kaif, an unknown number is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(defaults, null),
        )
        assertEquals(
            "Master Kaif, an unknown number is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(defaults, "   "),
        )
    }

    @Test
    fun customTitleAndTemplate() {
        val s = defaults.copy(personalTitle = "Boss", announcementTemplate = "Hey {title}! {name} on the line.")
        assertEquals("Hey Boss! Rahul on the line.", AnnouncementText.forCaller(s, "Rahul"))
    }

    @Test
    fun pronunciationOnlyAffectsSpokenText() {
        val s = defaults.copy(titlePronunciation = "Master Kaeef")
        assertEquals(
            "Master Kaif, Rahul is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(s, "Rahul"),
        )
        assertEquals(
            "Master Kaeef, Rahul is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(s, "Rahul", spoken = true),
        )
        // Default title gets a pause so it isn't heard as "Mastercard"; Hindi uses Devanagari.
        assertEquals(
            "Master, Kaif, Rahul is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(defaults, "Rahul", spoken = true),
        )
        assertEquals(
            "मास्टर कैफ़, Rahul कॉल कर रहे हैं। उठाऊँ या नहीं?",
            AnnouncementText.forCaller(defaults.withLanguage("hi-IN"), "Rahul", spoken = true),
        )
        // A custom title with no pronunciation is spoken as written.
        val boss = defaults.copy(personalTitle = "Boss")
        assertEquals(
            AnnouncementText.forCaller(boss, "Rahul"),
            AnnouncementText.forCaller(boss, "Rahul", spoken = true),
        )
    }

    @Test
    fun hindiPresetAndLanguageSwitch() {
        val hindi = defaults.withLanguage("hi-IN")
        assertEquals("hi-IN", hindi.languageTag)
        assertEquals(
            "Master Kaif, Rahul कॉल कर रहे हैं। उठाऊँ या नहीं?",
            AnnouncementText.forCaller(hindi, "Rahul"),
        )
        assertEquals(
            "Master Kaif, एक अनजान नंबर से कॉल आ रहा है। उठाऊँ या नहीं?",
            AnnouncementText.forCaller(hindi, null),
        )
        // Back to English restores English wording.
        assertEquals(AppSettings.DEFAULT_TEMPLATE, hindi.withLanguage("en-US").announcementTemplate)
        // Customised wording is never overwritten by a language switch.
        val custom = defaults.copy(announcementTemplate = "Yo {title}, {name}!")
        assertEquals("Yo {title}, {name}!", custom.withLanguage("hi-IN").announcementTemplate)
    }

    @Test
    fun emptyTitleDoesNotLeaveDanglingComma() {
        val s = defaults.copy(personalTitle = "")
        assertEquals(
            "Rahul is calling. Would you like to pick up or not?",
            AnnouncementText.forCaller(s, "Rahul"),
        )
    }
}
