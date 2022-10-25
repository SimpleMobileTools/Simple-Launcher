package com.simplemobiletools.launcher.extensions

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Size
import com.simplemobiletools.commons.extensions.portrait
import com.simplemobiletools.commons.helpers.isSPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.databases.AppsDatabase
import com.simplemobiletools.launcher.helpers.Config
import com.simplemobiletools.launcher.interfaces.AppLaunchersDao
import com.simplemobiletools.launcher.interfaces.HiddenIconDao
import com.simplemobiletools.launcher.interfaces.HomeScreenGridItemsDao

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.launchersDB: AppLaunchersDao get() = AppsDatabase.getInstance(applicationContext).AppLaunchersDao()

val Context.homeScreenGridItemsDB: HomeScreenGridItemsDao get() = AppsDatabase.getInstance(applicationContext).HomeScreenGridItemsDao()

val Context.hiddenIconsDB: HiddenIconDao get() = AppsDatabase.getInstance(applicationContext).HiddenIconsDao()

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

fun Context.getInitialCellSize(info: AppWidgetProviderInfo, fallbackWidth: Int, fallbackHeight: Int): Size {
    return if (isSPlus() && info.targetCellWidth != 0 && info.targetCellHeight != 0) {
        Size(info.targetCellWidth, info.targetCellHeight)
    } else {
        val widthCells = getCellCount(fallbackWidth)
        val heightCells = getCellCount(fallbackHeight)
        Size(widthCells, heightCells)
    }
}

fun Context.getCellCount(size: Int): Int {
    val tiles = Math.ceil(((size / resources.displayMetrics.density) - 30) / 70.0).toInt()
    return Math.max(tiles, 1)
}
