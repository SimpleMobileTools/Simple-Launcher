package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Process
import android.view.*
import androidx.fragment.app.Fragment
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.SimpleActivity
import com.simplemobiletools.launcher.adapters.WidgetsAdapter
import com.simplemobiletools.launcher.extensions.getInitialCellSize
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_SHORTCUT
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_WIDGET
import com.simplemobiletools.launcher.models.*
import kotlinx.android.synthetic.main.widgets_fragment.view.widgets_list
import kotlinx.android.synthetic.main.widgets_fragment.widgets_fastscroller
import kotlinx.android.synthetic.main.widgets_fragment.widgets_list

class WidgetsBottomSheetFragment : Fragment(){
    var hasTopPadding = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.widgets_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        getAppWidgets()
    }

    fun onConfigurationChanged() {
        view?.widgets_list?.scrollToPosition(0)
        setupViews()

        val appWidgets = (view?.widgets_list?.adapter as? WidgetsAdapter)?.widgetListItems
        if (appWidgets != null) {
            setupAdapter(appWidgets)
        }
    }

    @SuppressLint("WrongConstant")
    fun getAppWidgets() {
        ensureBackgroundThread {
            // get the casual widgets
            var appWidgets = ArrayList<AppWidget>()
            val manager = AppWidgetManager.getInstance(context)
            val packageManager = requireContext().packageManager
            val infoList = manager.installedProviders
            for (info in infoList) {
                val appPackageName = info.provider.packageName
                val appMetadata = getAppMetadataFromPackage(appPackageName) ?: continue
                val appTitle = appMetadata.appTitle
                val appIcon = appMetadata.appIcon
                val widgetTitle = info.loadLabel(packageManager)
                val widgetPreviewImage = info.loadPreviewImage(requireContext(), resources.displayMetrics.densityDpi) ?: appIcon
                val cellSize = requireContext().getInitialCellSize(info, info.minWidth, info.minHeight)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupAdapter(widgetsListItems: ArrayList<WidgetsListItem>) {
        activity?.runOnUiThread {
            val currAdapter = view?.widgets_list?.adapter
            if (currAdapter == null) {
                WidgetsAdapter(requireActivity() as SimpleActivity, widgetsListItems, onItemLongPressed = {v, appWidget ->
                    var longPressFlag = true
                    v.setOnTouchListener { v1, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                // Start a runnable with delay of long press timeout
                                v1.postDelayed({
                                    longPressFlag = true
                                }, ViewConfiguration.getLongPressTimeout().toLong())
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (longPressFlag) {
                                    // Long press detected and the user moved the view, start drag
                                    startDrag(v, appWidget)
                                    // Remove the long press flag
                                    longPressFlag = false
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                // Remove the long press flag
                                longPressFlag = false
                                true
                            }
                            else -> false
                        }
                    }
                }) {
                    requireContext().toast(R.string.touch_hold_widget)
                }.apply {
                    view?.widgets_list?.adapter = this
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

        widgets_fastscroller.updateColors(requireContext().getProperPrimaryColor())

        var bottomListPadding = 0
        var leftListPadding = 0
        var rightListPadding = 0

        if (requireActivity().navigationBarOnBottom) {
            bottomListPadding = requireActivity().navigationBarHeight
            leftListPadding = 0
            rightListPadding = 0
        } else if (requireActivity().navigationBarOnSide) {
            bottomListPadding = 0

            val display = if (isRPlus()) {
                view?.display!!
            } else {
                (requireActivity().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            if (display.rotation == Surface.ROTATION_90) {
                rightListPadding = requireActivity().navigationBarWidth
            } else if (display.rotation == Surface.ROTATION_270) {
                leftListPadding = requireActivity().navigationBarWidth
            }
        }

        widgets_list.setPadding(0, 0, 0, bottomListPadding)
        widgets_fastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)

        hasTopPadding = addTopPadding
        val topPadding = if (addTopPadding) requireActivity().statusBarHeight else 0
        view?.setPadding(0, topPadding, 0, 0)
        view?.background = ColorDrawable(requireContext().getProperBackgroundColor())
        (widgets_list.adapter as? WidgetsAdapter)?.updateTextColor(requireContext().getProperTextColor())
    }

    private fun getAppMetadataFromPackage(packageName: String): WidgetsListSection? {
        try {
            val appInfo = requireActivity().packageManager.getApplicationInfo(packageName, 0)
            val appTitle = requireActivity().packageManager.getApplicationLabel(appInfo).toString()

            val launcher = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activityList = launcher.getActivityList(packageName, Process.myUserHandle())
            var appIcon = activityList.firstOrNull()?.getBadgedIcon(0)

            if (appIcon == null) {
                appIcon = requireContext().packageManager.getApplicationIcon(packageName)
            }

            if (appTitle.isNotEmpty()) {
                return WidgetsListSection(appTitle, appIcon)
            }
        } catch (ignored: Exception) {
        } catch (error: Error) {
        }

        return null
    }

    fun startDrag(v: View, appWidget: AppWidget) {
        val type = if (appWidget.isShortcut) {
            ITEM_TYPE_SHORTCUT
        }
        else {
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
        val data = ClipData.newPlainText(gridItem.title, gridItem.toString())
        val shadowBuilder = View.DragShadowBuilder(v)
        v.startDragAndDrop(data, shadowBuilder, gridItem, 0)
        requireActivity().onBackPressed()
    }
}
