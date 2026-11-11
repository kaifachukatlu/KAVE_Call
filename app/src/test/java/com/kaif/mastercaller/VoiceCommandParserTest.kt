package com.kaif.mastercaller

import com.kaif.mastercaller.voice.VoiceCommand
import com.kaif.mastercaller.voice.VoiceCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun answerCommands() {
        listOf("Pick up", "pick up the call", "Answer", "accept", "Yes", "yes please", "Pickup!", "Haan uthao")
            .forEach { assertEquals(it, VoiceCommand.ANSWER, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun rejectCommands() {
        listOf("Reject", "Decline", "Cut the call", "No", "no thanks", "not now", "hang up", "नहीं")
            .forEach { assertEquals(it, VoiceCommand.REJECT, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun negatedAnswerIsReject() {
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("don't pick up"))
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("Do not answer"))
        assertEquals(VoiceCommand.REJECT, VoiceCommandParser.parse("don’t answer it")) // curly apostrophe
    }

    @Test
    fun hindiCommands() {
        listOf("उठाओ", "हाँ उठाओ", "हां", "उठा लो", "उठाऊँ", "पिक अप", "uthao", "haan ji", "उठाओ ना")
            .forEach { assertEquals(it, VoiceCommand.ANSWER, VoiceCommandParser.parse(it)) }
        listOf("नहीं", "नहीं उठाओ", "मत उठाओ", "काट दो", "रहने दो", "nahi", "mat uthao", "nahi uthao", "रिजेक्ट")
            .forEach { assertEquals(it, VoiceCommand.REJECT, VoiceCommandParser.parse(it)) }
    }

    @Test
    fun ambiguousOrUnrelatedReturnsNull() {
        assertNull(VoiceCommandParser.parse("yes no"))
        assertNull(VoiceCommandParser.parse("don't reject"))
        assertNull(VoiceCommandParser.parse("what's the weather"))
        assertNull(VoiceCommandParser.parse(""))
        assertNull(VoiceCommandParser.parse(null))
    }

    @Test
    fun wordBoundariesAreRespected() {
        // "know" contains "no" and "cutlery" contains "cut" but neither is a command.
        assertNull(VoiceCommandParser.parse("I know"))
        assertNull(VoiceCommandParser.parse("cutlery"))
        assertNull(VoiceCommandParser.parse("yesterday"))
    }

    @Test
    fun parseBestTakesFirstDecisiveHypothesis() {
        assertEquals(
            VoiceCommand.ANSWER,
            VoiceCommandParser.parseBest(listOf("pickled", "pick up", "no")),
        )
        assertNull(VoiceCommandParser.parseBest(listOf("hello", "world")))
    }
}
