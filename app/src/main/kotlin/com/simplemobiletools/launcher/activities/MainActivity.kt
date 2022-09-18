package com.simplemobiletools.launcher.activities

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.RelativeLayout
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.extensions.launchApp
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import com.simplemobiletools.launcher.fragments.MyFragment
import com.simplemobiletools.launcher.fragments.WidgetsFragment
import com.simplemobiletools.launcher.helpers.ROW_COUNT
import com.simplemobiletools.launcher.interfaces.FlingListener
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : SimpleActivity(), FlingListener {
    private val ANIMATION_DURATION = 150L

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

        if (!config.wasHomeScreenInit) {
            ensureBackgroundThread {
                getDefaultAppPackages()
                config.wasHomeScreenInit = true
                home_screen_grid.fetchAppIcons(true)
            }
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
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun hideFragment(fragment: View) {
        ObjectAnimator.ofFloat(fragment, "y", mScreenHeight.toFloat()).apply {
            duration = ANIMATION_DURATION
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

    fun homeScreenClicked(x: Float, y: Float) {
        if (x >= home_screen_grid.left && x <= home_screen_grid.right && y >= home_screen_grid.top && y <= home_screen_grid.bottom) {
            home_screen_grid.gridClicked(x - home_screen_grid.marginLeft, y - home_screen_grid.marginTop) { packageName ->
                launchApp(packageName)
            }
        }
    }

    private fun showWidgetsFragment() {
        showFragment(widgets_fragment)
    }

    private class MyGestureListener(private val flingListener: FlingListener) : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(event: MotionEvent): Boolean {
            (flingListener as MainActivity).homeScreenClicked(event.x, event.y)
            return super.onSingleTapUp(event)
        }

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

    private fun getDefaultAppPackages() {
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val defaultDialerPackage = (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            val dialerIcon = HomeScreenGridItem(null, 0, ROW_COUNT - 1, 1, ROW_COUNT, defaultDialerPackage)
            homeScreenGridItems.add(dialerIcon)
        } catch (e: Exception) {
        }

        try {
            val defaultSMSMessengerPackage = Telephony.Sms.getDefaultSmsPackage(this)
            val SMSMessengerIcon = HomeScreenGridItem(null, 1, ROW_COUNT - 1, 2, ROW_COUNT, defaultSMSMessengerPackage)
            homeScreenGridItems.add(SMSMessengerIcon)
        } catch (e: Exception) {
        }

        try {
            val browserIntent = Intent("android.intent.action.VIEW", Uri.parse("http://"))
            val resolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            val browserIcon = HomeScreenGridItem(null, 2, ROW_COUNT - 1, 3, ROW_COUNT, defaultBrowserPackage)
            homeScreenGridItems.add(browserIcon)
        } catch (e: Exception) {
        }

        try {
            val potentialStores = arrayListOf("com.android.vending", "org.fdroid.fdroid", "com.aurora.store")
            val storePackage = potentialStores.firstOrNull { isPackageInstalled(it) }
            if (storePackage != null) {
                val storeIcon = HomeScreenGridItem(null, 3, ROW_COUNT - 1, 4, ROW_COUNT, storePackage)
                homeScreenGridItems.add(storeIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveInfo = packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultCameraPackage = resolveInfo!!.activityInfo.packageName
            val cameraIcon = HomeScreenGridItem(null, 4, ROW_COUNT - 1, 5, ROW_COUNT, defaultCameraPackage)
            homeScreenGridItems.add(cameraIcon)
        } catch (e: Exception) {
        }

        homeScreenGridItemsDB.insertAll(homeScreenGridItems)
    }
}
