package com.example.quizmind.data.model

import java.io.Serializable
import java.util.Locale

data class QuizResult(
    val quizId: String,
    val topic: String,
    val subject: String,
    val difficulty: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val correctMarks: Int,
    val negativeMarks: Int,
    val finalScore: Int,
    val maxScore: Int,
    val accuracyPercentage: Int,
    val timeTakenSeconds: Long,
    val questions: List<Question>,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {

    val formattedTime: String
        get() {
            val minutes = timeTakenSeconds / 60
            val seconds = timeTakenSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

    val feedbackMessage: String
        get() = when {
            accuracyPercentage >= 80 -> "Outstanding performance! You have mastered this topic."
            accuracyPercentage >= 60 -> "Good effort. Review your mistakes and try again."
            accuracyPercentage >= 40 -> "Decent attempt! Practice more to improve your score."
            else -> "Keep practicing! You will improve with more revision."
        }
}
