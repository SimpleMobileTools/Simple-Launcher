package com.simplemobiletools.launcher.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simplemobiletools.launcher.interfaces.AppLaunchersDao
import com.simplemobiletools.launcher.interfaces.HomeScreenGridItemsDao
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HomeScreenGridItem

@Database(entities = [AppLauncher::class, HomeScreenGridItem::class], version = 1)
abstract class AppsDatabase : RoomDatabase() {

    abstract fun AppLaunchersDao(): AppLaunchersDao

    abstract fun HomeScreenGridItemsDao(): HomeScreenGridItemsDao

    companion object {
        private var db: AppsDatabase? = null

        fun getInstance(context: Context): AppsDatabase {
            if (db == null) {
                synchronized(AppsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppsDatabase::class.java, "apps.db")
                            .build()
                    }
                }
            }
            return db!!
        }
    }
}
