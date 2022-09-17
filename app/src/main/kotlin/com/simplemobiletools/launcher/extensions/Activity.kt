package com.simplemobiletools.launcher.extensions

import android.app.Activity
import com.simplemobiletools.commons.extensions.showErrorToast

fun Activity.launchApp(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    try {
        startActivity(launchIntent)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}
