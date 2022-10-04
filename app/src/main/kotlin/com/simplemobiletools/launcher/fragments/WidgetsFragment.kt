package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.adapters.WidgetsAdapter
import com.simplemobiletools.launcher.helpers.COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_SHORTCUT
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_WIDGET
import com.simplemobiletools.launcher.helpers.ROW_COUNT
import com.simplemobiletools.launcher.interfaces.WidgetsFragmentListener
import com.simplemobiletools.launcher.models.*
import kotlinx.android.synthetic.main.widgets_fragment.view.*

class WidgetsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet), WidgetsFragmentListener {
    private var touchDownY = -1
    private var lastTouchCoords = Pair(0f, 0f)
    var ignoreTouches = false

    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        background.applyColorFilter(activity.getProperBackgroundColor())
        setPadding(0, activity.statusBarHeight, 0, 0)
        getAppWidgets()

        widgets_list.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    fun onConfigurationChanged() {
        widgets_list.scrollToPosition(0)
        setupViews()

        val appWidgets = (widgets_list.adapter as WidgetsAdapter).widgetListItems
        setupAdapter(appWidgets)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (ignoreTouches) {
            // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
            if (lastTouchCoords.first != event.x || lastTouchCoords.second != event.y) {
                touchDownY = -1
                return true
            }
        }

        lastTouchCoords = Pair(event.x, event.y)
        var shouldIntercept = false

        // pull the whole fragment down if it is scrolled way to the top and the users pulls it even further
        if (touchDownY != -1) {
            shouldIntercept = touchDownY - event.y.toInt() < 0 && widgets_list.computeVerticalScrollOffset() == 0
            if (shouldIntercept) {
                activity?.startHandlingTouches(touchDownY)
                touchDownY = -1
            }
        } else {
            touchDownY = event.y.toInt()
        }

        return shouldIntercept
    }

    @SuppressLint("WrongConstant")
    fun getAppWidgets() {
        ensureBackgroundThread {
            // get the casual widgets
            var appWidgets = ArrayList<AppWidget>()
            val manager = AppWidgetManager.getInstance(context)
            val packageManager = context.packageManager
            val infoList = manager.installedProviders
            for (info in infoList) {
                val appPackageName = info.provider.packageName
                val appMetadata = getAppMetadataFromPackage(appPackageName) ?: continue
                val appTitle = appMetadata.appTitle
                val appIcon = appMetadata.appIcon
                val widgetTitle = info.loadLabel(packageManager)
                val widgetPreviewImage = info.loadPreviewImage(context, resources.displayMetrics.densityDpi) ?: appIcon
                val widthCells = Math.min(COLUMN_COUNT, getTileCount(info.minWidth))
                val heightCells = Math.min(ROW_COUNT, getTileCount(info.minHeight))
                val className = info.provider.className
                val widget = AppWidget(appPackageName, appTitle, appIcon, widgetTitle, widgetPreviewImage, widthCells, heightCells, false, className, info)
                appWidgets.add(widget)
            }

            // show also the widgets that are technically shortcuts
            val intent = Intent(Intent.ACTION_CREATE_SHORTCUT, null)
            val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
            for (info in list) {
                val componentInfo = info.activityInfo.applicationInfo
                val appTitle = componentInfo.loadLabel(packageManager).toString()
                val appPackageName = componentInfo.packageName
                val appMetadata = getAppMetadataFromPackage(appPackageName) ?: continue
                val appIcon = appMetadata.appIcon
                val widgetTitle = info.loadLabel(packageManager).toString()
                val widgetPreviewImage = packageManager.getDrawable(componentInfo.packageName, info.iconResource, componentInfo)
                val widget = AppWidget(appPackageName, appTitle, appIcon, widgetTitle, widgetPreviewImage, 0, 0, true, "", null)
                appWidgets.add(widget)
            }

            appWidgets = appWidgets.sortedWith(compareBy({ it.appTitle }, { it.widgetTitle })).toMutableList() as ArrayList<AppWidget>
            splitWidgetsByApps(appWidgets)
        }
    }

    private fun getTileCount(size: Int): Int {
        val tiles = Math.ceil(((size / resources.displayMetrics.density) - 30) / 70.0).toInt()
        return Math.max(tiles, 1)
    }

    private fun splitWidgetsByApps(appWidgets: ArrayList<AppWidget>) {
        var currentAppPackageName = ""
        val widgetListItems = ArrayList<WidgetsListItem>()
        var currentAppWidgets = ArrayList<AppWidget>()
        appWidgets.forEach { appWidget ->
            if (appWidget.appPackageName != currentAppPackageName) {
                if (widgetListItems.isNotEmpty()) {
                    widgetListItems.add(WidgetsListItemsHolder(currentAppWidgets))
                    currentAppWidgets = ArrayList()
                }

                widgetListItems.add(WidgetsListSection(appWidget.appTitle, appWidget.appIcon))
            }

            currentAppWidgets.add(appWidget)
            currentAppPackageName = appWidget.appPackageName
        }

        if (widgetListItems.isNotEmpty()) {
            widgetListItems.add(WidgetsListItemsHolder(currentAppWidgets))
        }

        setupAdapter(widgetListItems)
    }

    private fun setupAdapter(widgetsListItems: ArrayList<WidgetsListItem>) {
        activity?.runOnUiThread {
            val currAdapter = widgets_list.adapter
            if (currAdapter == null) {
                WidgetsAdapter(activity!!, widgetsListItems, this).apply {
                    widgets_list.adapter = this
                }
            } else {
                (currAdapter as WidgetsAdapter).updateItems(widgetsListItems)
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

        widgets_list.setPadding(0, 0, 0, bottomListPadding)
        widgets_fastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)
    }

    private fun getAppMetadataFromPackage(packageName: String): WidgetsListSection? {
        try {
            val appInfo = activity!!.packageManager.getApplicationInfo(packageName, 0)
            val appTitle = activity!!.packageManager.getApplicationLabel(appInfo).toString()

            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activityList = launcher.getActivityList(packageName, Process.myUserHandle())
            var appIcon = activityList.firstOrNull()?.getBadgedIcon(0)

            if (appIcon == null) {
                appIcon = context.packageManager.getApplicationIcon(packageName)
            }

            if (appTitle.isNotEmpty()) {
                return WidgetsListSection(appTitle, appIcon)
            }
        } catch (ignored: Exception) {
        } catch (error: Error) {
        }

        return null
    }

    override fun onWidgetLongPressed(appWidget: AppWidget) {
        val type = if (appWidget.isShortcut) {
            ITEM_TYPE_SHORTCUT
        } else {
            ITEM_TYPE_WIDGET
        }

        val gridItem = HomeScreenGridItem(
            null,
            -1,
            -1,
            -1,
            -1,
            appWidget.widthCells,
            appWidget.heightCells,
            appWidget.appPackageName,
            "",
            type,
            appWidget.className,
            -1,
            appWidget.widgetPreviewImage,
            appWidget.providerInfo
        )

        activity?.widgetLongPressedOnList(gridItem)
        ignoreTouches = true
    }
}
