package com.example.quizmind.data.model

data class HistoryItem(
    val completionId: String,
    val quizId: String,
    val title: String,
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
    val completedAt: Long,
    val source: String,
    val sourceType: String = QuizSourceType.TOPIC,
    val isDownloaded: Boolean = false,
    val firestoreSynced: Boolean = true
) {
    val scoreLabel: String
        get() = "$finalScore/$maxScore"

    val subtitle: String
        get() = "$totalQuestions questions · $difficulty · $accuracyPercentage%"
}
