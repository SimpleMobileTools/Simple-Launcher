package com.simplemobiletools.launcher.activities

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import com.simplemobiletools.commons.extensions.appLaunched
import com.simplemobiletools.commons.extensions.realScreenSize
import com.simplemobiletools.commons.extensions.statusBarHeight
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {
    private var mTouchDownY = 0
    private var mScreenHeight = 0
    private var mCurrentFragmentY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        showTransparentNavigation = true

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        window.setDecorFitsSystemWindows(false)
        (all_apps_fragment as AllAppsFragment).setupFragment(this)
        mScreenHeight = realScreenSize.y
        mCurrentFragmentY = mScreenHeight
        all_apps_fragment.y = mScreenHeight.toFloat()
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownY = event.y.toInt()
                mCurrentFragmentY = all_apps_fragment.y.toInt()
            }

            MotionEvent.ACTION_MOVE -> {
                val diffY = mTouchDownY - event.y
                val newY = mCurrentFragmentY - diffY
                all_apps_fragment.y = Math.min(Math.max(0f, newY), mScreenHeight.toFloat())
            }
        }

        return true
    }
}
