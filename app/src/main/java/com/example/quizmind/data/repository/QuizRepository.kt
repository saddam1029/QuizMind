package com.example.quizmind.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.quizmind.data.credits.CreditPolicy
import com.example.quizmind.data.local.dao.QuizDao
import com.example.quizmind.data.local.entity.QuizWithQuestions
import com.example.quizmind.data.mapper.toDownloadQuestionEntities
import com.example.quizmind.data.mapper.toDownloadQuizEntity
import com.example.quizmind.data.mapper.toHistoryEntity
import com.example.quizmind.data.mapper.toHistoryItem
import com.example.quizmind.data.mapper.toPlayableQuiz
import com.example.quizmind.data.model.GeneratedQuiz
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.remote.CreditDeduction
import com.example.quizmind.data.remote.FirestoreCreditsDataSource
import com.example.quizmind.data.remote.FirestoreQuizDataSource
import com.example.quizmind.data.remote.GeminiQuizService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepository @Inject constructor(
    private val geminiQuizService: GeminiQuizService,
    private val firestoreDataSource: FirestoreQuizDataSource,
    private val creditsDataSource: FirestoreCreditsDataSource,
    private val quizDao: QuizDao,
    @ApplicationContext private val context: Context
) {

    private val creditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeCredits(): Flow<Long> = creditsDataSource.observeCredits()

    suspend fun generateQuiz(
        title: String,
        sourceMaterial: String,
        subject: String,
        questionCount: Int,
        difficulty: String,
        timerEnabled: Boolean
    ): Result<Pair<GeneratedQuiz, CreditDeduction>> = withContext(Dispatchers.IO) {
        // Cache-only soft gate — never block generate on a Firestore server read.
        val estimated = CreditPolicy.fallbackCost(questionCount)
        val available = creditsDataSource.getCachedCredits()
        if (available < estimated) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Insufficient credits. You need $estimated credits but have only $available."
                )
            )
        }

        val generated = geminiQuizService.generateQuiz(
            title = title,
            sourceMaterial = sourceMaterial,
            subject = subject,
            questionCount = questionCount,
            difficulty = difficulty,
            timerEnabled = timerEnabled
        ).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val cost = CreditPolicy.resolveCost(generated.usageTokenCount, questionCount)
        // Return quiz immediately; ledger write happens in the background.
        val optimistic = CreditDeduction(
            remainingCredits = (available - cost).coerceAtLeast(0L),
            deducted = cost
        )
        if (cost > 0L) {
            creditScope.launch {
                creditsDataSource.deductCredits(
                    amount = cost,
                    quizId = generated.quiz.id,
                    reason = "quiz_generation"
                )
            }
        }

        Result.success(generated to optimistic)
    }

    suspend fun warmUpAi() {
        geminiQuizService.warmUp()
    }

    suspend fun saveQuizResult(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        val online = isOnline()
        if (result.source == QuizResult.SOURCE_DOWNLOADED || !online) {
            quizDao.insertHistory(result.toHistoryEntity(synced = false))
            if (online) {
                val sync = firestoreDataSource.saveQuizResult(result)
                if (sync.isSuccess) {
                    quizDao.markHistorySynced(result.completionId)
                }
                return@withContext sync
            }
            return@withContext Result.success(Unit)
        }

        val remote = firestoreDataSource.saveQuizResult(result)
        quizDao.insertHistory(result.toHistoryEntity(synced = remote.isSuccess))
        remote
    }

    suspend fun syncPendingHistory(): Result<Int> = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext Result.success(0)
        val pending = quizDao.getUnsyncedHistory()
        var synced = 0
        pending.forEach { entry ->
            val result = QuizResult(
                quizId = entry.quizId,
                topic = entry.title,
                subject = entry.subject,
                difficulty = entry.difficulty,
                totalQuestions = entry.totalQuestions,
                correctCount = entry.correctCount,
                wrongCount = entry.wrongCount,
                skippedCount = entry.skippedCount,
                correctMarks = entry.correctMarks,
                negativeMarks = entry.negativeMarks,
                finalScore = entry.finalScore,
                maxScore = entry.maxScore,
                accuracyPercentage = entry.accuracyPercentage,
                timeTakenSeconds = entry.timeTakenSeconds,
                questions = emptyList(),
                timestamp = entry.completedAt,
                completionId = entry.completionId,
                source = entry.source
            )
            val save = firestoreDataSource.saveQuizResult(result)
            if (save.isSuccess) {
                quizDao.markHistorySynced(entry.completionId)
                synced++
            }
        }
        Result.success(synced)
    }

    fun observeHistory(query: String = ""): Flow<List<HistoryItem>> {
        val localFlow = if (query.isBlank()) {
            quizDao.observeHistory()
        } else {
            quizDao.searchHistory(query.trim())
        }
        return combine(
            localFlow,
            quizDao.getAllDownloadedQuizzes()
        ) { localHistory, downloads ->
            val downloadedIds = downloads.map { it.quiz.id }.toSet()
            localHistory.map { it.toHistoryItem(isDownloaded = it.quizId in downloadedIds) }
        }
    }

    suspend fun loadOnlineHistory(query: String = ""): Result<List<HistoryItem>> = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext Result.failure(IllegalStateException("No internet connection."))
        }
        syncPendingHistory()
        val remote = firestoreDataSource.fetchHistory()
        val downloadedIds = quizDao.getAllDownloadedQuizIds().toSet()
        remote.map { items ->
            items.forEach { item ->
                quizDao.insertHistory(
                    com.example.quizmind.data.local.entity.HistoryEntity(
                        completionId = item.completionId,
                        quizId = item.quizId,
                        title = item.title,
                        topic = item.topic,
                        subject = item.subject,
                        difficulty = item.difficulty,
                        totalQuestions = item.totalQuestions,
                        correctCount = item.correctCount,
                        wrongCount = item.wrongCount,
                        skippedCount = item.skippedCount,
                        correctMarks = item.correctMarks,
                        negativeMarks = item.negativeMarks,
                        finalScore = item.finalScore,
                        maxScore = item.maxScore,
                        accuracyPercentage = item.accuracyPercentage,
                        timeTakenSeconds = item.timeTakenSeconds,
                        completedAt = item.completedAt,
                        source = item.source,
                        firestoreSynced = true
                    )
                )
            }
            val filtered = if (query.isBlank()) {
                items
            } else {
                items.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.topic.contains(query, ignoreCase = true)
                }
            }
            filtered.map { item ->
                item.copy(isDownloaded = item.quizId in downloadedIds)
            }
        }
    }

    suspend fun downloadQuizToRoom(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (quizDao.isQuizDownloaded(result.quizId)) {
                return@withContext Result.failure(IllegalStateException("Quiz already downloaded."))
            }
            quizDao.insertQuizWithQuestions(
                result.toDownloadQuizEntity(),
                result.toDownloadQuestionEntities()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadHistoryItem(completionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val result = firestoreDataSource.fetchResultForDownload(completionId)
        result.fold(
            onSuccess = { downloadQuizToRoom(it) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun isQuizDownloaded(quizId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            quizDao.isQuizDownloaded(quizId)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getPlayableDownloadedQuiz(quizId: String): Quiz? = withContext(Dispatchers.IO) {
        quizDao.getDownloadedQuizById(quizId)?.toPlayableQuiz()
    }

    fun getAllDownloadedQuizzes(): Flow<List<QuizWithQuestions>> {
        return quizDao.getAllDownloadedQuizzes()
    }

    suspend fun renameHistory(item: HistoryItem, newTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Title cannot be empty."))
        }
        quizDao.renameHistory(item.completionId, title)
        quizDao.renameHistoryByQuizId(item.quizId, title)
        if (quizDao.isQuizDownloaded(item.quizId)) {
            quizDao.renameDownloadedQuiz(item.quizId, title)
        }
        if (isOnline()) {
            firestoreDataSource.renameHistory(item.completionId, title)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteHistory(item: HistoryItem): Result<Unit> = withContext(Dispatchers.IO) {
        quizDao.deleteHistory(item.completionId)
        if (isOnline()) {
            firestoreDataSource.deleteHistory(item.completionId)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteDownloadedQuiz(quizId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            quizDao.deleteQuizById(quizId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
