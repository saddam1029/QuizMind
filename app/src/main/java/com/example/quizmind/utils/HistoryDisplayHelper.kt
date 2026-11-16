package com.example.quizmind.utils

import com.example.quizmind.R
import com.example.quizmind.data.model.QuizSourceType

object HistoryDisplayHelper {

    fun subjectIconRes(subject: String?): Int {
        return when (subject?.trim()?.lowercase()) {
            "biology" -> R.drawable.ic_biology
            "chemistry" -> R.drawable.ic_chemistry
            "physics" -> R.drawable.ic_physics
            "mathematics", "math" -> R.drawable.ic_mathematics
            "computer", "computer science", "cs" -> R.drawable.ic_computer
            "english" -> R.drawable.ic_english
            "history" -> R.drawable.ic_histories
            "geography" -> R.drawable.ic_geography
            "economics" -> R.drawable.ic_economics
            "business" -> R.drawable.ic_business
            "engineering" -> R.drawable.ic_engineering
            "literature" -> R.drawable.ic_literature
            "art" -> R.drawable.ic_art
            "science" -> R.drawable.ic_science
            else -> R.drawable.ic_other
        }
    }

    fun sourceTypeIconRes(sourceType: String?): Int {
        return when (QuizSourceType.normalize(sourceType)) {
            QuizSourceType.TEXT -> R.drawable.ic_text
            QuizSourceType.PDF -> R.drawable.ic_pdf
            QuizSourceType.IMAGE -> R.drawable.ic_scan_notes
            else -> R.drawable.ic_topic_small
        }
    }
}
