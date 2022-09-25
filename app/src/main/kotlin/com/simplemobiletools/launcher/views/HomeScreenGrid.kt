package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName
import com.simplemobiletools.launcher.extensions.homeScreenGridItemsDB
import com.simplemobiletools.launcher.helpers.COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.ROW_COUNT
import com.simplemobiletools.launcher.models.HomeScreenGridItem

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var iconMargin = context.resources.getDimension(R.dimen.icon_side_margin).toInt()
    private var labelSideMargin = context.resources.getDimension(R.dimen.small_margin).toInt()
    private var textPaint: TextPaint
    private var dragShadowCirclePaint: Paint
    private var draggedItem: HomeScreenGridItem? = null

    // let's use a 6x5 grid for now with 1 special row at the bottom, prefilled with default apps
    private var rowXCoords = ArrayList<Int>(COLUMN_COUNT)
    private var rowYCoords = ArrayList<Int>(ROW_COUNT)
    private var rowWidth = 0
    private var rowHeight = 0
    private var iconSize = 0

    private var gridItems = ArrayList<HomeScreenGridItem>()
    private var gridCenters = ArrayList<Pair<Int, Int>>()
    private var draggedItemCurrentCoords = Pair(-1, -1)

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

        fetchGridItems()
    }

    fun fetchGridItems() {
        ensureBackgroundThread {
            gridItems = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
            gridItems.forEach { item ->
                item.drawable = context.getDrawableForPackageName(item.packageName)
            }

            invalidate()
        }
    }

    fun removeAppIcon(iconId: Long) {
        ensureBackgroundThread {
            context.homeScreenGridItemsDB.deleteById(iconId)
            gridItems.removeIf { it.id == iconId }
            invalidate()
        }
    }

    fun itemDraggingStarted(draggedGridItem: HomeScreenGridItem) {
        draggedItem = draggedGridItem
        if (draggedItem!!.drawable == null) {
            draggedItem!!.drawable = context.getDrawableForPackageName(draggedGridItem.packageName)
        }

        invalidate()
    }

    fun draggedItemMoved(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        draggedItemCurrentCoords = Pair(x, y)
        invalidate()
    }

    // figure out at which cell was the item dropped, if it is empty
    fun itemDraggingStopped(x: Int, y: Int) {
        if (draggedItem == null) {
            return
        }

        val center = gridCenters.minBy { Math.abs(it.first - x) + Math.abs(it.second - y) }
        var redrawIcons = false

        val gridCells = getClosestGridCells(center)
        if (gridCells != null) {
            val xIndex = gridCells.first
            val yIndex = gridCells.second
            // check if the destination grid item is empty
            val targetGridItem = gridItems.firstOrNull { it.left == xIndex && it.top == yIndex }
            if (targetGridItem == null) {
                val draggedHomeGridItem = gridItems.firstOrNull { it.id == draggedItem?.id }

                // we are moving an existing home screen item from one place to another
                if (draggedHomeGridItem != null) {
                    draggedHomeGridItem.apply {
                        left = xIndex
                        top = yIndex
                        right = xIndex + 1
                        bottom = yIndex + 1

                        ensureBackgroundThread {
                            context.homeScreenGridItemsDB.updateAppPosition(left, top, right, bottom, id!!)
                        }
                    }
                    redrawIcons = true
                } else if (draggedItem != null) {
                    // we are dragging a new item at the home screen from the All Apps fragment
                    val newHomeScreenGridItem =
                        HomeScreenGridItem(null, xIndex, yIndex, xIndex + 1, yIndex + 1, draggedItem!!.packageName, draggedItem!!.title, draggedItem!!.drawable)
                    ensureBackgroundThread {
                        val newId = context.homeScreenGridItemsDB.insert(newHomeScreenGridItem)
                        newHomeScreenGridItem.id = newId
                        gridItems.add(newHomeScreenGridItem)
                        invalidate()
                    }
                }
            } else {
                redrawIcons = true
            }
        }

        draggedItem = null
        draggedItemCurrentCoords = Pair(-1, -1)
        if (redrawIcons) {
            invalidate()
        }
    }

    // convert stuff like 102x192 to grid cells like 0x1
    private fun getClosestGridCells(center: Pair<Int, Int>): Pair<Int, Int>? {
        rowXCoords.forEachIndexed { xIndex, xCell ->
            rowYCoords.forEachIndexed { yIndex, yCell ->
                if (xCell + rowWidth / 2 == center.first && yCell + rowHeight / 2 == center.second) {
                    return Pair(xIndex, yIndex)
                }
            }
        }

        return null
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rowXCoords.isEmpty()) {
            rowWidth = width / (COLUMN_COUNT)
            rowHeight = height / ROW_COUNT
            iconSize = rowWidth - 2 * iconMargin
            for (i in 0 until COLUMN_COUNT) {
                rowXCoords.add(i, i * rowWidth)
            }

            for (i in 0 until ROW_COUNT) {
                rowYCoords.add(i, i * rowHeight)
            }

            rowXCoords.forEach { x ->
                rowYCoords.forEach { y ->
                    gridCenters.add(Pair(x + rowWidth / 2, y + rowHeight / 2))
                }
            }
        }

        gridItems.filter { it.drawable != null }.forEach { item ->
            if (item.id != draggedItem?.id) {
                val drawableX = rowXCoords[item.left] + iconMargin

                // icons at the bottom are drawn at the bottom of the grid and they have no label
                if (item.top == ROW_COUNT - 1) {
                    val drawableY = rowYCoords[item.top] + rowHeight - iconSize - iconMargin * 2
                    item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                } else {
                    val drawableY = rowYCoords[item.top] + iconSize / 2
                    item.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

                    if (item.id != draggedItem?.id) {
                        val textY = rowYCoords[item.top] + iconSize * 1.5f + labelSideMargin
                        val staticLayout = StaticLayout.Builder
                            .obtain(item.title, 0, item.title.length, textPaint, rowWidth - 2 * labelSideMargin)
                            .setMaxLines(2)
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .build()

                        canvas.save()
                        canvas.translate(rowXCoords[item.left].toFloat() + labelSideMargin, textY)
                        staticLayout.draw(canvas)
                        canvas.restore()
                    }
                }

                item.drawable!!.draw(canvas)
            }
        }

        if (draggedItem != null && draggedItemCurrentCoords.first != -1 && draggedItemCurrentCoords.second != -1) {
            // draw a circle under the current cell
            val center = gridCenters.minBy { Math.abs(it.first - draggedItemCurrentCoords.first) + Math.abs(it.second - draggedItemCurrentCoords.second) }
            val gridCells = getClosestGridCells(center)
            if (gridCells != null) {
                val shadowX = rowXCoords[gridCells.first] + iconMargin.toFloat() + iconSize / 2
                val shadowY = if (gridCells.second == ROW_COUNT - 1) {
                    rowYCoords[gridCells.second] + rowHeight - iconSize / 2 - iconMargin * 2
                } else {
                    rowYCoords[gridCells.second] + iconSize
                }

                canvas.drawCircle(shadowX, shadowY.toFloat(), iconSize / 2f, dragShadowCirclePaint)
            }

            // show the app icon itself at dragging
            val drawableX = draggedItemCurrentCoords.first - iconSize
            val drawableY = draggedItemCurrentCoords.second - iconSize
            draggedItem!!.drawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
            draggedItem!!.drawable!!.draw(canvas)
        }
    }

    fun isClickingGridItem(x: Int, y: Int): HomeScreenGridItem? {
        for (gridItem in gridItems) {
            if (x >= gridItem.left * rowWidth && x <= gridItem.right * rowWidth && y >= gridItem.top * rowHeight && y <= gridItem.bottom * rowHeight) {
                return gridItem
            }
        }

        return null
    }
}
