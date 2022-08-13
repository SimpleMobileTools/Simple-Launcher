package com.simplemobiletools.launcher.activities

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import com.simplemobiletools.commons.extensions.appLaunched
import com.simplemobiletools.commons.extensions.statusBarHeight
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        showTransparentNavigation = true

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        window.setDecorFitsSystemWindows(false)
        (all_apps_fragment as AllAppsFragment).setupFragment(this)
    }

    override fun onResume() {
        super.onResume()
        (main_holder.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
        updateStatusbarColor(Color.TRANSPARENT)
        (all_apps_fragment as AllAppsFragment).setupViews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (all_apps_fragment as AllAppsFragment).onConfigurationChanged()
    }
}
