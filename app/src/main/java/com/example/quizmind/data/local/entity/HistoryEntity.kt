package com.example.quizmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["quizId"]),
        Index(value = ["completedAt"]),
        Index(value = ["firestoreSynced"])
    ]
)
data class HistoryEntity(
    @PrimaryKey
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
    val sourceType: String = "topic",
    val firestoreSynced: Boolean = false,
    /** Serialized questions for pending online sync when download package is unavailable. */
    val questionsJson: String? = null
)
