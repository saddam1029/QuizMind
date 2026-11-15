package com.example.quizmind.data.repository

import com.example.quizmind.data.local.dao.QuizDao
import com.example.quizmind.data.local.entity.QuestionEntity
import com.example.quizmind.data.local.entity.QuizEntity
import com.example.quizmind.data.local.entity.QuizWithQuestions
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.remote.FirestoreQuizDataSource
import com.example.quizmind.data.remote.GeminiQuizService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepository @Inject constructor(
    private val geminiQuizService: GeminiQuizService,
    private val firestoreDataSource: FirestoreQuizDataSource,
    private val quizDao: QuizDao
) {

    suspend fun generateQuiz(
        topic: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ): Result<Quiz> {
        return geminiQuizService.generateQuiz(
            topic = topic,
            subject = subject,
            questionCount = questionCount,
            difficulty = difficulty,
            timerEnabled = timerEnabled
        )
    }

    suspend fun saveQuizResultToFirestore(result: QuizResult): Result<Unit> {
        return firestoreDataSource.saveQuizResult(result)
    }

    suspend fun downloadQuizToRoom(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (quizDao.isQuizDownloaded(result.quizId)) {
                return@withContext Result.failure(IllegalStateException("Quiz already downloaded."))
            }

            val quizEntity = QuizEntity(
                id = result.quizId,
                topic = result.topic,
                subject = result.subject,
                difficulty = result.difficulty,
                totalQuestions = result.totalQuestions,
                correctCount = result.correctCount,
                wrongCount = result.wrongCount,
                skippedCount = result.skippedCount,
                correctMarks = result.correctMarks,
                negativeMarks = result.negativeMarks,
                finalScore = result.finalScore,
                maxScore = result.maxScore,
                accuracyPercentage = result.accuracyPercentage,
                timeTakenSeconds = result.timeTakenSeconds,
                downloadedAt = System.currentTimeMillis()
            )

            val questionEntities = result.questions.mapIndexed { index, q ->
                QuestionEntity(
                    id = q.id,
                    quizId = result.quizId,
                    questionIndex = index,
                    question = q.question,
                    optionA = q.options.getOrElse(0) { "" },
                    optionB = q.options.getOrElse(1) { "" },
                    optionC = q.options.getOrElse(2) { "" },
                    optionD = q.options.getOrElse(3) { "" },
                    correctAnswer = q.correctAnswer,
                    explanation = q.explanation,
                    hint = q.hint,
                    selectedAnswer = q.selectedAnswer,
                    isSkipped = q.isSkipped
                )
            }

            quizDao.insertQuizWithQuestions(quizEntity, questionEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isQuizDownloaded(quizId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            quizDao.isQuizDownloaded(quizId)
        } catch (_: Exception) {
            false
        }
    }

    fun getAllDownloadedQuizzes(): Flow<List<QuizWithQuestions>> {
        return quizDao.getAllDownloadedQuizzes()
    }
}
