package com.simplemobiletools.launcher.activities

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GestureDetectorCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.*
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import com.simplemobiletools.launcher.fragments.MyFragment
import com.simplemobiletools.launcher.fragments.WidgetsFragment
import com.simplemobiletools.launcher.helpers.ROW_COUNT
import com.simplemobiletools.launcher.helpers.UNINSTALL_APP_REQUEST_CODE
import com.simplemobiletools.launcher.interfaces.FlingListener
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity(), FlingListener {
    private val ANIMATION_DURATION = 150L

    private var mTouchDownY = -1
    private var mCurrentFragmentY = 0
    private var mScreenHeight = 0
    private var mIgnoreUpEvent = false
    private var mIgnoreMoveEvents = false
    private var mLongPressedIcon: HomeScreenGridItem? = null
    private var mOpenPopupMenu: PopupMenu? = null
    private var mCachedLaunchers = ArrayList<AppLauncher>()

    private lateinit var mDetector: GestureDetectorCompat

    companion object {
        private var mLastUpEvent = 0L
    }

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
    }

    override fun onResume() {
        super.onResume()
        updateStatusbarColor(Color.TRANSPARENT)
        (all_apps_fragment as AllAppsFragment).setupViews()
        (widgets_fragment as WidgetsFragment).setupViews()

        ensureBackgroundThread {
            if (mCachedLaunchers.isEmpty()) {
                mCachedLaunchers = launchersDB.getAppLaunchers() as ArrayList<AppLauncher>
                (all_apps_fragment as AllAppsFragment).gotLaunchers(mCachedLaunchers)
            }

            refetchLaunchers()
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == UNINSTALL_APP_REQUEST_CODE) {
            ensureBackgroundThread {
                refetchLaunchers()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (all_apps_fragment as AllAppsFragment).onConfigurationChanged()
        (widgets_fragment as WidgetsFragment).onConfigurationChanged()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mLongPressedIcon != null && event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            mLastUpEvent = System.currentTimeMillis()
        }

        mDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownY = event.y.toInt()
                mCurrentFragmentY = all_apps_fragment.y.toInt()
                mIgnoreUpEvent = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (mLongPressedIcon != null && mOpenPopupMenu != null) {
                    mOpenPopupMenu?.dismiss()
                    mOpenPopupMenu = null
                    home_screen_grid.itemDraggingStarted(mLongPressedIcon!!)
                    hideFragment(all_apps_fragment)
                }

                if (mLongPressedIcon != null) {
                    home_screen_grid.draggedItemMoved(event.x.toInt(), event.y.toInt())
                }

                if (mTouchDownY != -1 && !mIgnoreMoveEvents) {
                    val diffY = mTouchDownY - event.y
                    val newY = mCurrentFragmentY - diffY
                    all_apps_fragment.y = Math.min(Math.max(0f, newY), mScreenHeight.toFloat())
                }
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                mTouchDownY = -1
                mIgnoreMoveEvents = false
                mLongPressedIcon = null
                (all_apps_fragment as AllAppsFragment).ignoreTouches = false
                home_screen_grid.itemDraggingStopped(event.x.toInt(), event.y.toInt())
                if (!mIgnoreUpEvent) {
                    if (all_apps_fragment.y < mScreenHeight * 0.7) {
                        showFragment(all_apps_fragment)
                    } else if (all_apps_fragment.y != realScreenSize.y.toFloat()) {
                        hideFragment(all_apps_fragment)
                    }
                }
            }
        }

        return true
    }

    private fun refetchLaunchers() {
        val launchers = getAllAppLaunchers()
        (all_apps_fragment as AllAppsFragment).gotLaunchers(launchers)
        (widgets_fragment as WidgetsFragment).getAppWidgets()

        var hasDeletedAnything = false
        mCachedLaunchers.map { it.packageName }.forEach { packageName ->
            if (!launchers.map { it.packageName }.contains(packageName)) {
                hasDeletedAnything = true
                launchersDB.deleteApp(packageName)
                homeScreenGridItemsDB.deleteByPackageName(packageName)
            }
        }

        if (hasDeletedAnything) {
            home_screen_grid.fetchGridItems()
        }

        mCachedLaunchers = launchers

        if (!config.wasHomeScreenInit) {
            ensureBackgroundThread {
                getDefaultAppPackages(launchers)
                config.wasHomeScreenInit = true
                home_screen_grid.fetchGridItems()
            }
        }
    }

    fun startHandlingTouches(touchDownY: Int) {
        mLongPressedIcon = null
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

        window.navigationBarColor = resources.getColor(R.color.semitransparent_navigation)
    }

    private fun hideFragment(fragment: View) {
        ObjectAnimator.ofFloat(fragment, "y", mScreenHeight.toFloat()).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = Color.TRANSPARENT
    }

    fun homeScreenLongPressed(x: Float, y: Float) {
        if (all_apps_fragment.y != mScreenHeight.toFloat()) {
            return
        }

        mIgnoreMoveEvents = true
        main_holder.performHapticFeedback()
        val clickedGridItem = home_screen_grid.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            showHomeIconMenu(x, y - resources.getDimension(R.dimen.icon_long_press_anchor_offset_y), clickedGridItem, false)
            return
        }

        showMainLongPressMenu(x, y)
    }

    fun homeScreenClicked(x: Float, y: Float) {
        val clickedGridItem = home_screen_grid.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            launchApp(clickedGridItem.packageName)
        }
    }

    fun showHomeIconMenu(x: Float, y: Float, gridItem: HomeScreenGridItem, isOnAllAppsFragment: Boolean) {
        mLongPressedIcon = gridItem
        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = y
        mOpenPopupMenu = handleGridItemPopupMenu(home_screen_popup_menu_anchor, gridItem, isOnAllAppsFragment)
    }

    private fun showMainLongPressMenu(x: Float, y: Float) {
        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = y - resources.getDimension(R.dimen.home_long_press_anchor_offset_y)
        val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
        PopupMenu(contextTheme, home_screen_popup_menu_anchor, Gravity.TOP or Gravity.END).apply {
            inflate(R.menu.menu_home_screen)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.widgets -> showWidgetsFragment()
                }
                true
            }
            show()
        }
    }

    private fun handleGridItemPopupMenu(anchorView: View, gridItem: HomeScreenGridItem, isOnAllAppsFragment: Boolean): PopupMenu {
        val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
        return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
            inflate(R.menu.menu_app_icon)
            menu.findItem(R.id.remove).isVisible = !isOnAllAppsFragment
            setOnMenuItemClickListener { item ->
                (all_apps_fragment as AllAppsFragment).ignoreTouches = false
                when (item.itemId) {
                    R.id.app_info -> launchAppInfo(gridItem.packageName)
                    R.id.remove -> home_screen_grid.removeAppIcon(gridItem.id!!)
                    R.id.uninstall -> uninstallApp(gridItem.packageName)
                }
                true
            }

            setOnDismissListener {
                mOpenPopupMenu = null
                (all_apps_fragment as AllAppsFragment).ignoreTouches = false
            }

            show()
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
            // ignore fling events just after releasing an icon from dragging
            if (System.currentTimeMillis() - mLastUpEvent < 500L) {
                return true
            }

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

    @SuppressLint("WrongConstant")
    override fun onFlingDown() {
        mIgnoreUpEvent = true
        if (all_apps_fragment.y != mScreenHeight.toFloat()) {
            hideFragment(all_apps_fragment)
        } else {
            try {
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar"))
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("WrongConstant")
    fun getAllAppLaunchers(): ArrayList<AppLauncher> {
        val allApps = ArrayList<AppLauncher>()
        val allPackageNames = ArrayList<String>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val label = info.loadLabel(packageManager).toString()
            val packageName = componentInfo.packageName
            val drawable = getDrawableForPackageName(packageName) ?: continue
            val placeholderColor = calculateAverageColor(drawable.toBitmap())

            allPackageNames.add(packageName)
            allApps.add(AppLauncher(null, label, packageName, 0, placeholderColor, drawable))
        }

        val launchers = allApps.distinctBy { it.packageName } as ArrayList<AppLauncher>
        launchersDB.insertAll(launchers)
        return launchers
    }

    private fun getDefaultAppPackages(appLaunchers: ArrayList<AppLauncher>) {
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val defaultDialerPackage = (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.apply {
                val dialerIcon = HomeScreenGridItem(null, 0, ROW_COUNT - 1, 1, ROW_COUNT, defaultDialerPackage, title, null)
                homeScreenGridItems.add(dialerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val defaultSMSMessengerPackage = Telephony.Sms.getDefaultSmsPackage(this)
            appLaunchers.firstOrNull { it.packageName == defaultSMSMessengerPackage }?.apply {
                val SMSMessengerIcon =
                    HomeScreenGridItem(null, 1, ROW_COUNT - 1, 2, ROW_COUNT, defaultSMSMessengerPackage, title, null)
                homeScreenGridItems.add(SMSMessengerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val browserIntent = Intent("android.intent.action.VIEW", Uri.parse("http://"))
            val resolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultBrowserPackage }?.apply {
                val browserIcon = HomeScreenGridItem(null, 2, ROW_COUNT - 1, 3, ROW_COUNT, defaultBrowserPackage, title, null)
                homeScreenGridItems.add(browserIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val potentialStores = arrayListOf("com.android.vending", "org.fdroid.fdroid", "com.aurora.store")
            val storePackage = potentialStores.firstOrNull { isPackageInstalled(it) && appLaunchers.map { it.packageName }.contains(it) }
            if (storePackage != null) {
                appLaunchers.firstOrNull { it.packageName == storePackage }?.apply {
                    val storeIcon = HomeScreenGridItem(null, 3, ROW_COUNT - 1, 4, ROW_COUNT, storePackage, title, null)
                    homeScreenGridItems.add(storeIcon)
                }
            }
        } catch (e: Exception) {
        }

        try {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE")
            val resolveInfo = packageManager.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultCameraPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultCameraPackage }?.apply {
                val cameraIcon = HomeScreenGridItem(null, 4, ROW_COUNT - 1, 5, ROW_COUNT, defaultCameraPackage, title, null)
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (e: Exception) {
        }

        homeScreenGridItemsDB.insertAll(homeScreenGridItems)
    }

    // taken from https://gist.github.com/maxjvh/a6ab15cbba9c82a5065d
    private fun calculateAverageColor(bitmap: Bitmap): Int {
        var red = 0
        var green = 0
        var blue = 0
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
            n++
            i += 1
        }

        return Color.rgb(red / n, green / n, blue / n)
    }
}
