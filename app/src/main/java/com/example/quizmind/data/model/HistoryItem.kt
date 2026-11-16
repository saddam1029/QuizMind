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
    val firestoreSynced: Boolean = true,
    /** False for Room download packages that have never been attempted locally. */
    val isCompleted: Boolean = true
) {
    val scoreLabel: String
        get() = if (isCompleted) "$finalScore/$maxScore" else "Downloaded"

    val subtitle: String
        get() = if (isCompleted) {
            "$totalQuestions questions · $difficulty · $accuracyPercentage%"
        } else {
            "$totalQuestions questions · $difficulty · Downloaded"
        }

    companion object {
        fun downloadPlaceholderId(quizId: String): String = "download_$quizId"

        fun isDownloadPlaceholderId(completionId: String): Boolean =
            completionId.startsWith("download_")
    }
}
