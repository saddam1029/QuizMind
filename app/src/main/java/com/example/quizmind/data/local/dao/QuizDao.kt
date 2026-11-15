package com.example.quizmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM downloaded_quizzes WHERE id = :quizId")
    suspend fun deleteQuizById(quizId: String)
}
