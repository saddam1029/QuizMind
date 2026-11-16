package com.example.quizmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_history_deletes")
data class PendingDeleteEntity(
    @PrimaryKey
    val completionId: String,
    val createdAt: Long = System.currentTimeMillis()
)
