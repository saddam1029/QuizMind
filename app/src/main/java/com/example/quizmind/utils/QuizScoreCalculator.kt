package com.example.quizmind.utils

import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.QuizScore
import kotlin.math.roundToInt

object QuizScoreCalculator {

    const val MARKS_PER_CORRECT = 10
    const val PENALTY_PER_WRONG = -1
    const val MARKS_PER_SKIPPED = 0

    fun calculateScore(questions: List<Question>): QuizScore {
        var correctCount = 0
        var wrongCount = 0
        var skippedCount = 0

        for (q in questions) {
            when {
                q.isSkipped || q.selectedAnswer.isNullOrBlank() -> {
                    skippedCount++
                }
                q.selectedAnswer.equals(q.correctAnswer, ignoreCase = true) -> {
                    correctCount++
                }
                else -> {
                    wrongCount++
                }
            }
        }

        val totalQuestions = questions.size
        val maxScore = totalQuestions * MARKS_PER_CORRECT
        val correctMarks = correctCount * MARKS_PER_CORRECT
        val negativeMarks = wrongCount * PENALTY_PER_WRONG // e.g., -2
        val finalScore = (correctMarks + negativeMarks).coerceAtLeast(0)

        val accuracyPercentage = if (totalQuestions > 0) {
            ((correctCount.toDouble() / totalQuestions) * 100).roundToInt()
        } else {
            0
        }

        return QuizScore(
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            wrongCount = wrongCount,
            skippedCount = skippedCount,
            correctMarks = correctMarks,
            negativeMarks = negativeMarks,
            finalScore = finalScore,
            maxScore = maxScore,
            accuracyPercentage = accuracyPercentage
        )
    }
}
