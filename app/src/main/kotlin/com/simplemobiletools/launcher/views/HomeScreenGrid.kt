package com.simplemobiletools.launcher.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.telecom.TelecomManager
import android.util.AttributeSet
import android.view.View
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.getDrawableForPackageName

class HomeScreenGrid(context: Context, attrs: AttributeSet, defStyle: Int) : View(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private val ROW_COUNT = 6
    private val COLUMN_COUNT = 6

    private var iconMargin = context.resources.getDimension(R.dimen.icon_side_margin).toInt()
    private var textPaint: Paint
    private var defaultDialerPackage = (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
    private var dialerDrawable = context.getDrawableForPackageName(defaultDialerPackage)

    // let's use a 5x5 grid for now with 1 special row at the bottom, prefilled with default apps
    private var rowXCoords = ArrayList<Int>(COLUMN_COUNT)
    private var rowYCoords = ArrayList<Int>(ROW_COUNT)
    private var rowWidth = 0
    private var rowHeight = 0
    private var iconSize = 0

    init {
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = context.resources.getDimension(R.dimen.normal_text_size)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rowXCoords.isEmpty()) {
            rowWidth = width / (COLUMN_COUNT - 1)
            rowHeight = height / ROW_COUNT
            iconSize = rowWidth - 2 * iconMargin
            for (i in 0 until COLUMN_COUNT - 1) {
                rowXCoords.add(i, i * rowWidth)
            }

            for (i in 0 until ROW_COUNT) {
                rowYCoords.add(i, i * rowHeight)
            }
        }

        if (dialerDrawable != null) {
            for (i in 0 until COLUMN_COUNT - 1) {
                val drawableX = rowXCoords[i] + iconMargin
                val drawableY = rowYCoords[COLUMN_COUNT - 1] + rowHeight - iconSize - iconMargin * 2
                dialerDrawable!!.setBounds(drawableX, drawableY, drawableX + iconSize, drawableY + iconSize)
                dialerDrawable!!.draw(canvas)
            }
        }
    }

    fun gridClicked(x: Float, y: Float) {

    }
}
