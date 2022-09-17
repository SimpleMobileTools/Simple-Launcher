package com.simplemobiletools.launcher.activities

import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.RelativeLayout
import androidx.core.view.GestureDetectorCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import com.simplemobiletools.launcher.fragments.MyFragment
import com.simplemobiletools.launcher.fragments.WidgetsFragment
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
        mScreenHeight = realScreenSize.y
        mCurrentFragmentY = mScreenHeight

        arrayOf(all_apps_fragment as MyFragment, widgets_fragment as MyFragment).forEach { fragment ->
            fragment.setupFragment(this)
            fragment.y = mScreenHeight.toFloat()
            fragment.beVisible()
        }

        (home_screen_grid.layoutParams as RelativeLayout.LayoutParams).apply {
            topMargin = statusBarHeight
            bottomMargin = navigationBarHeight
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusbarColor(Color.TRANSPARENT)
        (all_apps_fragment as AllAppsFragment).setupViews()
        (widgets_fragment as WidgetsFragment).setupViews()
    }

    override fun onBackPressed() {
        if (all_apps_fragment.y != mScreenHeight.toFloat()) {
            hideFragment(all_apps_fragment)
        } else if (widgets_fragment.y != mScreenHeight.toFloat()) {
            hideFragment(widgets_fragment)
        } else {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (all_apps_fragment as AllAppsFragment).onConfigurationChanged()
        (widgets_fragment as WidgetsFragment).onConfigurationChanged()
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
                        showFragment(all_apps_fragment)
                    } else {
                        hideFragment(all_apps_fragment)
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

    private fun showFragment(fragment: View) {
        ObjectAnimator.ofFloat(fragment, "y", 0f).apply {
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun hideFragment(fragment: View) {
        ObjectAnimator.ofFloat(fragment, "y", mScreenHeight.toFloat()).apply {
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun homeScreenLongPressed(x: Float, y: Float) {
        if (all_apps_fragment.y != mScreenHeight.toFloat()) {
            return
        }

        main_holder.performHapticFeedback()

        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = y - resources.getDimension(R.dimen.long_press_anchor_offset_y)
        val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
        PopupMenu(contextTheme, home_screen_popup_menu_anchor, Gravity.TOP or Gravity.END).apply {
            inflate(R.menu.menu_home_screen)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.widgets -> {
                        showWidgetsFragment()
                    }
                }
                true
            }
            show()
        }
    }

    private fun showWidgetsFragment() {
        showFragment(widgets_fragment)
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

        override fun onLongPress(event: MotionEvent) {
            (flingListener as MainActivity).homeScreenLongPressed(event.x, event.y)
        }
    }

    override fun onFlingUp() {
        mIgnoreUpEvent = true
        showFragment(all_apps_fragment)
    }

    override fun onFlingDown() {
        mIgnoreUpEvent = true
        hideFragment(all_apps_fragment)
    }
}
