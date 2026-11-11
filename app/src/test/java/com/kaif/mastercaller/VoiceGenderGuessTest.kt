package com.kaif.mastercaller

import com.kaif.mastercaller.voice.VoiceGender
import com.kaif.mastercaller.voice.VoiceGenderGuess
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceGenderGuessTest {

    @Test
    fun explicitLabels() {
        assertEquals(VoiceGender.FEMALE, VoiceGenderGuess.guess("en-US-Female-1"))
        assertEquals(VoiceGender.MALE, VoiceGenderGuess.guess("en-US-male-2"))
        assertEquals(VoiceGender.FEMALE, VoiceGenderGuess.guess("voice-x", setOf("gender=female")))
    }

    @Test
    fun googleVoiceCodes() {
        assertEquals(VoiceGender.FEMALE, VoiceGenderGuess.guess("en-us-x-sfg-local"))
        assertEquals(VoiceGender.FEMALE, VoiceGenderGuess.guess("en-us-x-tpf-network"))
        assertEquals(VoiceGender.MALE, VoiceGenderGuess.guess("en-us-x-iom-local"))
    }

    @Test
    fun samsungVoiceCodes() {
        assertEquals(VoiceGender.FEMALE, VoiceGenderGuess.guess("en-US-SMTf00"))
        assertEquals(VoiceGender.MALE, VoiceGenderGuess.guess("en-US-SMTm00"))
    }

    @Test
    fun unknownStaysUnknown() {
        assertEquals(VoiceGender.UNKNOWN, VoiceGenderGuess.guess("en-US-language"))
    }
}
