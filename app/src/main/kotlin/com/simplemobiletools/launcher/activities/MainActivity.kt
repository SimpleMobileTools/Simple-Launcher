package com.simplemobiletools.launcher.activities

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
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.launcher.BuildConfig
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.adapters.DockAdapter
import com.simplemobiletools.launcher.adapters.HomeScreenPagerAdapter
import com.simplemobiletools.launcher.extensions.*
import com.simplemobiletools.launcher.fragments.*
import com.simplemobiletools.launcher.helpers.*
import com.simplemobiletools.launcher.helpers.BottomSheetBehavior.STATE_COLLAPSED
import com.simplemobiletools.launcher.helpers.BottomSheetBehavior.STATE_EXPANDED
import com.simplemobiletools.launcher.helpers.BottomSheetBehavior.from
import com.simplemobiletools.launcher.models.*
import com.simplemobiletools.launcher.views.MyAppWidgetResizeFrame
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : SimpleActivity(), HomeScreenFragment.HomeScreenActionsListener /*HomeViewPager.OnPagerGestureListener*/ {
    private val ANIMATION_DURATION = 150L
    private var mLongPressedIcon: HomeScreenGridItem? = null
    private var mCachedLaunchers = ArrayList<AppLauncher>()
    private var mActionOnCanBindWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnWidgetConfiguredWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnAddShortcut: ((label: String, icon: Bitmap?, intent: String) -> Unit)? = null

    private lateinit var mDetector: GestureDetectorCompat
    private var homeScreenPagerAdapter: HomeScreenPagerAdapter? = null

    private lateinit var dockRecyclerView: RecyclerView
    private lateinit var dockAdapter: DockAdapter
    private var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null
    private var widgetsBottomSheet: BottomSheetDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (isRPlus()) {
            window.setDecorFitsSystemWindows(false)
        }

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
                val rect = findFirstEmptyCell() ?: return@ensureBackgroundThread
                val gridItem = HomeScreenGridItem(
                    null,
                    0, // Use Id of current page or first page with enough space
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.bottom,
                    item.shortcutInfo!!.`package`,
                    "",
                    label,
                    ITEM_TYPE_SHORTCUT,
                    "",
                    -1,
                    "",
                    shortcutId,
                    icon.toBitmap(),
                    icon,
                )

                // delay showing the shortcut both to let the user see adding it in realtime and hackily avoid concurrent modification exception at HomeScreenGrid
                Thread.sleep(2000)

                try {
                    item.accept()
                    getHomeFragment()?.storeAndShowGridItem(gridItem)
                } catch (ignored: IllegalStateException) {
                }
            }
        }
        lifecycleScope.launch {
            setupHomeScreenViewPager()
            val initialDockItems = withContext(Dispatchers.IO) {
                getDefaultAppPackages(launcherHelper.getAllAppLaunchers(), -1)
            }
            setupDock(initialDockItems)
        }
        bottomSheetBehavior = from(bottomSheetView)
        bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if(newState == STATE_EXPANDED){
                    getHomeFragment()?.homeScreenGrid?.fragmentExpanded()
                    getHomeFragment()?.homeScreenGrid?.hideResizeLines()
                }else if(newState == STATE_COLLAPSED){
                    getHomeFragment()?.homeScreenGrid?.fragmentCollapsed()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }

        })

        widgetsBottomSheet = WidgetsBottomSheetFragment()
    }

    private fun getHomeFragment() = homeScreenPagerAdapter?.getFragmentAt(home_screen_view_pager.currentItem)
    private fun findFirstEmptyCell(): Rect? {
        val gridItems = homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
        val occupiedCells = ArrayList<Pair<Int, Int>>()
        gridItems.forEach { item ->
            for (xCell in item.left..item.right) {
                for (yCell in item.top..item.bottom) {
                    occupiedCells.add(Pair(xCell, yCell))
                }
            }
        }

        for (checkedYCell in 0 until COLUMN_COUNT) {
            for (checkedXCell in 0 until ROW_COUNT - 1) {
                val wantedCell = Pair(checkedXCell, checkedYCell)
                if (!occupiedCells.contains(wantedCell)) {
                    return Rect(wantedCell.first, wantedCell.second, wantedCell.first, wantedCell.second)
                }
            }
        }

        return null
    }

    override fun onResume() {
        super.onResume()
        updateStatusbarColor(Color.TRANSPARENT)
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior?.state == STATE_EXPANDED) {
            closeAllAppsBottomSheet()
        }
        if (getHomeFragment()?.homeScreenGrid?.findViewById<MyAppWidgetResizeFrame>(R.id.resize_frame)?.isVisible() == true) {
            getHomeFragment()?.homeScreenGrid?.hideResizeLines()
        } else {
            // this is a home launcher app, avoid glitching by pressing Back
            // super.onBackPressed()
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
//        allAppsBottomSheet?.onConfigurationChanged()
        (widgets_fragment as? WidgetsFragment)?.onConfigurationChanged()
    }

    private fun refetchLaunchers() {
        val launchers = launcherHelper.getAllAppLaunchers()
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
            lifecycleScope.launch {
                onUpdateAdapter()
            }
        }

        mCachedLaunchers = launchers

        if (!config.wasHomeScreenInit) {
            ensureBackgroundThread {
                val defaultPage = HomeScreenPage(id = 0, position = 0)
                lifecycleScope.launch(Dispatchers.IO) {
                    homeScreenPagesDB.insert(defaultPage)

//                   // Insert Dock Items
//                   val defaultGridItems = getDefaultAppPackages(launchers, defaultPage.id ?: 0)
//                   homeScreenGridItemsDB.insertAll(defaultGridItems)
                }
                config.wasHomeScreenInit = true
                lifecycleScope.launch {
                    setupHomeScreenViewPager()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private suspend fun setupHomeScreenViewPager() {
        val pages = withContext(Dispatchers.IO) { homeScreenPagesDB.getPagesWithGridItems() } as ArrayList
        pages.add(PageWithGridItems(page = HomeScreenPage(id = -1L, pages.size), gridItems = emptyList(), isAddNewPageIndicator = true))
        Log.i("HOMEVP", "Pages:$pages")
        homeScreenPagerAdapter = createHomeScreenPagerAdapter(pages)
        home_screen_view_pager.adapter = homeScreenPagerAdapter
        Log.d("SM-LAUNCHER", "adapter page count: ${homeScreenPagerAdapter?.count}")
    }

    private fun createHomeScreenPagerAdapter(pages: ArrayList<PageWithGridItems>): HomeScreenPagerAdapter {
        return HomeScreenPagerAdapter(supportFragmentManager).apply {
            setHomePages(pages)
        }
    }

    override fun onUpdateAdapter() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pages = homeScreenPagesDB.getPagesWithGridItems() as ArrayList
            pages.add(PageWithGridItems(page = HomeScreenPage(id = -1L, position = pages.size), gridItems = emptyList(), isAddNewPageIndicator = true))
            launch(Dispatchers.Main) { homeScreenPagerAdapter?.updateItems(pages) }
        }
    }

    fun startHandlingTouches(touchDownY: Int) {
//        mLongPressedIcon = null
//        mTouchDownY = touchDownY
//        mIgnoreUpEvent = false
    }
    fun widgetLongPressedOnList(gridItem: HomeScreenGridItem) {
        mLongPressedIcon = gridItem
        getHomeFragment()?.homeScreenGrid?.itemDraggingStarted(mLongPressedIcon!!)
    }
    override fun launchWallpapersIntent() {
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
    override fun showWidgetsFragment() {
        widgetsBottomSheet?.show(supportFragmentManager,"Widgets BS")
    }
    private fun showAllAppsBottomSheet() {
        bottomSheetBehavior?.state = STATE_EXPANDED
    }
    fun closeAllAppsBottomSheet() {
        bottomSheetBehavior?.state = STATE_COLLAPSED
    }
    override fun onFlingUp() {
        showAllAppsBottomSheet()
    }
    @SuppressLint("WrongConstant")
    override fun onFlingDown() {
//        bottomSheetBehavior?.state = STATE_COLLAPSED
    }
    private fun getDefaultAppPackages(appLaunchers: ArrayList<AppLauncher>, pageId: Long = 0): ArrayList<HomeScreenGridItem> {
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val defaultDialerPackage = (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.apply {
                val dialerIcon =
                    HomeScreenGridItem(null, pageId, 0, ROW_COUNT - 1, 0, ROW_COUNT - 1, defaultDialerPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(dialerIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val defaultSMSMessengerPackage = Telephony.Sms.getDefaultSmsPackage(this)
            appLaunchers.firstOrNull { it.packageName == defaultSMSMessengerPackage }?.apply {
                val SMSMessengerIcon =
                    HomeScreenGridItem(
                        null,
                        pageId,
                        1,
                        ROW_COUNT - 1,
                        1,
                        ROW_COUNT - 1,
                        defaultSMSMessengerPackage,
                        "",
                        title,
                        ITEM_TYPE_ICON,
                        "",
                        -1,
                        "",
                        "",
                        null,
                    )
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
                    HomeScreenGridItem(null, pageId, 2, ROW_COUNT - 1, 2, ROW_COUNT - 1, defaultBrowserPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(browserIcon)
            }
        } catch (e: Exception) {
        }

        try {
            val potentialStores = arrayListOf("com.android.vending", "org.fdroid.fdroid", "com.aurora.store")
            val storePackage = potentialStores.firstOrNull { isPackageInstalled(it) && appLaunchers.map { it.packageName }.contains(it) }
            if (storePackage != null) {
                appLaunchers.firstOrNull { it.packageName == storePackage }?.apply {
                    val storeIcon =
                        HomeScreenGridItem(null, pageId, 3, ROW_COUNT - 1, 3, ROW_COUNT - 1, storePackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
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
                    HomeScreenGridItem(null, pageId, 4, ROW_COUNT - 1, 4, ROW_COUNT - 1, defaultCameraPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
                homeScreenGridItems.add(cameraIcon)
            }
        } catch (e: Exception) {
        }

        return homeScreenGridItems
    }

    fun handleWidgetBinding(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo,
        callback: (canBind: Boolean) -> Unit,
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
            null,
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

    private fun setupDock(initialDockItems: List<HomeScreenGridItem>) {
        dockRecyclerView = findViewById(R.id.dock_recycler_view)
        dockRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        dockAdapter = DockAdapter(this) { clickedGridItem ->
            // Handle app click
            launchApp(clickedGridItem.packageName, clickedGridItem.activityName)
        }
        dockRecyclerView.adapter = dockAdapter

        // Load and set the initial dock items
        dockAdapter.setItems(initialDockItems)

        // Set up drag and drop for the dock items
        val dockItemTouchHelper = ItemTouchHelper(DockItemTouchHelperCallback(dockAdapter))
        dockItemTouchHelper.attachToRecyclerView(dockRecyclerView)
    }
}
