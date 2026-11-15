package com.example.quizmind.data.session

import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the in-progress quiz and its result in memory.
 * Screens pass only an id, so a large quiz never goes through an Intent.
 */
@Singleton
class QuizSessionStore @Inject constructor() {

    private val quizzes = object : LinkedHashMap<String, Quiz>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Quiz>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private val results = object : LinkedHashMap<String, QuizResult>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuizResult>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun putQuiz(quiz: Quiz) {
        quizzes[quiz.id] = quiz
    }

    @Synchronized
    fun getQuiz(quizId: String): Quiz? = quizzes[quizId]

    @Synchronized
    fun putResult(result: QuizResult) {
        results[result.quizId] = result
    }

    @Synchronized
    fun getResult(quizId: String): QuizResult? = results[quizId]

    private companion object {
        const val MAX_ENTRIES = 4
    }
}
