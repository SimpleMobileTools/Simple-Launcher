package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
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
    private var draggedItem: HomeScreenGridItem? = null

    // let's use a 6x5 grid for now with 1 special row at the bottom, prefilled with default apps
    private var rowXCoords = ArrayList<Int>(COLUMN_COUNT)
    private var rowYCoords = ArrayList<Int>(ROW_COUNT)
    private var rowWidth = 0
    private var rowHeight = 0
    private var iconSize = 0

    private var gridItems = ArrayList<HomeScreenGridItem>()
    private var gridItemDrawables = HashMap<String, Drawable>()
    private var gridCenters = ArrayList<Pair<Int, Int>>()

    init {
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.smaller_text_size)
            setShadowLayer(.5f, 0f, 0f, Color.BLACK)
        }

        fetchGridItems()
    }

    fun fetchGridItems() {
        ensureBackgroundThread {
            gridItemDrawables.clear()
            gridItems = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
            gridItems.forEach { item ->
                val drawable = context.getDrawableForPackageName(item.packageName)
                if (drawable != null) {
                    gridItemDrawables[item.packageName] = drawable
                }
            }

            invalidate()
        }
    }

    fun itemDraggingStarted(draggedGridItem: HomeScreenGridItem) {
        draggedItem = draggedGridItem
        invalidate()
    }

    fun itemDraggingStopped(x: Int, y: Int) {
        draggedItem = null
        invalidate()
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

        gridItems.forEach { item ->
            val drawable = gridItemDrawables[item.packageName]
            if (drawable != null) {
                val drawableX = rowXCoords[item.left] + iconMargin

                // icons at the bottom are drawn at the bottom of the grid and they have no label
                if (item.top == ROW_COUNT - 1) {
                    val drawableY = rowYCoords[item.top] + rowHeight - iconSize - iconMargin * 2
                    drawable.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                } else {
                    val drawableY = rowYCoords[item.top] + iconSize / 2
                    drawable.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

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

                drawable.draw(canvas)
            }
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
