package com.simplemobiletools.launcher.extensions

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.launcher.activities.SettingsActivity
import com.simplemobiletools.launcher.helpers.UNINSTALL_APP_REQUEST_CODE

fun Activity.launchApp(packageName: String, activityName: String) {
    // if this is true, launch the app settings
    if (packageName == this.packageName) {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
        return
    }

    try {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
            component = ComponentName.unflattenFromString("$packageName/$activityName")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            startActivity(this)
        }
    } catch (e: Exception) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            startActivity(launchIntent)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Activity.launchAppInfo(packageName: String) {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        startActivity(this)
    }
}

fun Activity.uninstallApp(packageName: String) {
    Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        startActivityForResult(this, UNINSTALL_APP_REQUEST_CODE)
    }
}
