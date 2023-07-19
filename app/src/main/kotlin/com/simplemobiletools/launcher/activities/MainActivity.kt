package com.simplemobiletools.launcher.activities

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isPiePlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.dialogs.RenameItemDialog
import com.simplemobiletools.launcher.extensions.*
import com.simplemobiletools.launcher.fragments.AllAppsFragment
import com.simplemobiletools.launcher.fragments.MyFragment
import com.simplemobiletools.launcher.fragments.WidgetsFragment
import com.simplemobiletools.launcher.helpers.*
import com.simplemobiletools.launcher.interfaces.FlingListener
import com.simplemobiletools.launcher.models.AppLauncher
import com.simplemobiletools.launcher.models.HiddenIcon
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.all_apps_fragment.view.*
import kotlinx.android.synthetic.main.widgets_fragment.view.*
import kotlin.math.abs

class MainActivity : SimpleActivity(), FlingListener {
    private val ANIMATION_DURATION = 150L

    private var mTouchDownX = -1
    private var mTouchDownY = -1
    private var mAllAppsFragmentY = 0
    private var mWidgetsFragmentY = 0
    private var mScreenHeight = 0
    private var mMoveGestureThreshold = 0
    private var mIgnoreUpEvent = false
    private var mIgnoreMoveEvents = false
    private var mLongPressedIcon: HomeScreenGridItem? = null
    private var mOpenPopupMenu: PopupMenu? = null
    private var mCachedLaunchers = ArrayList<AppLauncher>()
    private var mLastTouchCoords = Pair(-1f, -1f)
    private var mActionOnCanBindWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnWidgetConfiguredWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnAddShortcut: ((label: String, icon: Bitmap?, intent: String) -> Unit)? = null

    private lateinit var mDetector: GestureDetectorCompat

    companion object {
        private var mLastUpEvent = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        mDetector = GestureDetectorCompat(this, MyGestureListener(this))

        if (isRPlus()) {
            window.setDecorFitsSystemWindows(false)
        }

        mScreenHeight = realScreenSize.y
        mAllAppsFragmentY = mScreenHeight
        mWidgetsFragmentY = mScreenHeight
        mMoveGestureThreshold = resources.getDimension(R.dimen.move_gesture_threshold).toInt()

        arrayOf(all_apps_fragment as MyFragment, widgets_fragment as MyFragment).forEach { fragment ->
            fragment.setupFragment(this)
            fragment.y = mScreenHeight.toFloat()
            fragment.beVisible()
        }

        handleIntentAction(intent)

        home_screen_grid.itemClickListener = {
            performItemClick(it)
        }

        home_screen_grid.itemLongClickListener = {
            performItemLongClick(home_screen_grid.getClickableRect(it).left.toFloat(), it)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleIntentAction(intent)
        }
    }

    private fun handleIntentAction(intent: Intent) {
        if (intent.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            val launcherApps = applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val item = launcherApps.getPinItemRequest(intent)
            if (item.shortcutInfo == null) {
                return
            }

            ensureBackgroundThread {
                val shortcutId = item.shortcutInfo?.id!!
                val label = item.shortcutInfo?.shortLabel?.toString() ?: item.shortcutInfo?.longLabel?.toString() ?: ""
                val icon = launcherApps.getShortcutIconDrawable(item.shortcutInfo!!, resources.displayMetrics.densityDpi)
                val (page, rect) = findFirstEmptyCell()
                val gridItem = HomeScreenGridItem(
                    null,
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    page,
                    item.shortcutInfo!!.`package`,
                    "",
                    label,
                    ITEM_TYPE_SHORTCUT,
                    "",
                    -1,
                    "",
                    shortcutId,
                    icon.toBitmap(),
                    false,
                    icon
                )

                runOnUiThread {
                    home_screen_grid.skipToPage(page)
                }
                // delay showing the shortcut both to let the user see adding it in realtime and hackily avoid concurrent modification exception at HomeScreenGrid
                Thread.sleep(2000)

                try {
                    item.accept()
                    home_screen_grid.storeAndShowGridItem(gridItem)
                } catch (ignored: IllegalStateException) {
                }
            }
        }
    }

