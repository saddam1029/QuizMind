package com.example.quizmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_quizzes")
data class QuizEntity(
    @PrimaryKey
    val id: String,
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
    val downloadedAt: Long = System.currentTimeMillis()
)
