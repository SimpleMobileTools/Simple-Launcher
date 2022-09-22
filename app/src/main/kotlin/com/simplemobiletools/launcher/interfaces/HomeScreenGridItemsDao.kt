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
    fun insert(item: HomeScreenGridItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<HomeScreenGridItem>)

    @Query("UPDATE home_screen_grid_items SET title = :title WHERE package_name = :packageName")
    fun updateAppTitle(title: String, packageName: String)

    @Query("UPDATE home_screen_grid_items SET `left` = :left, `top` = :top, `right` = :right, `bottom` = :bottom WHERE id = :id")
    fun updateAppPosition(left: Int, top: Int, right: Int, bottom: Int, id: Long)

    @Query("DELETE FROM home_screen_grid_items WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM home_screen_grid_items WHERE package_name = :packageName")
    fun deleteByPackageName(packageName: String)
}
