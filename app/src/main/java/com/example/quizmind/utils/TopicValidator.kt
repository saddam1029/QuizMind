package com.example.quizmind.utils

object TopicValidator {

    const val MAX_LETTERS = 30

    /**
     * Counts the number of alphabetic characters in [text], excluding spaces, punctuation, etc.
     */
    fun countLetters(text: CharSequence?): Int {
        if (text.isNullOrEmpty()) return 0
        return text.count { it.isLetter() }
    }

    /**
     * Trims the text to at most [maxLetters] alphabetic characters,
     * preserving spaces and structure up to the limit.
     */
    fun enforceMaxLetters(text: String, maxLetters: Int = MAX_LETTERS): String {
        var letterCount = 0
        val sb = StringBuilder()
        for (char in text) {
            if (char.isLetter()) {
                if (letterCount < maxLetters) {
                    sb.append(char)
                    letterCount++
                } else {
                    break
                }
            } else {
                if (letterCount < maxLetters) {
                    sb.append(char)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Cleans and formats the topic for quiz generation:
     * trims leading/trailing spaces and collapses internal whitespace.
     */
    fun sanitizeTopic(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.trim().replace("\\s+".toRegex(), " ")
    }

    /**
     * Validates whether the topic is non-empty and contains at least one letter.
     */
    fun isValid(text: String?): Boolean {
        val sanitized = sanitizeTopic(text)
        return sanitized.isNotEmpty() && countLetters(sanitized) in 1..MAX_LETTERS
    }
}
