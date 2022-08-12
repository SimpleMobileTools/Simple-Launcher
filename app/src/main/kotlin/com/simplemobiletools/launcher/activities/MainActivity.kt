package com.simplemobiletools.launcher.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.commons.views.MyGridLayoutManager
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.adapters.LaunchersAdapter
import com.simplemobiletools.launcher.models.AppLauncher
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        getLaunchers()
    }

    override fun onResume() {
        super.onResume()
        launchers_fastscroller.updateColors(getProperPrimaryColor())
        (launchers_holder.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
        updateStatusbarColor(Color.TRANSPARENT)
        setupNavigationBar()
    }

    @SuppressLint("WrongConstant")
    private fun getLaunchers() {
        val allApps = ArrayList<AppLauncher>()
        val allPackageNames = ArrayList<String>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val label = componentInfo.loadLabel(packageManager).toString()
            val packageName = componentInfo.packageName

            var drawable: Drawable? = null
            try {
                // try getting the properly colored launcher icons
                val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val activityList = launcher.getActivityList(packageName, android.os.Process.myUserHandle())[0]
                drawable = activityList.getBadgedIcon(0)
            } catch (e: Exception) {
            } catch (e: Error) {
            }

            if (drawable == null) {
                drawable = try {
                    packageManager.getApplicationIcon(packageName)
                } catch (ignored: Exception) {
                    continue
                }
            }

            allPackageNames.add(packageName)
            allApps.add(AppLauncher(0, label, packageName, 0, drawable))
        }

        val launchers = allApps.distinctBy { it.packageName } as ArrayList<AppLauncher>
        launchers.sortBy { it.title.toLowerCase() }

        val layoutManager = launchers_grid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = if (portrait) {
            resources.getInteger(R.integer.portrait_column_count)
        } else {
            resources.getInteger(R.integer.landscape_column_count)
        }

        setupAdapter(launchers)
    }

    private fun setupAdapter(launchers: ArrayList<AppLauncher>) {
        LaunchersAdapter(this, launchers) {

        }.apply {
            launchers_grid.adapter = this
        }
    }

    private fun setupNavigationBar() {
        var bottomListPadding = 0
        var leftListPadding = 0
        var rightListPadding = 0

        if (navigationBarOnBottom) {
            bottomListPadding = navigationBarHeight
            leftListPadding = 0
            rightListPadding = 0
        } else if (navigationBarOnSide) {
            bottomListPadding = 0

            val display = if (isRPlus()) {
                display!!
            } else {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }

            if (display.rotation == Surface.ROTATION_90) {
                rightListPadding = navigationBarWidth
            } else if (display.rotation == Surface.ROTATION_270) {
                leftListPadding = navigationBarWidth
            }
        }

        launchers_grid.setPadding(0, 0, resources.getDimension(R.dimen.medium_margin).toInt(), bottomListPadding)
        launchers_fastscroller.setPadding(leftListPadding, 0, rightListPadding, 0)
    }
}
