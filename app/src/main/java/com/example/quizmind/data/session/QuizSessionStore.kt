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

    private val quizSources = object : LinkedHashMap<String, String>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private val results = object : LinkedHashMap<String, QuizResult>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QuizResult>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun putQuiz(quiz: Quiz, source: String = QuizResult.SOURCE_ONLINE) {
        quizzes[quiz.id] = quiz
        quizSources[quiz.id] = source
    }

    @Synchronized
    fun getQuiz(quizId: String): Quiz? = quizzes[quizId]

    @Synchronized
    fun getQuizSource(quizId: String): String? = quizSources[quizId]

    @Synchronized
    fun putResult(result: QuizResult) {
        results[result.quizId] = result
        results[result.completionId] = result
    }

    @Synchronized
    fun getResult(id: String): QuizResult? = results[id]

    private companion object {
        const val MAX_ENTRIES = 6
    }
}
