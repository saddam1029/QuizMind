package com.example.quizmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["quizId"]),
        Index(value = ["completedAt"])
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
    val firestoreSynced: Boolean = false
)
