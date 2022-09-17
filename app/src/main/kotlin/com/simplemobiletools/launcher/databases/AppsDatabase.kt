package com.simplemobiletools.launcher.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simplemobiletools.launcher.models.AppLauncher

@Database(entities = [AppLauncher::class], version = 1)
abstract class AppsDatabase : RoomDatabase() {

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
