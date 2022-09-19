package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
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
    private var textPaint: Paint

    // let's use a 6x5 grid for now with 1 special row at the bottom, prefilled with default apps
    private var rowXCoords = ArrayList<Int>(COLUMN_COUNT)
    private var rowYCoords = ArrayList<Int>(ROW_COUNT)
    private var rowWidth = 0
    private var rowHeight = 0
    private var iconSize = 0

    private var appIcons = ArrayList<HomeScreenGridItem>()
    private var appIconDrawables = HashMap<String, Drawable>()

    init {
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.normal_text_size)
        }

        fetchAppIcons()
    }

    fun fetchAppIcons() {
        ensureBackgroundThread {
            appIconDrawables.clear()
            appIcons = context.homeScreenGridItemsDB.getAllItems() as ArrayList<HomeScreenGridItem>
            appIcons.forEach { item ->
                val drawable = context.getDrawableForPackageName(item.packageName)
                if (drawable != null) {
                    appIconDrawables[item.packageName] = drawable
                }
            }

            invalidate()
        }
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
        }

        appIcons.forEach { icon ->
            val drawable = appIconDrawables[icon.packageName]
            if (drawable != null) {
                val drawableX = rowXCoords[icon.left] + iconMargin
                val drawableY = rowYCoords[icon.top] + rowHeight - iconSize - iconMargin * 2

                drawable.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                drawable.draw(canvas)
            }
        }
    }

    fun gridClicked(x: Float, y: Float): String {
        for (appIcon in appIcons) {
            if (x >= appIcon.left * rowWidth && x <= appIcon.right * rowWidth && y >= appIcon.top * rowHeight && y <= appIcon.bottom * rowHeight) {
                return appIcon.packageName
            }
        }

        return ""
    }
}
