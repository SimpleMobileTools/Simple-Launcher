package com.simplemobiletools.launcher.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import com.simplemobiletools.commons.extensions.getPopupMenuTheme
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.helpers.UNINSTALL_APP_REQUEST_CODE

fun Activity.launchApp(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    try {
        startActivity(launchIntent)
    } catch (e: Exception) {
        showErrorToast(e)
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

fun Activity.handleAppIconPopupMenu(anchorView: View, appPackageName: String): PopupMenu {
    val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
    return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
        inflate(R.menu.menu_app_icon)
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.app_info -> launchAppInfo(appPackageName)
                R.id.uninstall -> uninstallApp(appPackageName)
            }
            true
        }
        show()
    }
}
