package com.kaif.mastercaller.voice

enum class VoiceGender { FEMALE, MALE, UNKNOWN }

/**
 * Android's TTS API has no gender field, and engines name voices differently. This is a best-effort
 * guess used only to sort/label voices ("Likely female"); the user previews and picks the final voice.
 */
object VoiceGenderGuess {

    // Google Speech Services voice codes ("en-us-x-sfg-local" -> "sfg").
    private val googleFemale = setOf(
        "sfg", "iob", "iog", "tpc", "tpf", // en-US
        "gba", "gbc", "gbg", // en-GB
        "aua", "auc", // en-AU
        "ena", "enc", "ahp", // en-IN
        "hia", "hic", // hi-IN
        "tef", // te-IN
    )
    private val googleMale = setOf(
        "iol", "iom", "tpd",
        "gbb", "gbd", "rjs",
        "aub", "aud",
        "end", "ene",
        "hid", "hie",
        "tem",
    )

    private val googleCode = Regex("-x-([a-z]{3})")
    private val samsungCode = Regex("smt([fm])\\d")
    private val wordMale = Regex("(^|[^a-z])male([^a-z]|$)")

    fun guess(name: String, features: Collection<String> = emptyList()): VoiceGender {
        val n = name.lowercase()
        val f = features.joinToString(" ").lowercase()
        if ("female" in n || "female" in f || "woman" in n) return VoiceGender.FEMALE
        if (wordMale.containsMatchIn(n) || wordMale.containsMatchIn(f)) return VoiceGender.MALE
        googleCode.find(n)?.groupValues?.get(1)?.let { code ->
            if (code in googleFemale) return VoiceGender.FEMALE
            if (code in googleMale) return VoiceGender.MALE
        }
        samsungCode.find(n)?.let { return if (it.groupValues[1] == "f") VoiceGender.FEMALE else VoiceGender.MALE }
        return VoiceGender.UNKNOWN
    }
}
