package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.simplemobiletools.launcher.R

@SuppressLint("ViewConstructor")
class MyAppWidgetResizeFrame(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var resizeWidgetLinePaint: Paint

    init {
        background = ColorDrawable(Color.TRANSPARENT)

        resizeWidgetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = context.resources.getDimension(R.dimen.tiny_margin)
            style = Paint.Style.STROKE
        }
    }

    fun updateFrameCoords(coords: Rect) {
        layoutParams.width = coords.right - coords.left
        layoutParams.height = coords.bottom - coords.top
        x = coords.left.toFloat()
        y = coords.top.toFloat()
        requestLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (x != 0f || y != 0f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), resizeWidgetLinePaint)
        }
    }
}
