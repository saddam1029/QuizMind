package com.example.quizmind.data.model

import java.io.Serializable
import java.util.UUID

data class Quiz(
    val id: String = UUID.randomUUID().toString(),
    val topic: String,
    val subject: String,
    val difficulty: String,
    val questionCount: Int,
    val questions: List<Question>,
    val timerEnabled: Boolean = false,
    var timeTakenSeconds: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceType: String = QuizSourceType.TOPIC
) : Serializable
