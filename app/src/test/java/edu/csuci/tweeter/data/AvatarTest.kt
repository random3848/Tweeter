package edu.csuci.tweeter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarTest {
    @Test
    fun initialsOf_twoWords_firstLetterOfEach() {
        assertEquals("MK", initialsOf("Maya Kim"))
    }

    @Test
    fun initialsOf_oneWord_firstLetter() {
        assertEquals("M", initialsOf("Maya"))
    }

    @Test
    fun initialsOf_threeWords_firstAndLastWords() {
        assertEquals("MK", initialsOf("Maya Rose Kim"))
    }

    @Test
    fun initialsOf_lowercaseAndExtraSpaces_uppercasedAndTrimmed() {
        assertEquals("JR", initialsOf("  jordan   reyes "))
    }

    @Test
    fun initialsOf_blank_questionMark() {
        assertEquals("?", initialsOf("   "))
    }

    @Test
    fun initialsOf_emoji_keepsWholeCharacter() {
        assertEquals("🌿M", initialsOf("🌿leaf Maya"))
    }
}
