package com.example.quizmind

import com.example.quizmind.utils.PdfMetadataHelper
import com.example.quizmind.utils.WordCountHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class WordCountHelperTest {

    @Test
    fun testEmptyAndBlankText() {
        assertEquals(0, WordCountHelper.countWords(""))
        assertEquals(0, WordCountHelper.countWords("   "))
        assertEquals(0, WordCountHelper.countWords("\n\t  \n"))
        assertEquals(0, WordCountHelper.countWords(null))
    }

    @Test
    fun testWordCountWithMultipleSpacesAndNewlines() {
        val text = "Biology   chapter   3 \n\n covers \t photosynthesis  and respiration."
        assertEquals(7, WordCountHelper.countWords(text))
    }

    @Test
    fun testTrimToMaxWords() {
        val thirtyFiveWords = (1..35).joinToString(" ") { "word$it" }
        assertEquals(35, WordCountHelper.countWords(thirtyFiveWords))

        val trimmed = WordCountHelper.trimToMaxWords(thirtyFiveWords, 30)
        assertEquals(30, WordCountHelper.countWords(trimmed))

        val expected = (1..30).joinToString(" ") { "word$it" }
        assertEquals(expected, trimmed)
    }

    @Test
    fun testTrimToMaxWordsWhenUnderLimit() {
        val tenWords = (1..10).joinToString(" ") { "word$it" }
        val trimmed = WordCountHelper.trimToMaxWords(tenWords, 30)
        assertEquals(tenWords, trimmed)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", PdfMetadataHelper.formatFileSize(0))
        assertEquals("512 B", PdfMetadataHelper.formatFileSize(512))
        assertEquals("1.5 KB", PdfMetadataHelper.formatFileSize(1536))
        assertEquals("2.4 MB", PdfMetadataHelper.formatFileSize(2516582))
    }
}
