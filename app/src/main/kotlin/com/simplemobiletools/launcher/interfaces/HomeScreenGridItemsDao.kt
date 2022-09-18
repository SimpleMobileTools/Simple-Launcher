package com.simplemobiletools.launcher.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.launcher.models.HomeScreenGridItem

@Dao
interface HomeScreenGridItemsDao {
    @Query("SELECT * FROM home_screen_grid_items")
    fun getAllItems(): List<HomeScreenGridItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<HomeScreenGridItem>)
}
