package com.example.quizmind.data.remote

import com.example.quizmind.data.model.QuizResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreQuizDataSource @Inject constructor() {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    suspend fun saveQuizResult(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            val data = hashMapOf(
                "quizId" to result.quizId,
                "userId" to (user?.uid ?: "anonymous"),
                "topic" to result.topic,
                "subject" to result.subject,
                "difficulty" to result.difficulty,
                "totalQuestions" to result.totalQuestions,
                "correctCount" to result.correctCount,
                "wrongCount" to result.wrongCount,
                "skippedCount" to result.skippedCount,
                "correctMarks" to result.correctMarks,
                "negativeMarks" to result.negativeMarks,
                "finalScore" to result.finalScore,
                "maxScore" to result.maxScore,
                "accuracyPercentage" to result.accuracyPercentage,
                "timeTakenSeconds" to result.timeTakenSeconds,
                "formattedTime" to result.formattedTime,
                "timestamp" to result.timestamp,
                "questions" to result.questions.map { q ->
                    hashMapOf(
                        "id" to q.id,
                        "question" to q.question,
                        "options" to q.options,
                        "correctAnswer" to q.correctAnswer,
                        "explanation" to q.explanation,
                        "hint" to q.hint,
                        "selectedAnswer" to q.selectedAnswer,
                        "isSkipped" to q.isSkipped
                    )
                }
            )

            if (user != null) {
                firestore.collection("users")
                    .document(user.uid)
                    .collection("quizzes")
                    .document(result.quizId)
                    .set(data, SetOptions.merge())
                    .await()
            }

            // Also keep a reference in root quizzes collection for global analytics
            firestore.collection("quizzes")
                .document(result.quizId)
                .set(data, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            // Online save error should not crash the app
            Result.failure(e)
        }
    }
}
