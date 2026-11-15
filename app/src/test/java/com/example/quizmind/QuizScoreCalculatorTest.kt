package com.example.quizmind

import com.example.quizmind.data.model.Question
import com.example.quizmind.utils.QuizScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizScoreCalculatorTest {

    @Test
    fun testAllCorrect() {
        val questions = listOf(
            Question(question = "Q1", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "A"),
            Question(question = "Q2", options = listOf("A", "B", "C", "D"), correctAnswer = "B", explanation = "E", hint = "H", selectedAnswer = "B"),
            Question(question = "Q3", options = listOf("A", "B", "C", "D"), correctAnswer = "C", explanation = "E", hint = "H", selectedAnswer = "C"),
            Question(question = "Q4", options = listOf("A", "B", "C", "D"), correctAnswer = "D", explanation = "E", hint = "H", selectedAnswer = "D"),
            Question(question = "Q5", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "A")
        )

        val score = QuizScoreCalculator.calculateScore(questions)
        assertEquals(5, score.totalQuestions)
        assertEquals(5, score.correctCount)
        assertEquals(0, score.wrongCount)
        assertEquals(0, score.skippedCount)
        assertEquals(50, score.correctMarks)
        assertEquals(0, score.negativeMarks)
        assertEquals(50, score.finalScore)
        assertEquals(50, score.maxScore)
        assertEquals(100, score.accuracyPercentage)
    }

    @Test
    fun testMixedScoreWithPenalties() {
        val questions = listOf(
            // Correct (+10)
            Question(question = "Q1", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "A"),
            // Correct (+10)
            Question(question = "Q2", options = listOf("A", "B", "C", "D"), correctAnswer = "B", explanation = "E", hint = "H", selectedAnswer = "B"),
            // Correct (+10)
            Question(question = "Q3", options = listOf("A", "B", "C", "D"), correctAnswer = "C", explanation = "E", hint = "H", selectedAnswer = "C"),
            // Wrong (-1)
            Question(question = "Q4", options = listOf("A", "B", "C", "D"), correctAnswer = "D", explanation = "E", hint = "H", selectedAnswer = "B"),
            // Skipped (0)
            Question(question = "Q5", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", isSkipped = true)
        )

        val score = QuizScoreCalculator.calculateScore(questions)
        assertEquals(5, score.totalQuestions)
        assertEquals(3, score.correctCount)
        assertEquals(1, score.wrongCount)
        assertEquals(1, score.skippedCount)
        assertEquals(30, score.correctMarks)
        assertEquals(-1, score.negativeMarks)
        assertEquals(29, score.finalScore)
        assertEquals(50, score.maxScore)
        assertEquals(60, score.accuracyPercentage)
    }

    @Test
    fun testScoreNeverNegative() {
        val questions = listOf(
            Question(question = "Q1", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "B"),
            Question(question = "Q2", options = listOf("A", "B", "C", "D"), correctAnswer = "B", explanation = "E", hint = "H", selectedAnswer = "C")
        )

        val score = QuizScoreCalculator.calculateScore(questions)
        assertEquals(2, score.totalQuestions)
        assertEquals(0, score.correctCount)
        assertEquals(2, score.wrongCount)
        assertEquals(-2, score.negativeMarks)
        assertEquals(0, score.finalScore) // Coerced to 0
        assertEquals(0, score.accuracyPercentage)
    }
}
