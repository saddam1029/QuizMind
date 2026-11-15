package com.example.quizmind

import com.example.quizmind.utils.TopicValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicValidatorTest {

    @Test
    fun testLetterCountExcludesSpaces() {
        assertEquals(7, TopicValidator.countLetters("Biology"))
        assertEquals(23, TopicValidator.countLetters("Human Reproductive System"))
        assertEquals(0, TopicValidator.countLetters("    "))
    }

    @Test
    fun testEnforceMaxLetters() {
        val topic = "Human Reproductive System And Modern Anatomy"
        val clamped = TopicValidator.enforceMaxLetters(topic, 30)
        assertEquals(30, TopicValidator.countLetters(clamped))
    }

    @Test
    fun testValidation() {
        assertTrue(TopicValidator.isValid("Biology"))
        assertTrue(TopicValidator.isValid("Quantum Physics"))
        assertFalse(TopicValidator.isValid(""))
        assertFalse(TopicValidator.isValid("   "))
        assertFalse(TopicValidator.isValid("12345!@#"))
    }
}
