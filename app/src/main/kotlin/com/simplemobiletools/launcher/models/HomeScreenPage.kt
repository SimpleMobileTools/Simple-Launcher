package com.simplemobiletools.launcher.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "home_screen_pages", indices = [(Index(value = ["id"], unique = true))])
data class HomeScreenPage(
    @PrimaryKey(autoGenerate = true) var id: Long? = null,
    val position: Int = 0
)
