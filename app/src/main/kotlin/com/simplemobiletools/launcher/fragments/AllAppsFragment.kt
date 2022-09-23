package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
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
import com.simplemobiletools.launcher.extensions.getColumnCount
import com.simplemobiletools.launcher.extensions.launchApp
import com.simplemobiletools.launcher.interfaces.AllAppsListener
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.all_apps_fragment.view.*

class AllAppsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet), AllAppsListener {
    private var touchDownY = -1
    var ignoreTouches = false

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        background.applyColorFilter(activity.getProperBackgroundColor())
        setPadding(0, activity.statusBarHeight, 0, 0)

        all_apps_grid.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                touchDownY = -1
            }

            return@setOnTouchListener false
        }
    }

    fun onConfigurationChanged() {
        all_apps_grid.scrollToPosition(0)
        all_apps_fastscroller.resetManualScrolling()
        setupViews()

        val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context.getColumnCount()
        val launchers = (all_apps_grid.adapter as LaunchersAdapter).launchers
        setupAdapter(launchers)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (ignoreTouches) {
            touchDownY = -1
            return true
        }

        var shouldIntercept = false

        // pull the whole fragment down if it is scrolled way to the top and the users pulls it even further
        if (touchDownY != -1) {
            shouldIntercept = touchDownY - event.y < 0 && all_apps_grid.computeVerticalScrollOffset() == 0
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
        val sorted = appLaunchers.sortedBy { it.title.normalizeString().lowercase() }.toMutableList() as ArrayList<AppLauncher>
        setupAdapter(sorted)
    }

    private fun setupAdapter(launchers: ArrayList<AppLauncher>) {
        activity?.runOnUiThread {
            val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.getColumnCount()

            val currAdapter = all_apps_grid.adapter
            if (currAdapter == null) {
                LaunchersAdapter(activity!!, launchers, all_apps_fastscroller, this) {
                    activity?.launchApp((it as AppLauncher).packageName)
                }.apply {
                    all_apps_grid.adapter = this
                }
            } else {
                (currAdapter as LaunchersAdapter).updateItems(launchers)
            }
        }
    }

    fun setupViews() {
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
    }

    override fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher) {
        val gridItem = HomeScreenGridItem(null, -1, -1, -1, 1, appLauncher.packageName, appLauncher.title, appLauncher.drawable)
        activity?.showHomeIconMenu(x, y, gridItem, true)
        ignoreTouches = true
    }
}
