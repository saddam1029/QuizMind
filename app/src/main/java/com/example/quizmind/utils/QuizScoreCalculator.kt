package com.example.quizmind.utils

import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.QuizScore
import kotlin.math.roundToInt

object QuizScoreCalculator {

    const val MARKS_PER_CORRECT = 1
    const val PENALTY_PER_WRONG = 0
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
        val negativeMarks = wrongCount * PENALTY_PER_WRONG
        val finalScore = (correctMarks - negativeMarks).coerceAtLeast(0)

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

    fun averageTimeSeconds(timeTakenSeconds: Long, totalQuestions: Int): Long {
        if (totalQuestions <= 0) return 0L
        return timeTakenSeconds / totalQuestions
    }

    fun formatMarks(value: Int): String = String.format(java.util.Locale.US, "%d.00", value)

    fun formatSignedMarks(value: Int): String {
        return if (value > 0) "+${formatMarks(value)}" else formatMarks(value)
    }
}
