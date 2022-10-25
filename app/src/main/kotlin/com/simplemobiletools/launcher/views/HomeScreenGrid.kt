package com.simplemobiletools.launcher.views

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
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toDrawable
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isSPlus
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.activities.MainActivity
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.helpers.*
import com.simplemobiletools.launcher.models.HomeScreenGridItem
import kotlinx.android.synthetic.main.activity_main.view.*

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var iconMargin = context.resources.getDimension(R.dimen.icon_side_margin).toInt()
    private var labelSideMargin = context.resources.getDimension(R.dimen.small_margin).toInt()
    private var roundedCornerRadius = context.resources.getDimension(R.dimen.activity_margin)
    private var textPaint: TextPaint
    private var dragShadowCirclePaint: Paint
    private var draggedItem: HomeScreenGridItem? = null
    private var resizedWidget: HomeScreenGridItem? = null
    private var isFirstDraw = true
    private var iconSize = 0

    // let's use a 6x5 grid for now with 1 special row at the bottom, prefilled with default apps
    private var cellXCoords = ArrayList<Int>(COLUMN_COUNT)
    private var cellYCoords = ArrayList<Int>(ROW_COUNT)
    var cellWidth = 0
    var cellHeight = 0

    // apply fake margins at the home screen. Real ones would cause the icons be cut at dragging at screen sides
    var sideMargins = Rect()

    private var gridItems = ArrayList<HomeScreenGridItem>()
    private var gridCenters = ArrayList<Pair<Int, Int>>()
    private var draggedItemCurrentCoords = Pair(-1, -1)
    private var widgetViews = ArrayList<MyAppWidgetHostView>()

    val appWidgetHost = MyAppWidgetHost(context, WIDGET_HOST_ID)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    init {
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.smaller_text_size)
            setShadowLayer(.5f, 0f, 0f, Color.BLACK)
        }

        dragShadowCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.resources.getColor(R.color.light_grey_stroke)
            strokeWidth = context.resources.getDimension(R.dimen.small_margin)
            style = Paint.Style.STROKE
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

    fun removeAppIcon(item: HomeScreenGridItem) {
        ensureBackgroundThread {
            removeItemFromHomeScreen(item)
            post {
                removeView(widgetViews.firstOrNull { it.tag == item.widgetId })
            }

            gridItems.removeIf { it.id == item.id }
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
        redrawGrid()
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
                item.bottom = cellsRect.bottom
                updateWidgetPositionAndSize(widgetView, item)
                ensureBackgroundThread {
                    context.homeScreenGridItemsDB.updateItemPosition(cellsRect.left, cellsRect.top, cellsRect.right, cellsRect.bottom, item.id!!)
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
            gridItems.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.top..item.bottom) {
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

                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.updateItemPosition(left, top, right, bottom, id!!)
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
                        draggedItem!!.packageName,
                        draggedItem!!.activityName,
                        draggedItem!!.title,
                        draggedItem!!.type,
                        "",
                        -1,
                        "",
                        "",
                        draggedItem!!.icon,
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
            gridItems.filter { it.id != draggedItem?.id }.forEach { item ->
                for (xCell in item.left..item.right) {
                    for (yCell in item.top..item.bottom) {
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
                        context.homeScreenGridItemsDB.updateItemPosition(widgetItem.left, widgetItem.top, widgetItem.right, widgetItem.bottom, widgetItem.id!!)
                        val widgetView = widgetViews.firstOrNull { it.tag == widgetItem.widgetId }
                        if (widgetView != null) {
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
        widgetView.x = calculateWidgetX(item.left)
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

    private fun calculateWidgetX(leftCell: Int) = leftCell * cellWidth + sideMargins.left.toFloat()

    private fun calculateWidgetY(topCell: Int) = topCell * cellHeight + sideMargins.top.toFloat()

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
        super.onDraw(canvas)
        if (canvas == null) {
            return
        }

        if (cellXCoords.isEmpty()) {
            fillCellSizes()
        }

        gridItems.filter { it.drawable != null && it.type == ITEM_TYPE_ICON || it.type == ITEM_TYPE_SHORTCUT }.forEach { item ->
            if (item.id != draggedItem?.id) {
                val drawableX = cellXCoords[item.left] + iconMargin + sideMargins.left

                // icons at the bottom are drawn at the bottom of the grid and they have no label
                if (item.top == ROW_COUNT - 1) {
                    val drawableY = cellYCoords[item.top] + cellHeight - iconSize - iconMargin * 2 + sideMargins.top
                    item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                } else {
                    val drawableY = cellYCoords[item.top] + iconSize / 2 + sideMargins.top
                    item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

                    if (item.id != draggedItem?.id && item.title.isNotEmpty()) {
                        val textX = cellXCoords[item.left].toFloat() + labelSideMargin + sideMargins.left
                        val textY = cellYCoords[item.top] + iconSize * 1.5f + labelSideMargin + sideMargins.top
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

        if (isFirstDraw) {
            gridItems.filter { it.type == ITEM_TYPE_WIDGET }.forEach { item ->
                bindWidget(item, true)
            }
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            if (draggedItem!!.type == ITEM_TYPE_ICON || draggedItem!!.type == ITEM_TYPE_SHORTCUT) {
                // draw a circle under the current cell
                val center = gridCenters.minBy {
                    Math.abs(it.first - draggedItemCurrentCoords.first + sideMargins.left) + Math.abs(it.second - draggedItemCurrentCoords.second + sideMargins.top)
                }

                val gridCells = getClosestGridCells(center)
                if (gridCells != null) {
                    val shadowX = cellXCoords[gridCells.first] + iconMargin.toFloat() + iconSize / 2 + sideMargins.left
                    val shadowY = if (gridCells.second == ROW_COUNT - 1) {
                        cellYCoords[gridCells.second] + cellHeight - iconSize / 2 - iconMargin * 2
                    } else {
                        cellYCoords[gridCells.second] + iconSize
                    } + sideMargins.top

                    canvas.drawCircle(shadowX, shadowY.toFloat(), iconSize / 2f, dragShadowCirclePaint)
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
                        val leftSide = widgetRect.left * cellWidth + sideMargins.left + iconMargin.toFloat()
                        val topSide = widgetRect.top * cellHeight + sideMargins.top + iconMargin.toFloat()
                        val rightSide = leftSide + draggedItem!!.getWidthInCells() * cellWidth - sideMargins.right - iconMargin.toFloat()
                        val bottomSide = topSide + draggedItem!!.getHeightInCells() * cellHeight - sideMargins.top
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
        cellWidth = getFakeWidth() / COLUMN_COUNT
        cellHeight = getFakeHeight() / ROW_COUNT
        iconSize = cellWidth - 2 * iconMargin
        for (i in 0 until COLUMN_COUNT) {
            cellXCoords.add(i, i * cellWidth)
        }

        for (i in 0 until ROW_COUNT) {
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

        val clickableLeft = item.left * cellWidth + sideMargins.left
        val clickableTop = cellYCoords[item.top] + iconSize / 3 + sideMargins.top
        return Rect(clickableLeft, clickableTop, clickableLeft + cellWidth, clickableTop + iconSize * 2)
    }

    // drag the center of the widget, not the top left corner
    private fun getWidgetOccupiedRect(item: Pair<Int, Int>): Rect {
        val left = item.first - Math.floor((draggedItem!!.getWidthInCells() - 1) / 2.0).toInt()
        val rect = Rect(left, item.second, left + draggedItem!!.getWidthInCells() - 1, item.second + draggedItem!!.getHeightInCells() - 1)
        if (rect.left < 0) {
            rect.right -= rect.left
            rect.left = 0
        } else if (rect.right > COLUMN_COUNT - 1) {
            val diff = rect.right - COLUMN_COUNT + 1
            rect.right -= diff
            rect.left -= diff
        }

        // do not allow placing widgets at the bottom row, that is for pinned default apps
        if (rect.bottom >= ROW_COUNT - 1) {
            val diff = rect.bottom - ROW_COUNT + 2
            rect.bottom -= diff
            rect.top -= diff
        }

        return rect
    }

    fun isClickingGridItem(x: Int, y: Int): HomeScreenGridItem? {
        for (gridItem in gridItems) {
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
}
