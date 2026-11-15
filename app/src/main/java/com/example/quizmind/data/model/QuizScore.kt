package com.example.quizmind.data.model

import java.io.Serializable

data class QuizScore(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val correctMarks: Int,
    val negativeMarks: Int,
    val finalScore: Int,
    val maxScore: Int,
    val accuracyPercentage: Int
) : Serializable
