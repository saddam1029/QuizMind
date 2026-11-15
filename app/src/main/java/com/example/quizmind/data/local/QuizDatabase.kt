package com.example.quizmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.quizmind.data.local.dao.QuizDao
import com.example.quizmind.data.local.entity.QuestionEntity
import com.example.quizmind.data.local.entity.QuizEntity

@Database(
    entities = [QuizEntity::class, QuestionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun quizDao(): QuizDao
}
