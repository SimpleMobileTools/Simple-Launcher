package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.adapters.LaunchersAdapter
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.extensions.launchApp
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON
import com.simplemobiletools.launcher.interfaces.AllAppsListener
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.all_apps_fragment.view.*

class AllAppsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet), AllAppsListener {
    private var lastTouchCoords = Pair(0f, 0f)
    var touchDownY = -1
    var ignoreTouches = false
    var hasTopPadding = false

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity

        all_apps_grid.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun onResume() {
        if (all_apps_grid?.layoutManager == null || all_apps_grid?.adapter == null) {
            return
        }

        val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
        if (layoutManager.spanCount != context.config.drawerColumnCount) {
            onConfigurationChanged()
            // Force redraw due to changed item size
            (all_apps_grid.adapter as LaunchersAdapter).notifyDataSetChanged()
        }
    }

    fun onConfigurationChanged() {
        if (all_apps_grid == null) {
            return
        }

        all_apps_grid.scrollToPosition(0)
        all_apps_fastscroller.resetManualScrolling()
        setupViews()

        val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.config.drawerColumnCount
        val launchers = (all_apps_grid.adapter as LaunchersAdapter).launchers
        setupAdapter(launchers)
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
            shouldIntercept = touchDownY - event.y.toInt() < 0 && all_apps_grid.computeVerticalScrollOffset() == 0
            if (shouldIntercept) {
                activity?.startHandlingTouches(touchDownY)
                touchDownY = -1
            }
        } else {
            touchDownY = event.y.toInt()
        }

        return shouldIntercept
    }

    fun gotLaunchers(appLaunchers: ArrayList<AppLauncher>) {
        val sorted = appLaunchers.sortedWith(
            compareBy({
                it.title.normalizeString().lowercase()
            }, {
                it.packageName
            })
        ).toMutableList() as ArrayList<AppLauncher>

        setupAdapter(sorted)
    }

    private fun setupAdapter(launchers: ArrayList<AppLauncher>) {
        activity?.runOnUiThread {
            val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.config.drawerColumnCount

            val currAdapter = all_apps_grid.adapter
            if (currAdapter == null) {
                LaunchersAdapter(activity!!, launchers, this) {
                    activity?.launchApp((it as AppLauncher).packageName, it.activityName)
                    ignoreTouches = false
                    touchDownY = -1
                }.apply {
                    all_apps_grid.adapter = this
                }
            } else {
                (currAdapter as LaunchersAdapter).updateItems(launchers)
            }
        }
    }

    fun hideIcon(item: HomeScreenGridItem) {
        (all_apps_grid.adapter as? LaunchersAdapter)?.hideIcon(item)
    }

    fun setupViews(addTopPadding: Boolean = hasTopPadding) {
        if (activity == null) {
            return
        }

        all_apps_fastscroller.updateColors(context.getProperPrimaryColor())

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

        all_apps_grid.setPadding(0, 0, resources.getDimension(R.dimen.medium_margin).toInt(), bottomListPadding)
        all_apps_fastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)

        hasTopPadding = addTopPadding
        val topPadding = if (addTopPadding) activity!!.statusBarHeight else 0
        setPadding(0, topPadding, 0, 0)
        background = ColorDrawable(context.getProperBackgroundColor())
        (all_apps_grid.adapter as? LaunchersAdapter)?.updateTextColor(context.getProperTextColor())

        search_bar.beVisibleIf(context.config.useSearchBar)
        search_bar.getToolbar()?.beGone()
        search_bar.updateColors()
        search_bar.setupMenu()

        search_bar.onSearchTextChangedListener = {
            (all_apps_grid.adapter as? LaunchersAdapter)?.updateSearchQuery(it)
        }
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher) {
        val gridItem = HomeScreenGridItem(
            null,
            -1,
            -1,
            -1,
            -1,
            appLauncher.packageName,
            appLauncher.activityName,
            appLauncher.title,
            ITEM_TYPE_ICON,
            "",
            -1,
            "",
            "",
            null,
            appLauncher.drawable
        )

        activity?.showHomeIconMenu(x, y, gridItem, true)
        ignoreTouches = true
    }

    fun onBackPressed(): Boolean {
        if (search_bar.isSearchOpen) {
            search_bar.closeSearch()
            return true
        }

        return false
    }
}
