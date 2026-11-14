package com.example.quizmind.utils

object WordCountHelper {

    /**
     * Counts words in the given text, correctly ignoring empty spaces,
     * multiple consecutive spaces, tabs, and newlines.
     */
    fun countWords(text: CharSequence?): Int {
        if (text.isNullOrBlank()) return 0
        val trimmed = text.toString().trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    /**
     * Splits text into individual words, ignoring consecutive whitespace.
     */
    fun getWords(text: CharSequence?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val trimmed = text.toString().trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    /**
     * Trims the text to at most [maxWords] words while preserving a clean single-space separator.
     */
    fun trimToMaxWords(text: String, maxWords: Int): String {
        val words = getWords(text)
        return if (words.size > maxWords) {
            words.take(maxWords).joinToString(" ")
        } else {
            text
        }
    }
}
