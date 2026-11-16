package com.example.quizmind.data.remote

import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.Question
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.model.QuizSourceType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    private fun historyCollection(uid: String) =
        firestore.collection("users").document(uid).collection("history")

    suspend fun saveQuizResult(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User not signed in."))

            val data = historyMap(result, user.uid)
            historyCollection(user.uid)
                .document(result.completionId)
                .set(data, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchHistory(limit: Long = 100): Result<List<HistoryItem>> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.success(emptyList())

            val snapshot = historyCollection(user.uid)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val items = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                HistoryItem(
                    completionId = doc.id,
                    quizId = data["quizId"] as? String ?: doc.id,
                    title = data["title"] as? String
                        ?: data["topic"] as? String
                        ?: "Quiz",
                    topic = data["topic"] as? String ?: "",
                    subject = data["subject"] as? String ?: "",
                    difficulty = data["difficulty"] as? String ?: "",
                    totalQuestions = (data["totalQuestions"] as? Number)?.toInt() ?: 0,
                    correctCount = (data["correctCount"] as? Number)?.toInt() ?: 0,
                    wrongCount = (data["wrongCount"] as? Number)?.toInt() ?: 0,
                    skippedCount = (data["skippedCount"] as? Number)?.toInt() ?: 0,
                    correctMarks = (data["correctMarks"] as? Number)?.toInt() ?: 0,
                    negativeMarks = (data["negativeMarks"] as? Number)?.toInt() ?: 0,
                    finalScore = (data["finalScore"] as? Number)?.toInt() ?: 0,
                    maxScore = (data["maxScore"] as? Number)?.toInt() ?: 0,
                    accuracyPercentage = (data["accuracyPercentage"] as? Number)?.toInt() ?: 0,
                    timeTakenSeconds = (data["timeTakenSeconds"] as? Number)?.toLong() ?: 0L,
                    completedAt = (data["completedAt"] as? Number)?.toLong()
                        ?: (data["timestamp"] as? Number)?.toLong()
                        ?: 0L,
                    source = data["source"] as? String ?: QuizResult.SOURCE_ONLINE,
                    sourceType = QuizSourceType.normalize(data["sourceType"] as? String),
                    firestoreSynced = true
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameHistory(completionId: String, title: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User not signed in."))
            historyCollection(user.uid)
                .document(completionId)
                .set(
                    mapOf(
                        "title" to title,
                        "topic" to title
                    ),
                    SetOptions.merge()
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHistory(completionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User not signed in."))
            historyCollection(user.uid)
                .document(completionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchResultForDownload(completionId: String): Result<QuizResult> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("User not signed in."))
            val doc = historyCollection(user.uid).document(completionId).get().await()
            if (!doc.exists()) {
                return@withContext Result.failure(IllegalStateException("History item not found."))
            }
            val data = doc.data
                ?: return@withContext Result.failure(IllegalStateException("History item is empty."))

            @Suppress("UNCHECKED_CAST")
            val questionMaps = data["questions"] as? List<Map<String, Any?>> ?: emptyList()
            if (questionMaps.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("This history item has no questions to download.")
                )
            }

            val questions = questionMaps.map { q ->
                val options = (q["options"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                Question(
                    id = q["id"]?.toString().orEmpty(),
                    question = q["question"]?.toString().orEmpty(),
                    options = options,
                    correctAnswer = q["correctAnswer"]?.toString().orEmpty(),
                    explanation = q["explanation"]?.toString().orEmpty(),
                    hint = q["hint"]?.toString().orEmpty(),
                    selectedAnswer = q["selectedAnswer"]?.toString(),
                    isSkipped = q["isSkipped"] as? Boolean ?: false
                )
            }

            Result.success(
                QuizResult(
                    quizId = data["quizId"] as? String ?: completionId,
                    topic = data["title"] as? String ?: data["topic"] as? String ?: "Quiz",
                    subject = data["subject"] as? String ?: "",
                    difficulty = data["difficulty"] as? String ?: "",
                    totalQuestions = (data["totalQuestions"] as? Number)?.toInt() ?: questions.size,
                    correctCount = (data["correctCount"] as? Number)?.toInt() ?: 0,
                    wrongCount = (data["wrongCount"] as? Number)?.toInt() ?: 0,
                    skippedCount = (data["skippedCount"] as? Number)?.toInt() ?: 0,
                    correctMarks = (data["correctMarks"] as? Number)?.toInt() ?: 0,
                    negativeMarks = (data["negativeMarks"] as? Number)?.toInt() ?: 0,
                    finalScore = (data["finalScore"] as? Number)?.toInt() ?: 0,
                    maxScore = (data["maxScore"] as? Number)?.toInt() ?: questions.size,
                    accuracyPercentage = (data["accuracyPercentage"] as? Number)?.toInt() ?: 0,
                    timeTakenSeconds = (data["timeTakenSeconds"] as? Number)?.toLong() ?: 0L,
                    questions = questions,
                    timestamp = (data["completedAt"] as? Number)?.toLong()
                        ?: (data["timestamp"] as? Number)?.toLong()
                        ?: System.currentTimeMillis(),
                    completionId = completionId,
                    source = data["source"] as? String ?: QuizResult.SOURCE_ONLINE,
                    timerEnabled = data["timerEnabled"] as? Boolean ?: false,
                    sourceType = QuizSourceType.normalize(data["sourceType"] as? String)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun historyMap(result: QuizResult, userId: String): HashMap<String, Any?> {
        return hashMapOf(
            "completionId" to result.completionId,
            "quizId" to result.quizId,
            "userId" to userId,
            "title" to result.topic,
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
            "averageTimeSeconds" to result.averageTimeSeconds,
            "completedAt" to result.timestamp,
            "timestamp" to result.timestamp,
            "source" to result.source,
            "sourceType" to QuizSourceType.normalize(result.sourceType),
            "timerEnabled" to result.timerEnabled,
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
    }
}
