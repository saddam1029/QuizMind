package com.example.quizmind.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.quizmind.data.credits.CreditPolicy
import com.example.quizmind.data.local.dao.QuizDao
import com.example.quizmind.data.local.entity.PendingDeleteEntity
import com.example.quizmind.data.local.entity.QuizWithQuestions
import com.example.quizmind.data.mapper.toDownloadQuestionEntities
import com.example.quizmind.data.mapper.toDownloadQuizEntity
import com.example.quizmind.data.mapper.toHistoryEntity
import com.example.quizmind.data.mapper.toHistoryItem
import com.example.quizmind.data.mapper.toPlayableQuiz
import com.example.quizmind.data.mapper.toQuestionList
import com.example.quizmind.data.mapper.toQuizResult
import com.example.quizmind.data.model.GeneratedQuiz
import com.example.quizmind.data.model.HistoryItem
import com.example.quizmind.data.model.Quiz
import com.example.quizmind.data.model.QuizResult
import com.example.quizmind.data.model.QuizSourceType
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

    /**
     * Online SOURCE_ONLINE → Firestore only.
     * If Firestore fails, keep a pending Room history row (not a download).
     * SOURCE_DOWNLOADED or offline → Room first, then sync to Firestore when possible.
     */
    suspend fun saveQuizResult(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        val online = isOnline()
        val normalized = result.copy(sourceType = QuizSourceType.normalize(result.sourceType))
        val isDownloadedAttempt = normalized.source == QuizResult.SOURCE_DOWNLOADED

        if (isDownloadedAttempt || !online) {
            quizDao.insertHistory(
                normalized.toHistoryEntity(
                    synced = false,
                    persistQuestions = !quizDao.isQuizDownloaded(normalized.quizId)
                )
            )
            if (online) {
                val sync = firestoreDataSource.saveQuizResult(normalized)
                if (sync.isSuccess) {
                    quizDao.markHistorySynced(normalized.completionId)
                }
                return@withContext sync
            }
            return@withContext Result.success(Unit)
        }

        val remote = firestoreDataSource.saveQuizResult(normalized)
        if (remote.isFailure) {
            quizDao.insertHistory(
                normalized.toHistoryEntity(synced = false, persistQuestions = true)
            )
        }
        remote
    }

    suspend fun syncPendingHistory(): Result<Int> = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext Result.success(0)

        var synced = 0

        quizDao.getPendingDeletes().forEach { pending ->
            val deleted = firestoreDataSource.deleteHistory(pending.completionId)
            if (deleted.isSuccess) {
                quizDao.removePendingDelete(pending.completionId)
                synced++
            }
        }

        quizDao.getUnsyncedHistory().forEach { entry ->
            val questions = resolveQuestionsForSync(entry.quizId, entry.questionsJson)
            val result = entry.toQuizResult(questions)
            val save = firestoreDataSource.saveQuizResult(result)
            if (save.isSuccess) {
                quizDao.markHistorySynced(entry.completionId)
                synced++
            }
        }
        Result.success(synced)
    }

    private suspend fun resolveQuestionsForSync(quizId: String, questionsJson: String?) =
        questionsJson.toQuestionList().ifEmpty {
            quizDao.getDownloadedQuizById(quizId)?.toPlayableQuiz()?.questions.orEmpty()
        }

    /**
     * Room-only completed history (downloaded attempts + pending online saves).
     */
    fun observeLocalHistory(query: String = ""): Flow<List<HistoryItem>> {
        val localFlow = if (query.isBlank()) {
            quizDao.observeHistory()
        } else {
            quizDao.searchHistory(query.trim())
        }
        return combine(localFlow, quizDao.getAllDownloadedQuizzes()) { localHistory, downloads ->
            val downloadedIds = downloads.map { it.quiz.id }.toSet()
            localHistory.map { it.toHistoryItem(isDownloaded = it.quizId in downloadedIds) }
        }
    }

    suspend fun getLocalHistorySnapshot(query: String = ""): List<HistoryItem> =
        withContext(Dispatchers.IO) {
            val local = if (query.isBlank()) {
                quizDao.getAllHistory()
            } else {
                quizDao.searchHistoryOnce(query.trim())
            }
            val downloadedIds = quizDao.getAllDownloadedQuizIds().toSet()
            local.map { it.toHistoryItem(isDownloaded = it.quizId in downloadedIds) }
        }

    /**
     * Online: Firestore history ∪ unsynced Room rows (deduped by completionId).
     * Offline: Room only.
     */
    suspend fun loadMergedHistory(query: String = ""): Result<List<HistoryItem>> =
        withContext(Dispatchers.IO) {
            syncPendingHistory()
            val downloadedIds = quizDao.getAllDownloadedQuizIds().toSet()
            val q = query.trim()

            if (!isOnline()) {
                val local = getLocalHistorySnapshot(q)
                return@withContext Result.success(local)
            }

            val remote = firestoreDataSource.fetchHistory().getOrElse { error ->
                val localFallback = getLocalHistorySnapshot(q)
                return@withContext if (localFallback.isNotEmpty()) {
                    Result.success(localFallback)
                } else {
                    Result.failure(error)
                }
            }

            val pendingLocal = quizDao.getUnsyncedHistory()
                .map { it.toHistoryItem(isDownloaded = it.quizId in downloadedIds) }

            val merged = LinkedHashMap<String, HistoryItem>()
            remote.forEach { item ->
                merged[item.completionId] = item.copy(isDownloaded = item.quizId in downloadedIds)
            }
            // Unsynced local wins so offline renames/completions are not overwritten.
            pendingLocal.forEach { item ->
                merged[item.completionId] = item
            }

            val items = merged.values
                .sortedByDescending { it.completedAt }
                .filter {
                    q.isBlank() ||
                        it.title.contains(q, ignoreCase = true) ||
                        it.topic.contains(q, ignoreCase = true)
                }
            Result.success(items)
        }

    /** @deprecated Prefer [loadMergedHistory]. Kept for callers that expect the old name. */
    suspend fun loadOnlineHistory(query: String = ""): Result<List<HistoryItem>> =
        loadMergedHistory(query)

    fun observeHistory(query: String = ""): Flow<List<HistoryItem>> = observeLocalHistory(query)

    suspend fun downloadQuizToRoom(result: QuizResult): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (result.questions.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("This quiz has no questions to download.")
                )
            }
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

    suspend fun renameHistory(item: HistoryItem, newTitle: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val title = newTitle.trim()
            if (title.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Title cannot be empty."))
            }

            val local = quizDao.getHistoryById(item.completionId)
            if (local != null) {
                quizDao.renameHistory(item.completionId, title)
                quizDao.renameHistoryByQuizId(item.quizId, title)
            }

            if (quizDao.isQuizDownloaded(item.quizId)) {
                quizDao.renameDownloadedQuiz(item.quizId, title)
            }

            if (isOnline()) {
                val remote = firestoreDataSource.renameHistory(item.completionId, title)
                if (remote.isFailure && local != null) {
                    quizDao.markHistoryUnsynced(item.completionId)
                }
                remote
            } else {
                if (local != null) {
                    quizDao.markHistoryUnsynced(item.completionId)
                } else {
                    // Online-only item renamed while offline: keep a pending local row.
                    quizDao.insertHistory(
                        item.copy(title = title, topic = title, firestoreSynced = false)
                            .toLocalPendingEntity(title)
                    )
                }
                Result.success(Unit)
            }
        }

    private fun HistoryItem.toLocalPendingEntity(title: String) =
        com.example.quizmind.data.local.entity.HistoryEntity(
            completionId = completionId,
            quizId = quizId,
            title = title,
            topic = title,
            subject = subject,
            difficulty = difficulty,
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            wrongCount = wrongCount,
            skippedCount = skippedCount,
            correctMarks = correctMarks,
            negativeMarks = negativeMarks,
            finalScore = finalScore,
            maxScore = maxScore,
            accuracyPercentage = accuracyPercentage,
            timeTakenSeconds = timeTakenSeconds,
            completedAt = completedAt,
            source = source,
            sourceType = QuizSourceType.normalize(sourceType),
            firestoreSynced = false,
            questionsJson = null
        )

    /**
     * Deletes a completion history record from Room and Firestore.
     * Downloaded quiz packages remain available for offline replay.
     */
    suspend fun deleteHistory(item: HistoryItem): Result<Unit> = withContext(Dispatchers.IO) {
        quizDao.deleteHistory(item.completionId)
        if (isOnline()) {
            val remote = firestoreDataSource.deleteHistory(item.completionId)
            quizDao.removePendingDelete(item.completionId)
            remote
        } else {
            quizDao.insertPendingDelete(PendingDeleteEntity(item.completionId))
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
