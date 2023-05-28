package com.simplemobiletools.launcher.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.launcher.models.AppLauncher
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLaunchersDao {
    @Query("SELECT * FROM apps")
    fun getAppLaunchers(): Flow<List<AppLauncher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(appLaunchers: List<AppLauncher>)

    @Query("DELETE FROM apps WHERE package_name = :packageName")
    fun deleteApp(packageName: String)

    @Query("DELETE FROM apps WHERE id = :id")
    fun deleteById(id: Long)
}
