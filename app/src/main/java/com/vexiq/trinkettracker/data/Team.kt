package com.vexiq.trinkettracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class Team(
    @PrimaryKey
    val teamNumber: String,
    val teamName: String,
    val isCollected: Boolean = false,
    val photoPath: String? = null,
    val collectedAt: Long? = null
)
