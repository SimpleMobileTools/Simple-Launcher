package com.simplemobiletools.launcher.extensions

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import com.simplemobiletools.commons.extensions.portrait
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.databases.AppsDatabase
import com.simplemobiletools.launcher.helpers.Config
import com.simplemobiletools.launcher.interfaces.AppLaunchersDao
import com.simplemobiletools.launcher.interfaces.HomeScreenGridItemsDao

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.launchersDB: AppLaunchersDao get() = AppsDatabase.getInstance(applicationContext).AppLaunchersDao()

val Context.homeScreenGridItemsDB: HomeScreenGridItemsDao get() = AppsDatabase.getInstance(applicationContext).HomeScreenGridItemsDao()

fun Context.getColumnCount(): Int {
    return if (portrait) {
        resources.getInteger(R.integer.portrait_column_count)
    } else {
        resources.getInteger(R.integer.landscape_column_count)
    }
}

fun Context.getDrawableForPackageName(packageName: String): Drawable? {
    var drawable: Drawable? = null
    try {
        // try getting the properly colored launcher icons
        val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityList = launcher.getActivityList(packageName, Process.myUserHandle())[0]
        drawable = activityList.getBadgedIcon(0)
    } catch (e: Exception) {
    } catch (e: Error) {
    }

    if (drawable == null) {
        drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (ignored: Exception) {
            null
        }
    }

    return drawable
}

fun Context.getTileCount(size: Int): Int {
    val tiles = Math.ceil(((size / resources.displayMetrics.density) - 30) / 70.0).toInt()
    return Math.max(tiles, 1)
}
