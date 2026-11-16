package com.example.quizmind.data.model

object QuizSourceType {
    const val TOPIC = "topic"
    const val TEXT = "text"
    const val PDF = "pdf"
    const val IMAGE = "image"

    fun normalize(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            TOPIC, "topic quiz" -> TOPIC
            TEXT, "text/notes", "notes", "text notes" -> TEXT
            PDF -> PDF
            IMAGE, "scan", "scan notes", "scan_notes" -> IMAGE
            else -> TOPIC
        }
    }

    fun displayLabel(raw: String?): String {
        return when (normalize(raw)) {
            TEXT -> "Text"
            PDF -> "PDF"
            IMAGE -> "Scan Notes"
            else -> "Topic"
        }
    }
}
