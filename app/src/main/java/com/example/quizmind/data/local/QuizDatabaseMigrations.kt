package com.example.quizmind.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object QuizDatabaseMigrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE history_entries ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'topic'"
            )
            db.execSQL("ALTER TABLE history_entries ADD COLUMN questionsJson TEXT")
            db.execSQL(
                "ALTER TABLE downloaded_quizzes ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'topic'"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_history_deletes (
                    completionId TEXT NOT NULL PRIMARY KEY,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_history_entries_firestoreSynced ON history_entries(firestoreSynced)"
            )
        }
    }
}
