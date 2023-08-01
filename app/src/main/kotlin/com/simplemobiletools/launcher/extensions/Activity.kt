package com.simplemobiletools.launcher.extensions

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import com.simplemobiletools.commons.extensions.getPopupMenuTheme
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SettingsActivity
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_FOLDER
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_WIDGET
import com.simplemobiletools.launcher.helpers.UNINSTALL_APP_REQUEST_CODE
import com.simplemobiletools.launcher.interfaces.ItemMenuListener
import com.simplemobiletools.launcher.models.HomeScreenGridItem

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

fun Activity.canAppBeUninstalled(packageName: String): Boolean {
    return try {
        (packageManager.getApplicationInfo(packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (ignored: Exception) {
        false
    }
}

fun Activity.uninstallApp(packageName: String) {
    Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        startActivityForResult(this, UNINSTALL_APP_REQUEST_CODE)
    }
}

fun Activity.handleGridItemPopupMenu(anchorView: View, gridItem: HomeScreenGridItem, isOnAllAppsFragment: Boolean, listener: ItemMenuListener): PopupMenu {
    val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
    return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
        if (isQPlus()) {
            setForceShowIcon(true)
        }

        inflate(R.menu.menu_app_icon)
        menu.findItem(R.id.rename).isVisible = (gridItem.type == ITEM_TYPE_ICON || gridItem.type == ITEM_TYPE_FOLDER) && !isOnAllAppsFragment
        menu.findItem(R.id.hide_icon).isVisible = gridItem.type == ITEM_TYPE_ICON && isOnAllAppsFragment
        menu.findItem(R.id.resize).isVisible = gridItem.type == ITEM_TYPE_WIDGET
        menu.findItem(R.id.app_info).isVisible = gridItem.type == ITEM_TYPE_ICON
        menu.findItem(R.id.uninstall).isVisible = gridItem.type == ITEM_TYPE_ICON && canAppBeUninstalled(gridItem.packageName)
        menu.findItem(R.id.remove).isVisible = !isOnAllAppsFragment
        setOnMenuItemClickListener { item ->
            listener.onAnyClick()
            when (item.itemId) {
                R.id.hide_icon -> listener.hide(gridItem)
                R.id.rename -> listener.rename(gridItem)
                R.id.resize -> listener.resize(gridItem)
                R.id.app_info -> listener.appInfo(gridItem)
                R.id.remove -> listener.remove(gridItem)
                R.id.uninstall -> listener.uninstall(gridItem)
            }
            true
        }

        setOnDismissListener {
            listener.onDismiss()
        }

        listener.beforeShow(menu)

        show()
    }
}
