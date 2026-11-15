package com.example.quizmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.quizmind.data.local.entity.HistoryEntity
import com.example.quizmind.data.local.entity.QuestionEntity
import com.example.quizmind.data.local.entity.QuizEntity
import com.example.quizmind.data.local.entity.QuizWithQuestions
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: QuizEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Transaction
    suspend fun insertQuizWithQuestions(quiz: QuizEntity, questions: List<QuestionEntity>) {
        insertQuiz(quiz)
        insertQuestions(questions)
    }

    @Transaction
    @Query("SELECT * FROM downloaded_quizzes ORDER BY downloadedAt DESC")
    fun getAllDownloadedQuizzes(): Flow<List<QuizWithQuestions>>

    @Transaction
    @Query("SELECT * FROM downloaded_quizzes WHERE id = :quizId LIMIT 1")
    suspend fun getDownloadedQuizById(quizId: String): QuizWithQuestions?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_quizzes WHERE id = :quizId)")
    suspend fun isQuizDownloaded(quizId: String): Boolean

    @Query("SELECT id FROM downloaded_quizzes")
    suspend fun getAllDownloadedQuizIds(): List<String>

    @Query("UPDATE downloaded_quizzes SET title = :title, topic = :title WHERE id = :quizId")
    suspend fun renameDownloadedQuiz(quizId: String, title: String)

    @Query("DELETE FROM downloaded_quizzes WHERE id = :quizId")
    suspend fun deleteQuizById(quizId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity)

    @Query("SELECT * FROM history_entries ORDER BY completedAt DESC")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries WHERE title LIKE '%' || :query || '%' OR topic LIKE '%' || :query || '%' ORDER BY completedAt DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries WHERE completionId = :completionId LIMIT 1")
    suspend fun getHistoryById(completionId: String): HistoryEntity?

    @Query("SELECT * FROM history_entries WHERE firestoreSynced = 0 ORDER BY completedAt ASC")
    suspend fun getUnsyncedHistory(): List<HistoryEntity>

    @Query("UPDATE history_entries SET firestoreSynced = 1 WHERE completionId = :completionId")
    suspend fun markHistorySynced(completionId: String)

    @Query("UPDATE history_entries SET title = :title, topic = :title WHERE completionId = :completionId")
    suspend fun renameHistory(completionId: String, title: String)

    @Query("DELETE FROM history_entries WHERE completionId = :completionId")
    suspend fun deleteHistory(completionId: String)

    @Query("UPDATE history_entries SET title = :title, topic = :title WHERE quizId = :quizId")
    suspend fun renameHistoryByQuizId(quizId: String, title: String)
}
