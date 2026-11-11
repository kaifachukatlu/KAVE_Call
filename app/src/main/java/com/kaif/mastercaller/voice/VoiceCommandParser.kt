package com.kaif.mastercaller.voice

enum class VoiceCommand { ANSWER, REJECT }

/**
 * Turns recognised speech into a command. Pure Kotlin so it is unit-tested.
 * Conservative by design: anything ambiguous (e.g. "yes no") returns null, so the call keeps ringing
 * and the touch buttons remain the fallback.
 */
object VoiceCommandParser {

    val ANSWER_PHRASES = listOf(
        "pick up", "pickup", "pick it up", "answer", "accept", "yes", "yeah", "yep", "yup",
        "receive", "attend", "take it", "take the call",
        // Hindi / Hinglish (hi-IN recognition returns Devanagari, sometimes transliterated English)
        "haan", "haa", "haan ji", "uthao", "utha lo", "utha do", "uthaun", "uthau",
        "हाँ", "हां", "हा", "हाँ जी", "जी हाँ", "उठाओ", "उठा लो", "उठा दो", "उठाऊँ", "उठाऊं", "उठाइए",
        "पिक अप", "पिकअप", "आंसर", "एक्सेप्ट",
        // Urdu
        "ہاں", "ہاں جی", "جی ہاں", "اٹھاؤ", "اٹھا لو", "اٹھا دو", "اٹھاؤں", "پک اپ",
        // Telugu
        "ఎత్తు", "ఎత్తండి", "ఎత్తు కాల్", "అవును", "సరే", "లిఫ్ట్", "లిఫ్ట్ చేయి", "పిక్ అప్", "ఆన్సర్", "ettu", "avunu",
    )

    val REJECT_PHRASES = listOf(
        "reject", "decline", "cut the call", "cut it", "cut", "no", "nope", "nah", "ignore",
        "not now", "hang up", "end the call", "end call", "busy",
        // Hindi / Hinglish
        "nahi", "nahin", "mana", "kaat do", "kato", "rehne do",
        "नहीं", "नही", "मना", "काटो", "काट दो", "रहने दो", "रिजेक्ट", "डिक्लाइन", "कट", "कट करो",
        // Urdu
        "نہیں", "کاٹ دو", "کاٹو", "رہنے دو", "منع", "ریجیکٹ",
        // Telugu
        "వద్దు", "కట్", "కట్ చేయి", "కట్ చెయ్యి", "రిజెక్ట్", "లేదు", "ఎత్తొద్దు", "ఎత్తకు", "vaddu",
    )

    /** Pure negators: "don't pick up" / "mat uthao" -> reject, but "don't reject" stays ambiguous. */
    private val NEGATIONS = listOf("don't", "dont", "do not", "never", "mat", "मत", "مت")

    /** Hindi "no": on its own it rejects, and it also negates an answer word ("नहीं उठाओ"). */
    private val HINDI_NO = listOf("nahi", "nahin", "नहीं", "नही", "نہیں", "వద్దు", "vaddu", "లేదు")

    fun parse(text: String?): VoiceCommand? {
        if (text.isNullOrBlank()) return null
        val norm = normalize(text)
        if (norm.isBlank()) return null
        val answer = ANSWER_PHRASES.any { containsPhrase(norm, it) }
        val reject = REJECT_PHRASES.any { containsPhrase(norm, it) }
        val negated = NEGATIONS.any { containsPhrase(norm, it) }
        val hindiNo = HINDI_NO.any { containsPhrase(norm, it) }
        return when {
            answer && (negated || hindiNo) -> VoiceCommand.REJECT // "don't pick up", "नहीं उठाओ"
            answer && !reject -> VoiceCommand.ANSWER
            reject && !answer && !negated -> VoiceCommand.REJECT
            else -> null // nothing heard, or ambiguous ("yes no", "don't reject")
        }
    }

    /** Recognisers return several hypotheses ordered by confidence; take the first decisive one. */
    fun parseBest(candidates: List<String>): VoiceCommand? =
        candidates.asSequence().mapNotNull { parse(it) }.firstOrNull()

    internal fun normalize(text: String): String =
        text.lowercase()
            .replace('’', '\'')
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun containsPhrase(normalized: String, phrase: String): Boolean =
        " $normalized ".contains(" $phrase ")
}
