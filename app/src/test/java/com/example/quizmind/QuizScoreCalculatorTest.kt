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
        assertEquals(5, score.correctMarks)
        assertEquals(0, score.negativeMarks)
        assertEquals(5, score.finalScore)
        assertEquals(5, score.maxScore)
        assertEquals(100, score.accuracyPercentage)
    }

    @Test
    fun testScreenshotExampleOneCorrectFourWrong() {
        val questions = listOf(
            Question(question = "Q1", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "A"),
            Question(question = "Q2", options = listOf("A", "B", "C", "D"), correctAnswer = "B", explanation = "E", hint = "H", selectedAnswer = "A"),
            Question(question = "Q3", options = listOf("A", "B", "C", "D"), correctAnswer = "C", explanation = "E", hint = "H", selectedAnswer = "A"),
            Question(question = "Q4", options = listOf("A", "B", "C", "D"), correctAnswer = "D", explanation = "E", hint = "H", selectedAnswer = "A"),
            Question(question = "Q5", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "B")
        )

        val score = QuizScoreCalculator.calculateScore(questions)
        assertEquals(5, score.totalQuestions)
        assertEquals(1, score.correctCount)
        assertEquals(4, score.wrongCount)
        assertEquals(0, score.skippedCount)
        assertEquals(1, score.correctMarks)
        assertEquals(0, score.negativeMarks)
        assertEquals(1, score.finalScore)
        assertEquals(5, score.maxScore)
        assertEquals(20, score.accuracyPercentage)
    }

    @Test
    fun testSkippedNotCountedAsWrong() {
        val questions = listOf(
            Question(question = "Q1", options = listOf("A", "B", "C", "D"), correctAnswer = "A", explanation = "E", hint = "H", selectedAnswer = "A"),
            Question(question = "Q2", options = listOf("A", "B", "C", "D"), correctAnswer = "B", explanation = "E", hint = "H", isSkipped = true),
            Question(question = "Q3", options = listOf("A", "B", "C", "D"), correctAnswer = "C", explanation = "E", hint = "H", selectedAnswer = "A")
        )

        val score = QuizScoreCalculator.calculateScore(questions)
        assertEquals(3, score.totalQuestions)
        assertEquals(1, score.correctCount)
        assertEquals(1, score.wrongCount)
        assertEquals(1, score.skippedCount)
        assertEquals(1, score.correctMarks)
        assertEquals(0, score.negativeMarks)
        assertEquals(1, score.finalScore)
        assertEquals(3, score.maxScore)
        assertEquals(33, score.accuracyPercentage)
    }

    @Test
    fun testAverageTimePerQuestion() {
        assertEquals(12L, QuizScoreCalculator.averageTimeSeconds(60, 5))
        assertEquals(0L, QuizScoreCalculator.averageTimeSeconds(60, 0))
    }
}
