package com.example.quizmind.data.model

import java.io.Serializable
import java.util.UUID

data class Question(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String,
    val hint: String,
    var selectedAnswer: String? = null,
    var isSkipped: Boolean = false
) : Serializable
