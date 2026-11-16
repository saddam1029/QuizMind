package com.example.quizmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_quizzes")
data class QuizEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val topic: String,
    val subject: String,
    val difficulty: String,
    val totalQuestions: Int,
    val timerEnabled: Boolean = false,
    val sourceType: String = "topic",
    val downloadedAt: Long = System.currentTimeMillis()
)
