package com.simplemobiletools.launcher.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.hiddenIconsDB
import com.simplemobiletools.launcher.extensions.launchersDB
import com.simplemobiletools.launcher.models.AppLauncher

class LauncherHelper(private val context: Context) {
    @SuppressLint("WrongConstant")
    fun getAllAppLaunchers(): ArrayList<AppLauncher> {
        val hiddenIcons = context.hiddenIconsDB.getHiddenIcons().map {
            it.getIconIdentifier()
        }

        val allApps = ArrayList<AppLauncher>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val simpleLauncher = context.packageName
        val microG = "com.google.android.gms"
        val list = context.packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val packageName = componentInfo.packageName
            if (packageName == simpleLauncher || packageName == microG) {
                continue
            }

            val activityName = info.activityInfo.name
            if (hiddenIcons.contains("$packageName/$activityName")) {
                continue
            }

            val label = info.loadLabel(context.packageManager).toString()
            val drawable = info.loadIcon(context.packageManager) ?: context.getDrawableForPackageName(packageName) ?: continue
            val placeholderColor = calculateAverageColor(drawable.toBitmap())
            allApps.add(AppLauncher(null, label, packageName, activityName, 0, placeholderColor, drawable))
        }

        // add Simple Launchers settings as an app
        val drawable = context.getDrawableForPackageName(context.packageName)
        val placeholderColor = calculateAverageColor(drawable!!.toBitmap())
        val launcherSettings = AppLauncher(null, context.getString(R.string.launcher_settings), context.packageName, "", 0, placeholderColor, drawable)
        allApps.add(launcherSettings)
        context.launchersDB.insertAll(allApps)
        return allApps
    }

    // taken from https://gist.github.com/maxjvh/a6ab15cbba9c82a5065d
    fun calculateAverageColor(bitmap: Bitmap): Int {
        var red = 0
        var green = 0
        var blue = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
            n++
            i += 1
        }

        return Color.rgb(red / n, green / n, blue / n)
    }
}
