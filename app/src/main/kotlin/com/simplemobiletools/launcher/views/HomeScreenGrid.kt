package com.simplemobiletools.launcher.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.View
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.google.android.material.math.MathUtils
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isSPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.extensions.config
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.helpers.*
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var columnCount = context.config.homeColumnCount
    private var rowCount = context.config.homeRowCount
    private var cellXCoords = ArrayList<Int>(columnCount)
    private var cellYCoords = ArrayList<Int>(rowCount)
    var cellWidth = 0
    var cellHeight = 0
    private var extraXMargin = 0
    private var extraYMargin = 0

    private var iconMargin = (context.resources.getDimension(R.dimen.icon_side_margin) * 5 / columnCount).toInt()
    private var labelSideMargin = context.resources.getDimension(R.dimen.small_margin).toInt()
    private var roundedCornerRadius = context.resources.getDimension(R.dimen.activity_margin)
    private var pageIndicatorRadius = context.resources.getDimension(R.dimen.page_indicator_dot_radius)
    private var pageIndicatorMargin = context.resources.getDimension(R.dimen.page_indicator_margin)
    private var textPaint: TextPaint
    private var dragShadowCirclePaint: Paint
    private var emptyPageIndicatorPaint: Paint
    private var currentPageIndicatorPaint: Paint
    private var draggedItem: HomeScreenGridItem? = null
    private var resizedWidget: HomeScreenGridItem? = null
    private var isFirstDraw = true
    private var redrawWidgets = false
    private var iconSize = 0

    private var lastPage = 0
    private var currentPage = 0
    private var pageChangeLastArea = PageChangeArea.MIDDLE
    private var pageChangeLastAreaEntryTime = 0L
    private var pageChangeAnimLeftPercentage = 0f
    private var pageChangeEnabled = true
    private var pageChangeIndicatorsAlpha = 0f

    // apply fake margins at the home screen. Real ones would cause the icons be cut at dragging at screen sides
    var sideMargins = Rect()

    private var gridItems = ArrayList<HomeScreenGridItem>()
    private var gridCenters = ArrayList<Pair<Int, Int>>()
    private var draggedItemCurrentCoords = Pair(-1, -1)
    private var widgetViews = ArrayList<MyAppWidgetHostView>()

    val appWidgetHost = MyAppWidgetHost(context, WIDGET_HOST_ID)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    var itemClickListener: ((HomeScreenGridItem) -> Unit)? = null
    var itemLongClickListener: ((HomeScreenGridItem) -> Unit)? = null

    private val checkAndExecuteDelayedPageChange: Runnable = Runnable {
        if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            when (pageChangeLastArea) {
                PageChangeArea.RIGHT -> nextOrAdditionalPage(true)
                PageChangeArea.LEFT -> prevPage(true)
                else -> clearPageChangeFlags()
            }
        }
    }

    private val startFadingIndicators: Runnable = Runnable {
        ValueAnimator.ofFloat(1f, 0f)
            .apply {
                addUpdateListener {
                    pageChangeIndicatorsAlpha = it.animatedValue as Float
                    redrawGrid()
                }
                start()
            }
    }

    init {
        ViewCompat.setAccessibilityDelegate(this, HomeScreenGridTouchHelper(this))

        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.smaller_text_size)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
        }

        dragShadowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(R.color.hint_white)
            strokeWidth = context.resources.getDimension(R.dimen.small_margin)
            style = Paint.Style.STROKE
        }

        emptyPageIndicatorPaint = Paint(dragShadowCirclePaint).apply {
            strokeWidth = context.resources.getDimension(R.dimen.page_indicator_stroke_width)
        }
        currentPageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(R.color.white)
            style = Paint.Style.FILL
        }

        val sideMargin = context.resources.getDimension(R.dimen.normal_margin).toInt()
        sideMargins.apply {
            top = context.statusBarHeight
            bottom = context.navigationBarHeight
            left = sideMargin
            right = sideMargin
        }

        fetchGridItems()
    }

    fun fetchGridItems() {
        ensureBackgroundThread {
            val providers = appWidgetManager.installedProviders
            gridItems = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
            gridItems.forEach { item ->
                if (item.type == ITEM_TYPE_ICON) {
                    item.drawable = context.getDrawableForPackageName(item.packageName)
                } else if (item.type == ITEM_TYPE_SHORTCUT) {
                    if (item.icon != null) {
                        item.drawable = BitmapDrawable(item.icon)
                    } else {
                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.deleteById(item.id!!)
                        }
                    }
                }

                item.providerInfo = providers.firstOrNull { it.provider.className == item.className }
            }

            redrawGrid()
        }
    }

    fun resizeGrid(newRowCount: Int, newColumnCount: Int) {
        if (columnCount != newColumnCount || rowCount != newRowCount) {
            rowCount = newRowCount
            columnCount = newColumnCount
            cellXCoords = ArrayList(columnCount)
            cellYCoords = ArrayList(rowCount)
            gridCenters.clear()
            iconMargin = (context.resources.getDimension(R.dimen.icon_side_margin) * 5 / columnCount).toInt()
            redrawWidgets = true
            redrawGrid()
        }
    }

    fun removeAppIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
            }

            gridItems.removeIf { it.id == item.id }
            if (currentPage > getMaxPage()) {
                post {
                    prevPage()
                }
            }
            redrawGrid()
        }
    }

    private fun removeItemFromHomeScreen(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            if (item.id != null) {
                context.homeScreenGridItemsDB.deleteById(item.id!!)
            }

            if (item.type == ITEM_TYPE_WIDGET) {
                appWidgetHost.deleteAppWidgetId(item.widgetId)
            }
        }
    }

    fun itemDraggingStarted(draggedGridItem: HomeScreenGridItem) {
        draggedItem = draggedGridItem
        if (draggedItem!!.drawable == null) {
            draggedItem!!.drawable = context.getDrawableForPackageName(draggedGridItem.packageName)
        }

        redrawGrid()
    }

    fun draggedItemMoved(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        if (draggedItemCurrentCoords.first == -1 && draggedItemCurrentCoords.second == -1 && draggedItem != null) {
            if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                val draggedWidgetView = widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }
                if (draggedWidgetView != null) {
                    draggedWidgetView.buildDrawingCache()
                    draggedItem!!.drawable = Bitmap.createBitmap(draggedWidgetView.drawingCache).toDrawable(context.resources)
                    draggedWidgetView.beGone()
                }
            }
        }

        draggedItemCurrentCoords = Pair(x, y)
        if (x > right - sideMargins.right - cellWidth / 2) {
            doWithPageChangeDelay(PageChangeArea.RIGHT) {
                nextOrAdditionalPage()
            }
        } else if (x < left + sideMargins.left + cellWidth / 2) {
            doWithPageChangeDelay(PageChangeArea.LEFT) {
                prevPage()
            }
        } else {
            clearPageChangeFlags()
        }
        redrawGrid()
    }

    private fun clearPageChangeFlags() {
        pageChangeLastArea = PageChangeArea.MIDDLE
        pageChangeLastAreaEntryTime = 0
        removeCallbacks(checkAndExecuteDelayedPageChange)
    }

    private fun schedulePageChange() {
        pageChangeLastAreaEntryTime = System.currentTimeMillis()
        postDelayed(checkAndExecuteDelayedPageChange, PAGE_CHANGE_HOLD_THRESHOLD)
    }

    private fun scheduleIndicatorsFade() {
        pageChangeIndicatorsAlpha = 1f
        postDelayed(startFadingIndicators, PAGE_INDICATORS_FADE_DELAY)
    }

    private fun doWithPageChangeDelay(needed: PageChangeArea, pageChangeFunction: () -> Boolean) {
        if (pageChangeLastArea != needed) {
            pageChangeLastArea = needed
            schedulePageChange()
        } else if (System.currentTimeMillis() - pageChangeLastAreaEntryTime > PAGE_CHANGE_HOLD_THRESHOLD) {
            if (pageChangeFunction()) {
                clearPageChangeFlags()
            }
        }
    }

    // figure out at which cell was the item dropped, if it is empty
    fun itemDraggingStopped() {
        widgetViews.forEach {
            it.hasLongPressed = false
        }

        if (draggedItem == null) {
            return
        }

        when (draggedItem!!.type) {
            ITEM_TYPE_ICON, ITEM_TYPE_SHORTCUT -> addAppIconOrShortcut()
            ITEM_TYPE_WIDGET -> addWidget()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun widgetLongPressed(item: HomeScreenGridItem) {
        resizedWidget = item
        redrawGrid()

        val widgetView = widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }
        resize_frame.beGone()
        if (widgetView != null) {
            val viewX = widgetView.x.toInt()
            val viewY = widgetView.y.toInt()
            val frameRect = Rect(viewX, viewY, viewX + widgetView.width, viewY + widgetView.height)
            val otherGridItems = gridItems.filter { it.widgetId != item.widgetId }.toMutableList() as ArrayList<HomeScreenGridItem>
            resize_frame.updateFrameCoords(frameRect, cellWidth, cellHeight, sideMargins, item, otherGridItems)
            resize_frame.beVisible()
            resize_frame.z = 1f     // make sure the frame isnt behind the widget itself
            resize_frame.onClickListener = {
                hideResizeLines()
            }

            resize_frame.onResizeListener = { cellsRect ->
                item.left = cellsRect.left
                item.top = cellsRect.top
                item.right = cellsRect.right
                item.bottom = if (cellsRect.bottom > rowCount - 2) {
                    rowCount - 2
                } else {
                    cellsRect.bottom
                }
                updateWidgetPositionAndSize(widgetView, item)
                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.updateItemPosition(item.left, item.top, item.right, item.bottom, item.page, false, item.id!!)
                }
            }

            widgetView.ignoreTouches = true
            widgetView.setOnTouchListener { v, event ->
                resize_frame.onTouchEvent(event)
                return@setOnTouchListener true
            }
        }
    }

    fun hideResizeLines() {
        if (resizedWidget == null) {
            return
        }

        resize_frame.beGone()
        widgetViews.firstOrNull { it.tag == resizedWidget!!.widgetId }?.apply {
            ignoreTouches = false
            setOnTouchListener(null)
        }
        resizedWidget = null
    }

    private fun addAppIconOrShortcut() {
        val center = gridCenters.minBy {
            Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
        }

        var redrawIcons = false
        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val xIndex = gridCells.first
            val yIndex = gridCells.second

            // check if the destination cell is empty
            var areAllCellsEmpty = true
            val wantedCell = Pair(xIndex, yIndex)
            gridItems.filter { it.page == currentPage || it.docked }.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.getDockAdjustedTop(rowCount)..item.getDockAdjustedBottom(rowCount)) {
                        val cell = Pair(xCell, yCell)
                        val isAnyCellOccupied = wantedCell == cell
                        if (isAnyCellOccupied) {
                            areAllCellsEmpty = false
                            return@forEach
                        }
                    }
                }
            }

            if (areAllCellsEmpty) {
                val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }

                // we are moving an existing home screen item from one place to another
                if (draggedHomeGridItem != null) {
                    draggedHomeGridItem.apply {
                        left = xIndex
                        top = yIndex
                        right = xIndex
                        bottom = yIndex
                        page = currentPage
                        docked = yIndex == rowCount - 1

                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.updateItemPosition(left, top, right, bottom, page, docked, id!!)
                        }
                    }
                    redrawIcons = true
                } else if (draggedItem != null) {
                    // we are dragging a new item at the home screen from the All Apps fragment
                    val newHomeScreenGridItem = HomeScreenGridItem(
                        null,
                        xIndex,
                        yIndex,
                        xIndex,
                        yIndex,
                        currentPage,
                        draggedItem!!.packageName,
                        draggedItem!!.activityName,
                        draggedItem!!.title,
                        draggedItem!!.type,
                        "",
                        -1,
                        "",
                        "",
                        draggedItem!!.icon,
                        yIndex == rowCount - 1,
                        draggedItem!!.drawable,
                        draggedItem!!.providerInfo,
                        draggedItem!!.activityInfo
                    )

                    if (newHomeScreenGridItem.type == ITEM_TYPE_ICON) {
                        ensureBackgroundThread {
                            storeAndShowGridItem(newHomeScreenGridItem)
                        }
                    } else if (newHomeScreenGridItem.type == ITEM_TYPE_SHORTCUT) {
                        (context as? MainActivity)?.handleShorcutCreation(newHomeScreenGridItem.activityInfo!!) { label, icon, intent ->
                            ensureBackgroundThread {
                                newHomeScreenGridItem.title = label
                                newHomeScreenGridItem.icon = icon
                                newHomeScreenGridItem.intent = intent
                                newHomeScreenGridItem.drawable = BitmapDrawable(icon)
                                storeAndShowGridItem(newHomeScreenGridItem)
                            }
                        }
                    }
                }
            } else {
                performHapticFeedback()
                redrawIcons = true
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            redrawGrid()
        }
    }

    fun storeAndShowGridItem(item: HomeScreenGridItem) {
        val newId = context.homeScreenGridItemsDB.insert(item)
        item.id = newId
        gridItems.add(item)
        redrawGrid()
    }

    private fun addWidget() {
        val center = gridCenters.minBy {
            Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
        }

        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val widgetRect = getWidgetOccupiedRect(gridCells)
            val widgetTargetCells = ArrayList<Pair<Int, Int>>()
            for (xCell in widgetRect.left..widgetRect.right) {
                for (yCell in widgetRect.top..widgetRect.bottom) {
                    widgetTargetCells.add(Pair(xCell, yCell))
                }
            }

            var areAllCellsEmpty = true
            gridItems.filter { it.id != draggedItem?.id && (it.page == currentPage || it.docked) }.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.getDockAdjustedTop(rowCount)..item.getDockAdjustedBottom(rowCount)) {
                        val cell = Pair(xCell, yCell)
                        val isAnyCellOccupied = widgetTargetCells.contains(cell)
                        if (isAnyCellOccupied) {
                            areAllCellsEmpty = false
                            return@forEach
                        }
                    }
                }
            }

            if (areAllCellsEmpty) {
                val widgetItem = draggedItem!!.copy()
                widgetItem.apply {
                    left = widgetRect.left
                    top = widgetRect.top
                    right = widgetRect.right
                    bottom = widgetRect.bottom
                    page = currentPage
                }

                ensureBackgroundThread {
                    // store the new widget at creating it, else just move the existing one
                    if (widgetItem.id == null) {
                        val itemId = context.homeScreenGridItemsDB.insert(widgetItem)
                        widgetItem.id = itemId
                        post {
                            bindWidget(widgetItem, false)
                        }
                    } else {
                        context.homeScreenGridItemsDB.updateItemPosition(
                            widgetItem.left,
                            widgetItem.top,
                            widgetItem.right,
                            widgetItem.bottom,
                            currentPage,
                            false,
                            widgetItem.id!!
                        )
                        val widgetView = widgetViews.firstOrNull { it.tag == widgetItem.widgetId }
                        if (widgetView != null && !widgetItem.outOfBounds()) {
                            post {
                                widgetView.x = calculateWidgetX(widgetItem.left)
                                widgetView.y = calculateWidgetY(widgetItem.top)
                                widgetView.beVisible()
                            }
                        }

                        gridItems.firstOrNull { it.id == widgetItem.id }?.apply {
                            left = widgetItem.left
                            right = widgetItem.right
                            top = widgetItem.top
                            bottom = widgetItem.bottom
                            page = widgetItem.page
                        }
                    }
                }
            } else {
                performHapticFeedback()
                widgetViews.firstOrNull { it.tag == draggedItem?.widgetId }?.apply {
                    post {
                        beVisible()
                    }
                }
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        redrawGrid()
    }

    private fun bindWidget(item: HomeScreenGridItem, isInitialDrawAfterLaunch: Boolean) {
        val activity = context as MainActivity
        val appWidgetProviderInfo = item.providerInfo ?: appWidgetManager!!.installedProviders.firstOrNull { it.provider.className == item.className }
        if (appWidgetProviderInfo != null) {
            val appWidgetId = appWidgetHost.allocateAppWidgetId()
            activity.handleWidgetBinding(appWidgetManager, appWidgetId, appWidgetProviderInfo) { canBind ->
                if (canBind) {
                    if (appWidgetProviderInfo.configure != null && !isInitialDrawAfterLaunch) {
                        activity.handleWidgetConfigureScreen(appWidgetHost, appWidgetId) { success ->
                            if (success) {
                                placeAppWidget(appWidgetId, appWidgetProviderInfo, item)
                            } else {
                                removeItemFromHomeScreen(item)
                            }
                        }
                    } else {
                        placeAppWidget(appWidgetId, appWidgetProviderInfo, item)
                    }
                } else {
                    removeItemFromHomeScreen(item)
                }

                if (currentPage > getMaxPage()) {
                    prevPage(redraw = true)
                }
            }
        }
    }

    private fun placeAppWidget(appWidgetId: Int, appWidgetProviderInfo: AppWidgetProviderInfo, item: HomeScreenGridItem) {
        item.widgetId = appWidgetId
        // we have to pass the base context here, else there will be errors with the themes
        val widgetView = appWidgetHost.createView((context as MainActivity).baseContext, appWidgetId, appWidgetProviderInfo) as MyAppWidgetHostView
        widgetView.tag = appWidgetId
        widgetView.setAppWidget(appWidgetId, appWidgetProviderInfo)
        widgetView.longPressListener = { x, y ->
            val activity = context as? MainActivity
            if (activity?.isAllAppsFragmentExpanded() == false) {
                activity.showHomeIconMenu(x, widgetView.y, item, false)
                performHapticFeedback()
            }
        }

        widgetView.onIgnoreInterceptedListener = {
            hideResizeLines()
        }

        val widgetSize = updateWidgetPositionAndSize(widgetView, item)
        addView(widgetView, widgetSize.width, widgetSize.height)
        widgetViews.add(widgetView)

        // remove the drawable so that it gets refreshed on long press
        item.drawable = null
        gridItems.add(item)
    }

    private fun updateWidgetPositionAndSize(widgetView: AppWidgetHostView, item: HomeScreenGridItem): Size {
        var x = calculateWidgetX(item.left) + width * item.page - width * lastPage
        if (pageChangeAnimLeftPercentage > 0f && pageChangeAnimLeftPercentage < 1f && (item.page == currentPage || item.page == lastPage)) {
            val xFactor = if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage
            } else {
                -pageChangeAnimLeftPercentage
            }
            val lastXFactor = if (currentPage > lastPage) {
                pageChangeAnimLeftPercentage - 1
            } else {
                1 - pageChangeAnimLeftPercentage
            }
            if (item.page == currentPage) {
                x += width * xFactor
            }
            if (item.page == lastPage) {
                x += width * lastXFactor
            }
        }
        widgetView.x = x
        widgetView.y = calculateWidgetY(item.top)
        val widgetWidth = item.getWidthInCells() * cellWidth
        val widgetHeight = item.getHeightInCells() * cellHeight

        val density = context.resources.displayMetrics.density
        val widgetDpWidth = (widgetWidth / density).toInt()
        val widgetDpHeight = (widgetHeight / density).toInt()

        if (isSPlus()) {
            val sizes = listOf(SizeF(widgetDpWidth.toFloat(), widgetDpHeight.toFloat()))
            widgetView.updateAppWidgetSize(Bundle(), sizes)
        } else {
            widgetView.updateAppWidgetSize(Bundle(), widgetDpWidth, widgetDpHeight, widgetDpWidth, widgetDpHeight)
        }

        widgetView.layoutParams?.width = widgetWidth
        widgetView.layoutParams?.height = widgetHeight
        return Size(widgetWidth, widgetHeight)
    }

    private fun calculateWidgetX(leftCell: Int) = cellXCoords[leftCell] + sideMargins.left.toFloat() + extraXMargin

    private fun calculateWidgetY(topCell: Int) = cellYCoords[topCell] + sideMargins.top.toFloat() + extraYMargin

    // convert stuff like 102x192 to grid cells like 0x1
    private fun getClosestGridCells(center: Pair<Int, Int>): Pair<Int, Int>? {
        cellXCoords.forEachIndexed { xIndex, xCell ->
            cellYCoords.forEachIndexed { yIndex, yCell ->
                if (xCell + cellWidth / 2 == center.first && yCell + cellHeight / 2 == center.second) {
                    return Pair(xIndex, yIndex)
                }
            }
        }

        return null
    }

    private fun redrawGrid() {
        post {
            setWillNotDraw(false)
            invalidate()
        }
    }

    private fun getFakeWidth() = width - sideMargins.left - sideMargins.right

    private fun getFakeHeight() = height - sideMargins.top - sideMargins.bottom

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        if (canvas == null) {
            return
        }

        super.onDraw(canvas)
        if (cellXCoords.isEmpty()) {
            fillCellSizes()
        }

        val currentXFactor = if (currentPage > lastPage) {
            pageChangeAnimLeftPercentage
        } else {
            -pageChangeAnimLeftPercentage
        }
        val lastXFactor = if (currentPage > lastPage) {
            pageChangeAnimLeftPercentage - 1
        } else {
            1 - pageChangeAnimLeftPercentage
        }

        fun handleItemDrawing(item: HomeScreenGridItem, xFactor: Float) {
            if (item.id != draggedItem?.id) {
                val drawableX = cellXCoords[item.left] + iconMargin + extraXMargin + sideMargins.left + (width * xFactor).toInt()

                if (item.docked) {
                    val drawableY = cellYCoords[rowCount - 1] + cellHeight - iconMargin - iconSize + sideMargins.top

                    item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                } else {
                    val drawableY = cellYCoords[item.top] + iconMargin + extraYMargin + sideMargins.top
                    item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

                    if (item.id != draggedItem?.id && item.title.isNotEmpty()) {
                        val textX = cellXCoords[item.left].toFloat() + labelSideMargin + sideMargins.left + width * xFactor
                        val textY = cellYCoords[item.top].toFloat() + iconSize + iconMargin + extraYMargin + labelSideMargin + sideMargins.top
                        val staticLayout = StaticLayout.Builder
                            .obtain(item.title, 0, item.title.length, textPaint, cellWidth - 2 * labelSideMargin)
                            .setMaxLines(2)
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .build()

                        canvas.save()
                        canvas.translate(textX, textY)
                        staticLayout.draw(canvas)
                        canvas.restore()
                    }
                }

                item.drawable!!.draw(canvas)
            }
        }

        gridItems.filter { (it.drawable != null && it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_SHORTCUT) && it.page == currentPage && !it.docked }
            .forEach { item ->
                if (item.outOfBounds()) {
                    return@forEach
                }

                handleItemDrawing(item, currentXFactor)
            }
        gridItems.filter { (it.drawable != null && it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_SHORTCUT) && it.docked }.forEach { item ->
            if (item.outOfBounds()) {
                return@forEach
            }

            handleItemDrawing(item, 0f)
        }
        if (pageChangeAnimLeftPercentage > 0f && pageChangeAnimLeftPercentage < 1f) {
            gridItems.filter { (it.drawable != null && it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_SHORTCUT) && it.page == lastPage && !it.docked }
                .forEach { item ->
                    if (item.outOfBounds()) {
                        return@forEach
                    }

                    handleItemDrawing(item, lastXFactor)
                }
        }

        if (isFirstDraw) {
            gridItems.filter { it.type == ITEM_TYPE_WIDGET && !it.outOfBounds() }.forEach { item ->
                bindWidget(item, true)
            }
        } else {
            gridItems.filter { it.type == ITEM_TYPE_WIDGET && !it.outOfBounds() }.forEach { item ->
                widgetViews.firstOrNull { it.tag == item.widgetId }?.also {
                    updateWidgetPositionAndSize(it, item)
                }
            }
        }

        // Only draw page indicators when there is a need for it
        if (pageChangeAnimLeftPercentage > 0f || pageChangeIndicatorsAlpha != 0f) {
            val pageCount = max(getMaxPage(), currentPage) + 1
            val pageIndicatorsRequiredWidth = pageCount * pageIndicatorRadius * 2 + pageCount * (pageIndicatorMargin - 1)
            val usableWidth = getFakeWidth()
            val pageIndicatorsStart = (usableWidth - pageIndicatorsRequiredWidth) / 2 + sideMargins.left
            var currentPageIndicatorLeft = pageIndicatorsStart
            val pageIndicatorY = cellYCoords[rowCount - 1].toFloat() + sideMargins.top + extraYMargin + iconMargin
            val pageIndicatorStep = pageIndicatorRadius * 2 + pageIndicatorMargin
            if (pageChangeIndicatorsAlpha != 0f) {
                emptyPageIndicatorPaint.alpha = (pageChangeIndicatorsAlpha * 255.0f).toInt()
            } else {
                emptyPageIndicatorPaint.alpha = 255
            }
            // Draw empty page indicators
            for (page in 0 until pageCount) {
                canvas.drawCircle(currentPageIndicatorLeft + pageIndicatorRadius, pageIndicatorY, pageIndicatorRadius, emptyPageIndicatorPaint)
                currentPageIndicatorLeft += pageIndicatorStep
            }

            // Draw current page indicator on exact position
            val currentIndicatorRangeStart = pageIndicatorsStart + lastPage * pageIndicatorStep
            val currentIndicatorRangeEnd = pageIndicatorsStart + currentPage * pageIndicatorStep
            val currentIndicatorPosition = MathUtils.lerp(currentIndicatorRangeStart, currentIndicatorRangeEnd, 1 - pageChangeAnimLeftPercentage)
            if (pageChangeIndicatorsAlpha != 0f) {
                currentPageIndicatorPaint.alpha = (pageChangeIndicatorsAlpha * 255.0f).toInt()
            } else {
                currentPageIndicatorPaint.alpha = 255
            }
            canvas.drawCircle(currentIndicatorPosition + pageIndicatorRadius, pageIndicatorY, pageIndicatorRadius, currentPageIndicatorPaint)
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            if (draggedItem!!.type == ITEM_TYPE_ICON || draggedItem!!.type == ITEM_TYPE_SHORTCUT) {
                // draw a circle under the current cell
                val center = gridCenters.minBy {
                    abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
                }

                val gridCells = getClosestGridCells(center)
                if (gridCells != null) {
                    val shadowX = cellXCoords[gridCells.first] + iconMargin + iconSize / 2f + extraXMargin + sideMargins.left
                    val shadowY = if (gridCells.second == rowCount - 1) {
                        cellYCoords[gridCells.second] + cellHeight - iconMargin - iconSize / 2f
                    } else {
                        cellYCoords[gridCells.second] + iconMargin + iconSize / 2f + extraYMargin
                    } + sideMargins.top

                    canvas.drawCircle(shadowX, shadowY, iconSize / 2f, dragShadowCirclePaint)
                }

                // show the app icon itself at dragging, move it above the finger a bit to make it visible
                val drawableX = (draggedItemCurrentCoords.first - iconSize / 1.5f).toInt()
                val drawableY = (draggedItemCurrentCoords.second - iconSize / 1.2f).toInt()
                draggedItem!!.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                draggedItem!!.drawable!!.draw(canvas)
            } else if (draggedItem!!.type == ITEM_TYPE_WIDGET) {
                // at first draw we are loading the widget from the database at some exact spot, not dragging it
                if (!isFirstDraw) {
                    val center = gridCenters.minBy {
                        Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
                    }

                    val gridCells = getClosestGridCells(center)
                    if (gridCells != null) {
                        val widgetRect = getWidgetOccupiedRect(gridCells)
                        val leftSide = calculateWidgetX(widgetRect.left)
                        val topSide = calculateWidgetY(widgetRect.top)
                        val rightSide = leftSide + draggedItem!!.getWidthInCells() * cellWidth
                        val bottomSide = topSide + draggedItem!!.getHeightInCells() * cellHeight
                        canvas.drawRoundRect(leftSide, topSide, rightSide, bottomSide, roundedCornerRadius, roundedCornerRadius, dragShadowCirclePaint)
                    }

                    // show the widget preview itself at dragging
                    val drawable = draggedItem!!.drawable!!
                    val aspectRatio = drawable.minimumHeight / drawable.minimumWidth.toFloat()
                    val drawableX = (draggedItemCurrentCoords.first - drawable.minimumWidth / 2f).toInt()
                    val drawableY = (draggedItemCurrentCoords.second - drawable.minimumHeight / 3f).toInt()
                    val drawableWidth = draggedItem!!.getWidthInCells() * cellWidth - iconMargin * (draggedItem!!.getWidthInCells() - 1)
                    drawable.setBounds(
                        drawableX,
                        drawableY,
                        drawableX + drawableWidth,
                        (drawableY + drawableWidth * aspectRatio).toInt()
                    )
                    drawable.draw(canvas)
                }
            }
        }

        isFirstDraw = false
    }

    private fun fillCellSizes() {
        cellWidth = getFakeWidth() / context.config.homeColumnCount
        cellHeight = getFakeHeight() / context.config.homeRowCount
        extraXMargin = if (cellWidth > cellHeight) {
            (cellWidth - cellHeight) / 2
        } else {
            0
        }
        extraYMargin = if (cellHeight > cellWidth) {
            (cellHeight - cellWidth) / 2
        } else {
            0
        }
        iconSize = min(cellWidth, cellHeight) - 2 * iconMargin
        for (i in 0 until context.config.homeColumnCount) {
            cellXCoords.add(i, i * cellWidth)
        }

        for (i in 0 until context.config.homeRowCount) {
            cellYCoords.add(i, i * cellHeight)
        }

        cellXCoords.forEach { x ->
            cellYCoords.forEach { y ->
                gridCenters.add(Pair(x + cellWidth / 2, y + cellHeight / 2))
            }
        }
    }

    fun fragmentExpanded() {
        widgetViews.forEach {
            it.ignoreTouches = true
        }
    }

    fun fragmentCollapsed() {
        widgetViews.forEach {
            it.ignoreTouches = false
        }
    }

    // get the clickable area around the icon, it includes text too
    fun getClickableRect(item: HomeScreenGridItem): Rect {
        if (cellXCoords.isEmpty()) {
            fillCellSizes()
        }

        val clickableLeft = cellXCoords[item.left] + sideMargins.left + extraXMargin
        val clickableTop = if (item.docked) {
            cellYCoords[item.getDockAdjustedTop(rowCount)] + cellHeight - iconSize - iconMargin
        } else {
            cellYCoords[item.top] - iconMargin + extraYMargin
        } + sideMargins.top
        return Rect(clickableLeft, clickableTop, clickableLeft + iconSize + 2 * iconMargin, clickableTop + iconSize + 2 * iconMargin)
    }

    // drag the center of the widget, not the top left corner
    private fun getWidgetOccupiedRect(item: Pair<Int, Int>): Rect {
        val left = item.first - floor((draggedItem!!.getWidthInCells() - 1) / 2.0).toInt()
        val rect = Rect(left, item.second, left + draggedItem!!.getWidthInCells() - 1, item.second + draggedItem!!.getHeightInCells() - 1)
        if (rect.left < 0) {
            rect.right -= rect.left
            rect.left = 0
        } else if (rect.right > columnCount - 1) {
            val diff = rect.right - columnCount + 1
            rect.right -= diff
            rect.left -= diff
        }

        if (rect.top < 0) {
            rect.bottom -= rect.top
            rect.top = 0
        } else if (rect.bottom > rowCount - 2) {
            val diff = rect.bottom - rowCount + 2
            rect.bottom -= diff
            rect.top -= diff
        }

        return rect
    }

    fun isClickingGridItem(x: Int, y: Int): HomeScreenGridItem? {
        for (gridItem in gridItems.filter { it.page == currentPage || it.docked }) {
            if (gridItem.outOfBounds()) {
                continue
            }

            if (gridItem.type == ITEM_TYPE_ICON || gridItem.type == ITEM_TYPE_SHORTCUT) {
                val rect = getClickableRect(gridItem)
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    return gridItem
                }
            } else if (gridItem.type == ITEM_TYPE_WIDGET) {
                val left = calculateWidgetX(gridItem.left)
                val top = calculateWidgetY(gridItem.top)
                val right = left + gridItem.getWidthInCells() * cellWidth
                val bottom = top + gridItem.getHeightInCells() * cellHeight

                if (x >= left && x <= right && y >= top && y <= bottom) {
                    return gridItem
                }
            }
        }

        return null
    }

    fun intoViewSpaceCoords(screenSpaceX: Float, screenSpaceY: Float): Pair<Float, Float> {
        val viewLocation = IntArray(2)
        getLocationOnScreen(viewLocation)
        val x = screenSpaceX - viewLocation[0]
        val y = screenSpaceY - viewLocation[1]
        return Pair(x, y)
    }

    private fun HomeScreenGridItem.outOfBounds(): Boolean {
        return (left >= cellXCoords.size || right >= cellXCoords.size || (!docked && (top >= cellYCoords.size - 1 || bottom >= cellYCoords.size - 1)))
    }

    private inner class HomeScreenGridTouchHelper(host: View) : ExploreByTouchHelper(host) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val item = isClickingGridItem(x.toInt(), y.toInt())

            return if (item != null) {
                item.id?.toInt() ?: INVALID_ID
            } else {
                INVALID_ID
            }
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>?) {
            val sorted = gridItems.sortedBy {
                it.getDockAdjustedTop(rowCount) * 100 + it.left
            }
            sorted.forEachIndexed { index, homeScreenGridItem ->
                virtualViewIds?.add(index, homeScreenGridItem.id?.toInt() ?: index)
            }
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId } ?: throw IllegalArgumentException("Unknown id")

            node.text = item.title

            val viewLocation = IntArray(2)
            getLocationOnScreen(viewLocation)

            val viewBounds = getClickableRect(item)
            val onScreenBounds = Rect(viewBounds)
            onScreenBounds.offset(viewLocation[0], viewLocation[1])
            node.setBoundsInScreen(onScreenBounds)
            node.setBoundsInParent(viewBounds)

            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            node.setParent(this@HomeScreenGrid)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            val item = gridItems.firstOrNull { it.id?.toInt() == virtualViewId } ?: throw IllegalArgumentException("Unknown id")
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> itemClickListener?.apply {
                    invoke(item)
                    return true
                }

                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> itemLongClickListener?.apply {
                    invoke(item)
                    return true
                }
            }

            return false
        }
    }

    private fun getMaxPage() = gridItems.filter { !it.docked && !it.outOfBounds() }.maxOfOrNull { it.page } ?: 0

    private fun nextOrAdditionalPage(redraw: Boolean = false): Boolean {
        if (currentPage < getMaxPage() + 1 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun nextPage(redraw: Boolean = false): Boolean {
        if (currentPage < getMaxPage() && pageChangeEnabled) {
            lastPage = currentPage
            currentPage++
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun prevPage(redraw: Boolean = false): Boolean {
        if (currentPage > 0 && pageChangeEnabled) {
            lastPage = currentPage
            currentPage--
            handlePageChange(redraw)
            return true
        }

        return false
    }

    fun skipToPage(targetPage: Int): Boolean {
        if (currentPage != targetPage && targetPage < getMaxPage() + 1) {
            lastPage = currentPage
            currentPage = targetPage
            handlePageChange()
            return true
        }

        return false
    }

    private fun handlePageChange(redraw: Boolean = false) {
        pageChangeEnabled = false
        pageChangeIndicatorsAlpha = 0f
        removeCallbacks(startFadingIndicators)
        if (redraw) {
            redrawGrid()
        }
        ValueAnimator.ofFloat(1f, 0f)
            .apply {
                addUpdateListener {
                    pageChangeAnimLeftPercentage = it.animatedValue as Float
                    redrawGrid()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        pageChangeAnimLeftPercentage = 0f
                        pageChangeEnabled = true
                        lastPage = currentPage
                        schedulePageChange()
                        scheduleIndicatorsFade()
                        redrawGrid()
                    }
                })
                start()
            }
    }

    companion object {
        private const val PAGE_CHANGE_HOLD_THRESHOLD = 500L
        private const val PAGE_INDICATORS_FADE_DELAY = PAGE_CHANGE_HOLD_THRESHOLD + 300L

        private enum class PageChangeArea {
            LEFT,
            MIDDLE,
            RIGHT
        }
    }
}
