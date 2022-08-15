package com.simplemobiletools.launcher.activities

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.core.view.GestureDetectorCompat
import com.simplemobiletools.commons.extensions.appLaunched
import com.simplemobiletools.commons.extensions.realScreenSize
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import com.simplemobiletools.launcher.interfaces.FlingListener
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity(), FlingListener {
    private var mTouchDownY = -1
    private var mCurrentFragmentY = 0
    private var mScreenHeight = 0
    private var mIgnoreUpEvent = false
    private lateinit var mDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        showTransparentNavigation = true

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        mDetector = GestureDetectorCompat(this, MyGestureListener(this))
        window.setDecorFitsSystemWindows(false)
        (all_apps_fragment as AllAppsFragment).setupFragment(this)
        mScreenHeight = realScreenSize.y
        mCurrentFragmentY = mScreenHeight
        all_apps_fragment.y = mScreenHeight.toFloat()
    }

    override fun onResume() {
        super.onResume()
        updateStatusbarColor(Color.TRANSPARENT)
        (all_apps_fragment as AllAppsFragment).setupViews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (all_apps_fragment as AllAppsFragment).onConfigurationChanged()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownY = event.y.toInt()
                mCurrentFragmentY = all_apps_fragment.y.toInt()
                mIgnoreUpEvent = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (mTouchDownY != -1) {
                    val diffY = mTouchDownY - event.y
                    val newY = mCurrentFragmentY - diffY
                    all_apps_fragment.y = Math.min(Math.max(0f, newY), mScreenHeight.toFloat())
                }
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                mTouchDownY = -1
                if (!mIgnoreUpEvent) {
                    if (all_apps_fragment.y < mScreenHeight * 0.7) {
                        showAllAppsFragment()
                    } else {
                        hideAllAppsFragment()
                    }
                }
            }
        }

        return true
    }

    fun startHandlingTouches(touchDownY: Int) {
        mTouchDownY = touchDownY
        mCurrentFragmentY = all_apps_fragment.y.toInt()
        mIgnoreUpEvent = false
    }

    private fun showAllAppsFragment() {
        ObjectAnimator.ofFloat(all_apps_fragment, "y", 0f).apply {
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun hideAllAppsFragment() {
        ObjectAnimator.ofFloat(all_apps_fragment, "y", mScreenHeight.toFloat()).apply {
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private class MyGestureListener(private val flingListener: FlingListener) : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(event1: MotionEvent, event2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityY > 0) {
                flingListener.onFlingDown()
            } else {
                flingListener.onFlingUp()
            }
            return true
        }
    }

    override fun onFlingUp() {
        mIgnoreUpEvent = true
        showAllAppsFragment()
    }

    override fun onFlingDown() {
        mIgnoreUpEvent = true
        hideAllAppsFragment()
    }
}
