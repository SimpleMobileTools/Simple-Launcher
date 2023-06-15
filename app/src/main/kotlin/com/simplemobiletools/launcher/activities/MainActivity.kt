package com.simplemobiletools.launcher.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.*
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
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

class MainActivity : SimpleActivity(), HomeScreenFragment.HomeScreenActionsListener{
    private var mCachedLaunchers = ArrayList<AppLauncher>()
    private var mActionOnCanBindWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnWidgetConfiguredWidget: ((granted: Boolean) -> Unit)? = null
    private var mActionOnAddShortcut: ((label: String, icon: Bitmap?, intent: String) -> Unit)? = null

    private var homeScreenPagerAdapter: HomeScreenPagerAdapter? = null

    private lateinit var dockRecyclerView: RecyclerView
    private lateinit var dockAdapter: DockAdapter
    private var appBottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null
    private var widgetsBottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null

    private var currentGridSize = GRID_SIZE_5x5

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (isRPlus()) {
            window.setDecorFitsSystemWindows(false)
        }

        currentGridSize = config.homeGrid

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
        appBottomSheetBehavior = from(appsBottomSheetView)
        appBottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
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

        widgetsBottomSheetBehavior = from(widgetsBottomSheetView)
    }
    private fun adjustGridItems() {
        lifecycleScope.launch {
            val columnCount = config.columnCount
            val rowCount = config.rowCount

            withContext(Dispatchers.IO) {
                val pages = homeScreenPagesDB.getPagesWithGridItems()

                for (pageWithGridItems in pages) {
                    val gridItems = pageWithGridItems.gridItems.toMutableList()

                    // Adjust the position of items to fit the new grid size
                    for (item in gridItems) {
                        val itemWidth = item.right - item.left
                        val itemHeight = item.bottom - item.top
                        // Check if the item exceeds the new grid size
                        if (item.left >= columnCount || item.top >= rowCount || item.right > columnCount || item.bottom > rowCount) {
                            // Move the item to the nearest available cell within the grid
                            val emptyCell = findEmptyCell(columnCount, rowCount, gridItems)
                            if (emptyCell != null) {
                                item.left = emptyCell.first
                                item.top = emptyCell.second
                                item.right = item.left + itemWidth
                                item.bottom = item.top + itemHeight
                            }
                        }
                    }

                    if (gridItems.size > columnCount * rowCount) {
                        val newPageCount = gridItems.size / (columnCount * rowCount) + 1
                        val existingPageCount = pages.size
                        val pageDiff = newPageCount - existingPageCount

                        if (pageDiff > 0) {
                            // Create new pages and assign grid items to them
                            for (i in 0 until pageDiff) {
                                val newPage = HomeScreenPage(/* page properties */)
                                val newPageId = homeScreenPagesDB.insert(newPage)

                                val startIndex = existingPageCount * (columnCount * rowCount) + i * (columnCount * rowCount)
                                val endIndex = minOf(startIndex + (columnCount * rowCount), gridItems.size)

                                for (j in startIndex until endIndex) {
                                    val itemToUpdate = gridItems[j]
                                    itemToUpdate.pageId = newPageId
                                    // Calculate new coordinates for the item within the new page
                                    val itemIndexWithinPage = j - startIndex
                                    val newItemLeft = itemIndexWithinPage % columnCount
                                    val newItemTop = itemIndexWithinPage / columnCount
                                    val newItemRight = newItemLeft + itemToUpdate.right - itemToUpdate.left
                                    val newItemBottom = newItemTop + itemToUpdate.bottom - itemToUpdate.top
                                    itemToUpdate.left = newItemLeft
                                    itemToUpdate.top = newItemTop
                                    itemToUpdate.right = newItemRight
                                    itemToUpdate.bottom = newItemBottom
                                }
                            }
                        }
                    }

                    // Update the grid items in the database
                    homeScreenGridItemsDB.insertAll(gridItems)
                }
            }
            // reload homepage
            setupHomeScreenViewPager()
        }
    }

    private fun findEmptyCell(columnCount: Int, rowCount: Int, gridItems: List<HomeScreenGridItem>): Pair<Int, Int>? {
        val occupiedCells = HashSet<Pair<Int, Int>>()

        for (item in gridItems) {
            for (x in item.left until item.right) {
                for (y in item.top until item.bottom) {
                    occupiedCells.add(Pair(x, y))
                }
            }
        }

        for (x in 0 until columnCount) {
            for (y in 0 until rowCount) {
                val cell = Pair(x, y)
                if (!occupiedCells.contains(cell)) {
                    return cell // Return the first empty cell found
                }
            }
        }

        return null // No empty cell found
    }

    private fun findEmptyCell(
        columnCount: Int,
        rowCount: Int,
        gridItems: List<HomeScreenGridItem>,
        itemWidth: Int,
        itemHeight: Int,
        startCell: Pair<Int, Int>
    ): Pair<Int, Int>? {
        val occupiedCells = Array(columnCount) { BooleanArray(rowCount) }

        for (item in gridItems) {
            for (x in item.left until item.right) {
                for (y in item.top until item.bottom) {
                    occupiedCells[x][y] = true
                }
            }
        }

        val (startX, startY) = startCell

        // Find the first empty cell that can accommodate the item without overlapping existing items
        for (y in startY until rowCount) {
            for (x in if (y == startY) startX until columnCount else 0 until columnCount) {
                var empty = true
                for (i in x until x + itemWidth) {
                    for (j in y until y + itemHeight) {
                        if (i >= columnCount || j >= rowCount || occupiedCells[i][j]) {
                            empty = false
                            break
                        }
                    }
                    if (!empty) {
                        break
                    }
                }
                if (empty) {
                    return Pair(x, y)
                }
            }
        }

        return null // No empty cell found
    }

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

        for (checkedYCell in 0 until config.columnCount) {
            for (checkedXCell in 0 until config.rowCount) {
                val wantedCell = Pair(checkedXCell, checkedYCell)
                if (!occupiedCells.contains(wantedCell)) {
                    return Rect(wantedCell.first, wantedCell.second, wantedCell.first, wantedCell.second)
                }
            }
        }

        return null
    }

    private fun getHomeFragment() = homeScreenPagerAdapter?.getFragmentAt(home_screen_view_pager.currentItem)

    override fun onResume() {
        super.onResume()
        updateStatusbarColor(Color.TRANSPARENT)
        if(currentGridSize != config.homeGrid) {
            adjustGridItems()
            currentGridSize = config.homeGrid
        }
    }

    override fun onBackPressed() {
        if (appBottomSheetBehavior?.state == STATE_EXPANDED) {
            closeAllAppsBottomSheet()
        }
        if (widgetsBottomSheetBehavior?.state == STATE_EXPANDED) {
            closeWidgetsBottomSheet()
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
    }

    private fun refetchLaunchers() {
        val launchers = launcherHelper.getAllAppLaunchers()

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
        if(pages.isEmpty()){
            addNewPage(0)
        }else{
            pages.add(PageWithGridItems(page = HomeScreenPage(id = -1L, pages.size), gridItems = emptyList(), isAddNewPageIndicator = true))
        }
        Log.i("HOMEVP", "Pages:$pages")
        homeScreenPagerAdapter = createHomeScreenPagerAdapter(pages)
        home_screen_view_pager.adapter = homeScreenPagerAdapter
        home_screen_view_pager.setOnTouchListener { _, _ -> false }
        setupPageIndicator()
    }
   private fun setupPageIndicator(selectedPosition: Int = 0){
       page_indicators_container.removeAllViews()

        //Set up page indicators
        val pageCount = homeScreenPagerAdapter?.count ?: 0
        for (i in 0 until pageCount) {
            val dot = layoutInflater.inflate(R.layout.indicator_dot, page_indicators_container, false)
            page_indicators_container.addView(dot)
        }

        // Set the initial dot indicator as selected
        page_indicators_container.getChildAt(selectedPosition)?.isSelected = true

        home_screen_view_pager.addOnPageChangeListener(object: ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until page_indicators_container.childCount) {
                    page_indicators_container.getChildAt(i).isSelected = (i == position)
                }
            }
        })
    }
    private fun addNewPage(position: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            homeScreenPagesDB.insert(HomeScreenPage(position = position))
            withContext(Dispatchers.Main) {
                onUpdateAdapter()
            }
        }
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
            launch(Dispatchers.Main) {
                homeScreenPagerAdapter?.updateItems(pages)
                setupPageIndicator(home_screen_view_pager.currentItem)
            }
        }
    }

    override fun onDragStarted() {
        home_screen_view_pager.setPagingEnabled(false)
    }

    override fun onDragStopped() {
        home_screen_view_pager.setPagingEnabled(true)
    }

    override fun onWidgetResizeStarted() {
        home_screen_view_pager.setPagingEnabled(false)
    }

    override fun onWidgetResizeStopped() {
        home_screen_view_pager.setPagingEnabled(true)
    }
    override fun gotoNextPage(i: Int) {
        if ((homeScreenPagerAdapter?.count ?: 0) > i) {
            home_screen_view_pager.setCurrentItem(i + 1, true)
        }
    }

    override fun gotoPrevPage(i: Int) {
        if (i >  0) {
            home_screen_view_pager.setCurrentItem(i - 1, true)
        }
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
        widgetsBottomSheetBehavior?.state = STATE_EXPANDED
    }
    private fun showAllAppsBottomSheet() {
        appBottomSheetBehavior?.state = STATE_EXPANDED
    }
    fun closeAllAppsBottomSheet() {
        appBottomSheetBehavior?.state = STATE_COLLAPSED
    }
    fun closeWidgetsBottomSheet() {
        widgetsBottomSheetBehavior?.state = STATE_COLLAPSED
    }
    override fun onFlingUp() {
        showAllAppsBottomSheet()
    }
    @SuppressLint("WrongConstant")
    override fun onFlingDown() {}
    private fun getDefaultAppPackages(appLaunchers: ArrayList<AppLauncher>, pageId: Long = 0): ArrayList<HomeScreenGridItem> {
        val rowCount = config.rowCount
        val homeScreenGridItems = ArrayList<HomeScreenGridItem>()
        try {
            val defaultDialerPackage = (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
            appLaunchers.firstOrNull { it.packageName == defaultDialerPackage }?.apply {
                val dialerIcon =
                    HomeScreenGridItem(null, pageId, 0, rowCount - 1, 0, rowCount - 1, defaultDialerPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
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
                        rowCount - 1,
                        1,
                        rowCount - 1,
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
                    HomeScreenGridItem(null, pageId, 2, rowCount - 1, 2, rowCount - 1, defaultBrowserPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
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
                        HomeScreenGridItem(null, pageId, 3, rowCount - 1, 3, rowCount - 1, storePackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
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
                    HomeScreenGridItem(null, pageId, 4, rowCount - 1, 4, rowCount - 1, defaultCameraPackage, "", title, ITEM_TYPE_ICON, "", -1, "", "", null)
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
