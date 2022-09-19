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

    // let's use a 6x5 grid for now with 1 special row at the bottom, prefilled with default apps
    private var rowXCoords = ArrayList<Int>(COLUMN_COUNT)
    private var rowYCoords = ArrayList<Int>(ROW_COUNT)
    private var rowWidth = 0
    private var rowHeight = 0
    private var iconSize = 0

    private var appIcons = ArrayList<HomeScreenGridItem>()
    private var appIconDrawables = HashMap<String, Drawable>()

    init {
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.smaller_text_size)
            setShadowLayer(.5f, 0f, 0f, Color.BLACK)
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

                // icons at the bottom are drawn at the bottom of the grid and they have no label
                if (icon.top == ROW_COUNT - 1) {
                    val drawableY = rowYCoords[icon.top] + rowHeight - iconSize - iconMargin * 2
                    drawable.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                } else {
                    val drawableY = rowYCoords[icon.top] + iconSize / 2
                    drawable.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)

                    val textY = rowYCoords[icon.top] + iconSize * 1.5f + labelSideMargin
                    val staticLayout = StaticLayout.Builder
                        .obtain(icon.title, 0, icon.title.length, textPaint, rowWidth - 2 * labelSideMargin)
                        .setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .build()

                    canvas.save()
                    canvas.translate(rowXCoords[icon.left].toFloat() + labelSideMargin, textY)
                    staticLayout.draw(canvas)
                    canvas.restore()
                }

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
