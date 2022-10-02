package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.helpers.MAX_ALLOWED_MOVE_PX

@SuppressLint("ViewConstructor")
class MyAppWidgetResizeFrame(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var resizeWidgetLinePaint: Paint
    private var actionDownCoords = PointF()
    private var actionDownMS = 0L
    private var MAX_CLICK_DURATION = 150
    var onClickListener: (() -> Unit)? = null

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

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                actionDownMS = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (System.currentTimeMillis() - actionDownMS < MAX_CLICK_DURATION &&
                    Math.abs(actionDownCoords.x - event.rawX) < MAX_ALLOWED_MOVE_PX &&
                    Math.abs(actionDownCoords.y - event.rawY) < MAX_ALLOWED_MOVE_PX
                ) {
                    onClickListener?.invoke()
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (x != 0f || y != 0f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), resizeWidgetLinePaint)
        }
    }
}
