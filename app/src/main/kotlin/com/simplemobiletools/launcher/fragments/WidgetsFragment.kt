package com.simplemobiletools.launcher.fragments

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.adapters.WidgetsAdapter
import com.simplemobiletools.launcher.models.AppWidget
import kotlinx.android.synthetic.main.widgets_fragment.view.*

class WidgetsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet) {
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        background.applyColorFilter(activity.getProperBackgroundColor())
        setPadding(0, activity.statusBarHeight, 0, 0)
        getAppWidgets()
    }

    fun onConfigurationChanged() {
        widgets_list.scrollToPosition(0)
        setupViews()

        val appWidgets = (widgets_list.adapter as WidgetsAdapter).appWidgets
        setupAdapter(appWidgets)
    }

    private fun getAppWidgets() {
        ensureBackgroundThread {
            var appWidgets = ArrayList<AppWidget>()
            val manager = AppWidgetManager.getInstance(context)
            val infoList = manager.installedProviders
            for (info in infoList) {
                val appPackageName = info.provider.packageName
                val appTitle = getAppNameFromPackage(appPackageName) ?: continue
                val widgetTitle = info.loadLabel(activity?.packageManager)
                val width = info.minWidth
                val height = info.minHeight
                val widget = AppWidget(appPackageName, appTitle, widgetTitle, width, height)
                appWidgets.add(widget)
            }

            appWidgets = appWidgets.sortedWith(compareBy({ it.appTitle }, { it.widgetTitle })).toMutableList() as ArrayList<AppWidget>
            setupAdapter(appWidgets)
        }
    }

    private fun setupAdapter(appWidgets: ArrayList<AppWidget>) {
        activity?.runOnUiThread {
            WidgetsAdapter(activity!!, appWidgets) {

            }.apply {
                widgets_list.adapter = this
            }
        }
    }

    fun setupViews() {
        if (activity == null) {
            return
        }

        widgets_fastscroller.updateColors(context.getProperPrimaryColor())

        var bottomListPadding = 0
        var leftListPadding = 0
        var rightListPadding = 0

        if (activity!!.navigationBarOnBottom) {
            bottomListPadding = activity!!.navigationBarHeight
            leftListPadding = 0
            rightListPadding = 0
        } else if (activity!!.navigationBarOnSide) {
            bottomListPadding = 0

            val display = if (isRPlus()) {
                display!!
            } else {
                (activity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            if (display.rotation == Surface.ROTATION_90) {
                rightListPadding = activity!!.navigationBarWidth
            } else if (display.rotation == Surface.ROTATION_270) {
                leftListPadding = activity!!.navigationBarWidth
            }
        }

        widgets_list.setPadding(0, 0, resources.getDimension(R.dimen.medium_margin).toInt(), bottomListPadding)
        widgets_fastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)
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
