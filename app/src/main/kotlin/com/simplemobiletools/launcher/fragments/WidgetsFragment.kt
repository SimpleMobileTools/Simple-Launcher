package com.simplemobiletools.launcher.fragments

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getProperBackgroundColor
import com.simplemobiletools.commons.extensions.statusBarHeight
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.models.AppWidget

class WidgetsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet) {
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        background.applyColorFilter(activity.getProperBackgroundColor())
        setPadding(0, activity.statusBarHeight, 0, 0)
        getWidgets()
    }

    private fun getWidgets() {
        ensureBackgroundThread {
            var widgets = ArrayList<AppWidget>()
            val manager = AppWidgetManager.getInstance(context)
            val infoList = manager.installedProviders
            for (info in infoList) {
                val appPackageName = info.provider.packageName
                val appTitle = getAppNameFromPackage(appPackageName) ?: continue
                val widgetTitle = info.loadLabel(activity?.packageManager)
                val width = info.minWidth
                val height = info.minHeight
                val widget = AppWidget(appPackageName, appTitle, widgetTitle, width, height)
                widgets.add(widget)
            }

            widgets = widgets.sortedWith(compareBy({ it.appTitle }, { it.widgetTitle })).toMutableList() as ArrayList<AppWidget>
        }
    }

    private fun getAppNameFromPackage(packageName: String): String? {
        try {
            val appInfo = activity!!.packageManager.getApplicationInfo(packageName, 0)
            return activity!!.packageManager.getApplicationLabel(appInfo).toString()
        } catch (ignored: Exception) {
        }

        return null
    }
}
