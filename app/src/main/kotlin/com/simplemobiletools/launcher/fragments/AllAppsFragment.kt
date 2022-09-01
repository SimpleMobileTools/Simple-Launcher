package com.simplemobiletools.launcher.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.adapters.LaunchersAdapter
import com.simplemobiletools.launcher.extensions.getColumnCount
import com.simplemobiletools.launcher.models.AppLauncher
import kotlinx.android.synthetic.main.all_apps_fragment.view.*

class AllAppsFragment(context: Context, attributeSet: AttributeSet) : MyFragment(context, attributeSet) {
    private var touchDownY = -1

    @SuppressLint("ClickableViewAccessibility")
    override fun setupFragment(activity: MainActivity) {
        this.activity = activity
        background.applyColorFilter(activity.getProperBackgroundColor())
        setPadding(0, activity.statusBarHeight, 0, 0)
        getLaunchers()

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
        var shouldIntercept = false
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

    @SuppressLint("WrongConstant")
    private fun getLaunchers() {
        val allApps = ArrayList<AppLauncher>()
        val allPackageNames = ArrayList<String>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        ensureBackgroundThread {
            val list = context.packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
            for (info in list) {
                val componentInfo = info.activityInfo.applicationInfo
                val label = componentInfo.loadLabel(context.packageManager).toString()
                val packageName = componentInfo.packageName

                var drawable: Drawable? = null
                try {
                    // try getting the properly colored launcher icons
                    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val activityList = launcher.getActivityList(packageName, Process.myUserHandle())[0]
                    drawable = activityList.getBadgedIcon(0)
                } catch (e: Exception) {
                } catch (e: Error) {
                }

                if (drawable == null) {
                    drawable = try {
                        context.packageManager.getApplicationIcon(packageName)
                    } catch (ignored: Exception) {
                        continue
                    }
                }

                allPackageNames.add(packageName)
                allApps.add(AppLauncher(0, label, packageName, 0, drawable))
            }

            val launchers = allApps.distinctBy { it.packageName } as ArrayList<AppLauncher>
            launchers.sortBy { it.title.toLowerCase() }

            val layoutManager = all_apps_grid.layoutManager as MyGridLayoutManager
            layoutManager.spanCount = context.getColumnCount()
            setupAdapter(launchers)
        }
    }

    private fun setupAdapter(launchers: ArrayList<AppLauncher>) {
        activity?.runOnUiThread {
            LaunchersAdapter(activity!!, launchers, all_apps_fastscroller) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage((it as AppLauncher).packageName)
                try {
                    activity!!.startActivity(launchIntent)
                } catch (e: Exception) {
                    activity?.showErrorToast(e)
                }
            }.apply {
                all_apps_grid.adapter = this
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
}