    private fun findFirstEmptyCell(): Pair<Int, Rect> {
        val gridItems = homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
        val maxPage = gridItems.map { it.page }.max()
        val occupiedCells = ArrayList<Triple<Int, Int, Int>>()
        gridItems.forEach { item ->
            for (xCell in item.left..item.right) {
                for (yCell in item.top..item.bottom) {
                    occupiedCells.add(Triple(item.page, xCell, yCell))
                }
            }
        }

        for (page in 0 until maxPage) {
            for (checkedYCell in 0 until config.homeColumnCount) {
                for (checkedXCell in 0 until config.homeRowCount - 1) {
                    val wantedCell = Triple(page, checkedXCell, checkedYCell)
                    if (!occupiedCells.contains(wantedCell)) {
                        return Pair(page, Rect(wantedCell.second, wantedCell.third, wantedCell.second, wantedCell.third))
                    }
                }
            }
        }

        return Pair(maxPage + 1, Rect(0, 0, 0, 0))
    }

    override fun onStart() {
        super.onStart()
        home_screen_grid.appWidgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()
        updateStatusbarColor(Color.TRANSPARENT)

        main_holder.onGlobalLayout {
            if (isPiePlus()) {
                val addTopPadding = main_holder.rootWindowInsets?.displayCutout != null
                (all_apps_fragment as AllAppsFragment).setupViews(addTopPadding)
                (widgets_fragment as WidgetsFragment).setupViews(addTopPadding)
            }
        }

        ensureBackgroundThread {
            if (mCachedLaunchers.isEmpty()) {
                val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
                    it.getIconIdentifier()
                }

                mCachedLaunchers = launchersDB.getAppLaunchers().filter {
                    val showIcon = !hiddenIcons.contains(it.getLauncherIdentifier())
                    if (!showIcon) {
                        try {
                            launchersDB.deleteById(it.id!!)
                        } catch (ignored: Exception) {
                        }
                    }
                    showIcon
                }.toMutableList() as ArrayList<AppLauncher>

                (all_apps_fragment as AllAppsFragment).gotLaunchers(mCachedLaunchers)
            }

            refetchLaunchers()
        }

        // avoid showing fully colored navigation bars
        if (window.navigationBarColor != resources.getColor(R.color.semitransparent_navigation)) {
            window.navigationBarColor = Color.TRANSPARENT
        }

