package com.example.quizmind.di

import android.content.Context
import androidx.room.Room
import com.example.quizmind.data.local.QuizDatabase
import com.example.quizmind.data.local.dao.QuizDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideQuizDatabase(
        @ApplicationContext context: Context
    ): QuizDatabase {
        return Room.databaseBuilder(
            context,
            QuizDatabase::class.java,
            "quizmind_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideQuizDao(database: QuizDatabase): QuizDao {
        return database.quizDao()
    }
}
