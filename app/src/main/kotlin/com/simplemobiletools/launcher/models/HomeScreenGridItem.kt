package com.simplemobiletools.launcher.models

import android.graphics.drawable.Drawable
import androidx.room.*

// grid coords are from 0-5 by default. Icons occupy 1 slot only, widgets can be bigger
@Entity(tableName = "home_screen_grid_items", indices = [(Index(value = ["id"], unique = true))])
data class HomeScreenGridItem(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "left") var left: Int,
    @ColumnInfo(name = "top") var top: Int,
    @ColumnInfo(name = "right") var right: Int,
    @ColumnInfo(name = "bottom") var bottom: Int,
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "title") var title: String,

    @Ignore var drawable: Drawable?
) {
    constructor() : this(null, -1, -1, -1, -1, "", "", null)
}