        home_screen_grid?.resizeGrid(
            newRowCount = config.homeRowCount,
            newColumnCount = config.homeColumnCount
        )
        (all_apps_fragment as? AllAppsFragment)?.onResume()
    }

    override fun onStop() {
        super.onStop()
        home_screen_grid?.appWidgetHost?.stopListening()
    }

    override fun onBackPressed() {
        if (isAllAppsFragmentExpanded()) {
            if ((all_apps_fragment as? AllAppsFragment)?.onBackPressed() == false) {
                hideFragment(all_apps_fragment)
            }
        } else if (isWidgetsFragmentExpanded()) {
            hideFragment(widgets_fragment)
        } else if (home_screen_grid.resize_frame.isVisible) {
            home_screen_grid.hideResizeLines()
        } else {
            // this is a home launcher app, avoid glitching by pressing Back
            //super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        when (requestCode) {
            UNINSTALL_APP_REQUEST_CODE -> {
                ensureBackgroundThread {
                    refetchLaunchers()
                }
            }

            REQUEST_ALLOW_BINDING_WIDGET -> mActionOnCanBindWidget?.invoke(resultCode == Activity.RESULT_OK)
            REQUEST_CONFIGURE_WIDGET -> mActionOnWidgetConfiguredWidget?.invoke(resultCode == Activity.RESULT_OK)
            REQUEST_CREATE_SHORTCUT -> {
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    val launchIntent = resultData.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                    val label = resultData.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: ""
                    val icon = resultData.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap
                    mActionOnAddShortcut?.invoke(label, icon, launchIntent?.toUri(0).toString())
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (all_apps_fragment as? AllAppsFragment)?.onConfigurationChanged()
        (widgets_fragment as? WidgetsFragment)?.onConfigurationChanged()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        if (mLongPressedIcon != null && event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            mLastUpEvent = System.currentTimeMillis()
        }

        try {
            mDetector.onTouchEvent(event)
        } catch (ignored: Exception) {
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x.toInt()
                mTouchDownY = event.y.toInt()
                mAllAppsFragmentY = all_apps_fragment.y.toInt()
                mWidgetsFragmentY = widgets_fragment.y.toInt()
                mIgnoreUpEvent = false
            }

            MotionEvent.ACTION_MOVE -> {
                // if the initial gesture was handled by some other view, fix the Down values
                val hasFingerMoved = if (mTouchDownX == -1 || mTouchDownY == -1) {
                    mTouchDownX = event.x.toInt()
                    mTouchDownY = event.y.toInt()
                    false
                } else {
                    hasFingerMoved(event)
                }

                if (mLongPressedIcon != null && mOpenPopupMenu != null && hasFingerMoved) {
                    mOpenPopupMenu?.dismiss()
                    mOpenPopupMenu = null
                    home_screen_grid.itemDraggingStarted(mLongPressedIcon!!)
                    hideFragment(all_apps_fragment)
                }

                if (mLongPressedIcon != null && hasFingerMoved) {
                    home_screen_grid.draggedItemMoved(event.x.toInt(), event.y.toInt())
                }

                if (mTouchDownY != -1 && !mIgnoreMoveEvents) {
                    val diffY = mTouchDownY - event.y

                    if (isWidgetsFragmentExpanded()) {
                        val newY = mWidgetsFragmentY - diffY
                        widgets_fragment.y = Math.min(Math.max(0f, newY), mScreenHeight.toFloat())
                    } else if (mLongPressedIcon == null) {
                        val newY = mAllAppsFragmentY - diffY
                        all_apps_fragment.y = Math.min(Math.max(0f, newY), mScreenHeight.toFloat())
                    }
                }

                mLastTouchCoords = Pair(event.x, event.y)
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                mTouchDownX = -1
                mTouchDownY = -1
                mIgnoreMoveEvents = false
                mLongPressedIcon = null
                mLastTouchCoords = Pair(-1f, -1f)
                resetFragmentTouches()
                home_screen_grid.itemDraggingStopped()

                if (!mIgnoreUpEvent) {
                    if (all_apps_fragment.y < mScreenHeight * 0.5) {
                        showFragment(all_apps_fragment)
                    } else if (isAllAppsFragmentExpanded()) {
                        hideFragment(all_apps_fragment)
                    }

                    if (widgets_fragment.y < mScreenHeight * 0.5) {
                        showFragment(widgets_fragment)
                    } else if (isWidgetsFragmentExpanded()) {
                        hideFragment(widgets_fragment)
                    }
                }
            }
        }

        return true
    }

    // some devices ACTION_MOVE keeps triggering for the whole long press duration, but we are interested in real moves only, when coords change
    private fun hasFingerMoved(event: MotionEvent) = mTouchDownX != -1 && mTouchDownY != -1 &&
        (Math.abs(mTouchDownX - event.x) > mMoveGestureThreshold) || (Math.abs(mTouchDownY - event.y) > mMoveGestureThreshold)

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

    fun isAllAppsFragmentExpanded() = all_apps_fragment.y != mScreenHeight.toFloat()

    private fun isWidgetsFragmentExpanded() = widgets_fragment.y != mScreenHeight.toFloat()

    fun startHandlingTouches(touchDownY: Int) {
        mLongPressedIcon = null
        mTouchDownY = touchDownY
        mAllAppsFragmentY = all_apps_fragment.y.toInt()
        mWidgetsFragmentY = widgets_fragment.y.toInt()
        mIgnoreUpEvent = false
    }

    private fun showFragment(fragment: View) {
        ObjectAnimator.ofFloat(fragment, "y", 0f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = resources.getColor(R.color.semitransparent_navigation)
        home_screen_grid.fragmentExpanded()
        home_screen_grid.hideResizeLines()
        fragment.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
    }

    private fun hideFragment(fragment: View) {
        ObjectAnimator.ofFloat(fragment, "y", mScreenHeight.toFloat()).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            start()
        }

        window.navigationBarColor = Color.TRANSPARENT
        home_screen_grid.fragmentCollapsed()
        Handler().postDelayed({
            if (fragment is AllAppsFragment) {
                fragment.all_apps_grid.scrollToPosition(0)
                fragment.touchDownY = -1
            } else if (fragment is WidgetsFragment) {
                fragment.widgets_list.scrollToPosition(0)
                fragment.touchDownY = -1
            }
        }, ANIMATION_DURATION)
    }

    fun homeScreenLongPressed(eventX: Float, eventY: Float) {
        if (isAllAppsFragmentExpanded()) {
            return
        }

        val (x, y) = home_screen_grid.intoViewSpaceCoords(eventX, eventY)
        mIgnoreMoveEvents = true
        val clickedGridItem = home_screen_grid.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemLongClick(x, clickedGridItem)
            return
        }

        main_holder.performHapticFeedback()
        showMainLongPressMenu(x, y)
    }

    fun homeScreenClicked(eventX: Float, eventY: Float) {
        home_screen_grid.hideResizeLines()
        val (x, y) = home_screen_grid.intoViewSpaceCoords(eventX, eventY)
        val clickedGridItem = home_screen_grid.isClickingGridItem(x.toInt(), y.toInt())
        if (clickedGridItem != null) {
            performItemClick(clickedGridItem)
        }
    }

    private fun performItemClick(clickedGridItem: HomeScreenGridItem) {
        if (clickedGridItem.type == ITEM_TYPE_ICON) {
            launchApp(clickedGridItem.packageName, clickedGridItem.activityName)
        } else if (clickedGridItem.type == ITEM_TYPE_SHORTCUT) {
            if (clickedGridItem.intent.isNotEmpty()) {
                launchShortcutIntent(clickedGridItem)
            } else {
                // launch pinned shortcuts
                val id = clickedGridItem.shortcutId
                val packageName = clickedGridItem.packageName
                val userHandle = android.os.Process.myUserHandle()
                val shortcutBounds = home_screen_grid.getClickableRect(clickedGridItem)
                val launcherApps = applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.startShortcut(packageName, id, shortcutBounds, null, userHandle)
            }
        }
    }

    private fun performItemLongClick(x: Float, clickedGridItem: HomeScreenGridItem) {
        if (clickedGridItem.type == ITEM_TYPE_ICON || clickedGridItem.type == ITEM_TYPE_SHORTCUT) {
            main_holder.performHapticFeedback()
        }

        val anchorY = home_screen_grid.sideMargins.top + (clickedGridItem.top * home_screen_grid.cellHeight.toFloat())
        showHomeIconMenu(x, anchorY, clickedGridItem, false)
    }

    fun showHomeIconMenu(x: Float, y: Float, gridItem: HomeScreenGridItem, isOnAllAppsFragment: Boolean) {
        home_screen_grid.hideResizeLines()
        mLongPressedIcon = gridItem
        val anchorY = if (isOnAllAppsFragment || gridItem.type == ITEM_TYPE_WIDGET) {
            y
        } else if (gridItem.top == config.homeRowCount - 1) {
            home_screen_grid.sideMargins.top + (gridItem.top * home_screen_grid.cellHeight.toFloat())
        } else {
            (gridItem.top * home_screen_grid.cellHeight.toFloat())
        }

        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = anchorY

        if (mOpenPopupMenu == null) {
            mOpenPopupMenu = handleGridItemPopupMenu(home_screen_popup_menu_anchor, gridItem, isOnAllAppsFragment)
        }
    }

    fun widgetLongPressedOnList(gridItem: HomeScreenGridItem) {
        mLongPressedIcon = gridItem
        hideFragment(widgets_fragment)
        home_screen_grid.itemDraggingStarted(mLongPressedIcon!!)
    }

    private fun showMainLongPressMenu(x: Float, y: Float) {
        home_screen_grid.hideResizeLines()
        home_screen_popup_menu_anchor.x = x
        home_screen_popup_menu_anchor.y = y - resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * 2
        val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
        PopupMenu(contextTheme, home_screen_popup_menu_anchor, Gravity.TOP or Gravity.END).apply {
            inflate(R.menu.menu_home_screen)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.widgets -> showWidgetsFragment()
                    R.id.wallpapers -> launchWallpapersIntent()
                }
                true
            }
            show()
        }
    }

    private fun handleGridItemPopupMenu(anchorView: View, gridItem: HomeScreenGridItem, isOnAllAppsFragment: Boolean): PopupMenu {
        var visibleMenuButtons = 6
        visibleMenuButtons -= when (gridItem.type) {
            ITEM_TYPE_ICON -> 1
            ITEM_TYPE_WIDGET -> 3
            else -> 4
        }

        if (isOnAllAppsFragment) {
            visibleMenuButtons--
        }

        if (gridItem.type != ITEM_TYPE_WIDGET) {
            visibleMenuButtons--
        }

        val yOffset = resources.getDimension(R.dimen.long_press_anchor_button_offset_y) * (visibleMenuButtons - 1)
        anchorView.y -= yOffset

        val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
        return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
            if (isQPlus()) {
                setForceShowIcon(true)
            }

            inflate(R.menu.menu_app_icon)
            menu.findItem(R.id.rename).isVisible = gridItem.type == ITEM_TYPE_ICON && !isOnAllAppsFragment
            menu.findItem(R.id.hide_icon).isVisible = gridItem.type == ITEM_TYPE_ICON && isOnAllAppsFragment
            menu.findItem(R.id.resize).isVisible = gridItem.type == ITEM_TYPE_WIDGET
            menu.findItem(R.id.app_info).isVisible = gridItem.type == ITEM_TYPE_ICON
            menu.findItem(R.id.uninstall).isVisible = gridItem.type == ITEM_TYPE_ICON
            menu.findItem(R.id.remove).isVisible = !isOnAllAppsFragment
            setOnMenuItemClickListener { item ->
                resetFragmentTouches()
                when (item.itemId) {
                    R.id.hide_icon -> hideIcon(gridItem)
                    R.id.rename -> renameItem(gridItem)
                    R.id.resize -> home_screen_grid.widgetLongPressed(gridItem)
                    R.id.app_info -> launchAppInfo(gridItem.packageName)
                    R.id.remove -> home_screen_grid.removeAppIcon(gridItem)
                    R.id.uninstall -> uninstallApp(gridItem.packageName)
                }
                true
            }

            setOnDismissListener {
                mOpenPopupMenu = null
                resetFragmentTouches()
            }

            show()
        }
    }

    private fun resetFragmentTouches() {
        (widgets_fragment as WidgetsFragment).apply {
            touchDownY = -1
            ignoreTouches = false
        }

        (all_apps_fragment as AllAppsFragment).apply {
            touchDownY = -1
            ignoreTouches = false
        }
    }

    private fun showWidgetsFragment() {
        showFragment(widgets_fragment)
    }

    private fun hideIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            val hiddenIcon = HiddenIcon(null, item.packageName, item.activityName, item.title, null)
            hiddenIconsDB.insert(hiddenIcon)

            runOnUiThread {
                (all_apps_fragment as AllAppsFragment).hideIcon(item)
            }
        }
    }

    private fun renameItem(homeScreenGridItem: HomeScreenGridItem) {
        RenameItemDialog(this, homeScreenGridItem) {
            home_screen_grid.fetchGridItems()
        }
    }

    private fun launchWallpapersIntent() {
        try {
            Intent(Intent.ACTION_SET_WALLPAPER).apply {
                startActivity(this)
            }
        } catch (e: ActivityNotFoundException) {
            toast(R.string.no_app_found)
        } catch (e: Exception) {
            showErrorToast(e)
        }
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

            if (abs(velocityY) > abs(velocityX)) {
                if (velocityY > 0) {
                    flingListener.onFlingDown()
                } else {
                    flingListener.onFlingUp()
                }
            } else if (abs(velocityX) > abs(velocityY)) {
                if (velocityX > 0) {
                    flingListener.onFlingRight()
                } else {
                    flingListener.onFlingLeft()
                }
            }

            return true
        }

        override fun onLongPress(event: MotionEvent) {
            (flingListener as MainActivity).homeScreenLongPressed(event.x, event.y)
        }
    }

    override fun onFlingUp() {
        if (!isWidgetsFragmentExpanded()) {
            mIgnoreUpEvent = true
            showFragment(all_apps_fragment)
        }
    }

    @SuppressLint("WrongConstant")
    override fun onFlingDown() {
        mIgnoreUpEvent = true
        if (isAllAppsFragmentExpanded()) {
            hideFragment(all_apps_fragment)
        } else if (isWidgetsFragmentExpanded()) {
            hideFragment(widgets_fragment)
        } else {
            try {
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar"))
            } catch (e: Exception) {
            }
        }
    }

    override fun onFlingRight() {
        home_screen_grid.prevPage(redraw = true)
    }

    override fun onFlingLeft() {
        home_screen_grid.nextPage(redraw = true)
    }

    @SuppressLint("WrongConstant")
    fun getAllAppLaunchers(): ArrayList<AppLauncher> {
        val hiddenIcons = hiddenIconsDB.getHiddenIcons().map {
            it.getIconIdentifier()
        }

        val allApps = ArrayList<AppLauncher>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val simpleLauncher = applicationContext.packageName
        val microG = "com.google.android.gms"
        val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
        for (info in list) {
            val componentInfo = info.activityInfo.applicationInfo
            val packageName = componentInfo.packageName
            if (packageName == simpleLauncher || packageName == microG) {
                continue
            }

            val activityName = info.activityInfo.name
            if (hiddenIcons.contains("$packageName/$activityName")) {
                continue
            }

            val label = info.loadLabel(packageManager).toString()
            val drawable = info.loadIcon(packageManager) ?: getDrawableForPackageName(packageName) ?: continue
            val placeholderColor = calculateAverageColor(drawable.toBitmap())
            allApps.add(AppLauncher(null, label, packageName, activityName, 0, placeholderColor, drawable))
        }

        // add Simple Launchers settings as an app
        val drawable = getDrawableForPackageName(packageName)
        val placeholderColor = calculateAverageColor(drawable!!.toBitmap())
        val launcherSettings = AppLauncher(null, getString(R.string.launcher_settings), packageName, "", 0, placeholderColor, drawable)
        allApps.add(launcherSettings)
        launchersDB.insertAll(allApps)
        return allApps
    }

    private fun getDefaultAppPackages(appLaunchers: ArrayList<AppLauncher>) {
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val defaultDialerPackage = (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.apply {
                val dialerIcon =
                    HomeScreenGridItem(null, 0, config.homeRowCount - 1, 0, config.homeRowCount - 1, 0, defaultDialerPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null, true)
                homeScreenGridItems.add(dialerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val defaultSMSMessengerPackage = Telephony.Sms.getDefaultSmsPackage(this)
            appLaunchers.firstOrNull { it.packageName == defaultSMSMessengerPackage }?.apply {
                val SMSMessengerIcon =
                    HomeScreenGridItem(null, 1, config.homeRowCount - 1, 1, config.homeRowCount - 1, 0, defaultSMSMessengerPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null, true)
                homeScreenGridItems.add(SMSMessengerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val browserIntent = Intent("android.intent.action.VIEW", Uri.parse("http://"))
            val resolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultBrowserPackage = resolveInfo!!.activityInfo.packageName
            appLaunchers.firstOrNull { it.packageName == defaultBrowserPackage }?.apply {
                val browserIcon =
                    HomeScreenGridItem(null, 2, config.homeRowCount - 1, 2, config.homeRowCount - 1, 0, defaultBrowserPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null, true)
                homeScreenGridItems.add(browserIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val potentialStores = arrayListOf("com.android.vending", "org.fdroid.fdroid", "com.aurora.store")
            val storePackage = potentialStores.firstOrNull { isPackageInstalled(it) && appLaunchers.map { it.packageName }.contains(it) }
            if (storePackage != null) {
                appLaunchers.firstOrNull { it.packageName == storePackage }?.apply {
                    val storeIcon = HomeScreenGridItem(null, 3, config.homeRowCount - 1, 3, config.homeRowCount - 1, 0, storePackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null, true)
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
                val cameraIcon =
                    HomeScreenGridItem(null, 4, config.homeRowCount - 1, 4, config.homeRowCount - 1, 0, defaultCameraPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null, true)
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (e: Exception) {
        }

        homeScreenGridItemsDB.insertAll(homeScreenGridItems)
    }

    fun handleWidgetBinding(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo,
        callback: (canBind: Boolean) -> Unit
    ) {
        mActionOnCanBindWidget = null
        val canCreateWidget = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetInfo.provider)
        if (canCreateWidget) {
            callback(true)
        } else {
            mActionOnCanBindWidget = callback
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetInfo.provider)
                startActivityForResult(this, REQUEST_ALLOW_BINDING_WIDGET)
            }
        }
    }

    fun handleWidgetConfigureScreen(appWidgetHost: AppWidgetHost, appWidgetId: Int, callback: (canBind: Boolean) -> Unit) {
        mActionOnWidgetConfiguredWidget = callback
        appWidgetHost.startAppWidgetConfigureActivityForResult(
            this,
            appWidgetId,
            0,
            REQUEST_CONFIGURE_WIDGET,
            null
        )
    }

    fun handleShorcutCreation(activityInfo: ActivityInfo, callback: (label: String, icon: Bitmap?, intent: String) -> Unit) {
        mActionOnAddShortcut = callback
        val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
        Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            component = componentName
            startActivityForResult(this, REQUEST_CREATE_SHORTCUT)
        }
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
