package com.simplemobiletools.launcher.interfaces

import androidx.room.*
import com.simplemobiletools.launcher.models.HomeScreenPage
import com.simplemobiletools.launcher.models.PageWithGridItems

@Dao
interface HomeScreenPagesDao {
    @Query("SELECT * FROM home_screen_pages")
    fun getAllPages(): List<HomeScreenPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: HomeScreenPage): Long

    @Transaction
    @Query("SELECT * FROM home_screen_pages")
    fun getPagesWithGridItems(): List<PageWithGridItems>
}
