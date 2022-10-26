package com.simplemobiletools.launcher.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.launcher.helpers.Converters
import com.simplemobiletools.launcher.interfaces.AppLaunchersDao
import com.simplemobiletools.launcher.interfaces.HiddenIconsDao
import com.simplemobiletools.launcher.interfaces.HomeScreenGridItemsDao
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HiddenIcon
import com.simplemobiletools.launcher.models.HomeScreenGridItem

@Database(entities = [AppLauncher::class, HomeScreenGridItem::class, HiddenIcon::class], version = 4)
@TypeConverters(Converters::class)
abstract class AppsDatabase : RoomDatabase() {

    abstract fun AppLaunchersDao(): AppLaunchersDao

    abstract fun HomeScreenGridItemsDao(): HomeScreenGridItemsDao

    abstract fun HiddenIconsDao(): HiddenIconsDao

    companion object {
        private var db: AppsDatabase? = null

        fun getInstance(context: Context): AppsDatabase {
            if (db == null) {
                synchronized(AppsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppsDatabase::class.java, "apps.db")
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .build()
                    }
                }
            }
            return db!!
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN intent TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN shortcut_id TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN icon BLOB")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE apps ADD COLUMN activity_name TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE home_screen_grid_items ADD COLUMN activity_name TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `hidden_icons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `package_name` TEXT NOT NULL, `activity_name` TEXT NOT NULL, `title` TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX `index_hidden_icons_id` ON `hidden_icons` (`id`)")
            }
        }
    }
}
