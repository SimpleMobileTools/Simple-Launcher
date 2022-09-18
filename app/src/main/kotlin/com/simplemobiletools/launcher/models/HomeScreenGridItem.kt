package com.simplemobiletools.launcher.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// grid coords are from 0-5 by default. Icons occupy 1 slot only, widgets can be bigger
@Entity(tableName = "home_screen_grid_items", indices = [(Index(value = ["id"], unique = true))])
data class HomeScreenGridItem(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "left") var left: Int,
    @ColumnInfo(name = "top") val top: Int,
    @ColumnInfo(name = "right") val right: Int,
    @ColumnInfo(name = "bottom") val bottom: Int,
    @ColumnInfo(name = "package_name") val packageName: String
)
