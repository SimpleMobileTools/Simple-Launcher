package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.adapters.WidgetsAdapter
import com.simplemobiletools.launcher.extensions.getInitialCellSize
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_SHORTCUT
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_WIDGET
import com.simplemobiletools.launcher.interfaces.WidgetsFragmentListener
import com.simplemobiletools.launcher.models.*
import kotlinx.android.synthetic.main.widgets_fragment.view.*

class WidgetsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet), WidgetsFragmentListener {
    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false
    var hasTopPadding = false

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        getAppWidgets()

        widgets_list.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    fun onConfigurationChanged() {
        if (widgets_list == null) {
            return
        }

        widgets_list.scrollToPosition(0)
        setupViews()

        val appWidgets = (widgets_list.adapter as WidgetsAdapter).widgetListItems
        setupAdapter(appWidgets)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onInterceptTouchEvent(event)
        }

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
                val cellSize = context.getInitialCellSize(info, info.minWidth, info.minHeight)
                val widthCells = cellSize.width
                val heightCells = cellSize.height
                val className = info.provider.className
                val widget =
                    AppWidget(appPackageName, appTitle, appIcon, widgetTitle, widgetPreviewImage, widthCells, heightCells, false, className, info, null)
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
                val widget = AppWidget(appPackageName, appTitle, appIcon, widgetTitle, widgetPreviewImage, 0, 0, true, "", null, info.activityInfo)
                appWidgets.add(widget)
            }

            appWidgets = appWidgets.sortedWith(compareBy({ it.appTitle }, { it.appPackageName }, { it.widgetTitle })).toMutableList() as ArrayList<AppWidget>
            splitWidgetsByApps(appWidgets)
        }
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
                WidgetsAdapter(activity!!, widgetsListItems, this) {
                    context.toast(R.string.touch_hold_widget)
                    ignoreTouches = false
                    touchDownY = -1
                }.apply {
                    widgets_list.adapter = this
                }
            } else {
                (currAdapter as WidgetsAdapter).updateItems(widgetsListItems)
            }
        }
    }

    fun setupViews(addTopPadding: Boolean = hasTopPadding) {
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

        hasTopPadding = addTopPadding
        val topPadding = if (addTopPadding) activity!!.statusBarHeight else 0
        setPadding(0, topPadding, 0, 0)
        background = ColorDrawable(context.getProperBackgroundColor())
        (widgets_list.adapter as? WidgetsAdapter)?.updateTextColor(context.getProperTextColor())
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
            -1,
            appWidget.appPackageName,
            "",
            "",
            type,
            appWidget.className,
            -1,
            "",
            "",
            null,
            appWidget.widgetPreviewImage,
            appWidget.providerInfo,
            appWidget.activityInfo,
            appWidget.widthCells,
            appWidget.heightCells
        )

        activity?.widgetLongPressedOnList(gridItem)
        ignoreTouches = true
    }
}
