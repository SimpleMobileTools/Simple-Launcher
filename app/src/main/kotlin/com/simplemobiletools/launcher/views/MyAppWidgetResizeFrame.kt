package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.helpers.MAX_CLICK_DURATION

@SuppressLint("ViewConstructor")
class MyAppWidgetResizeFrame(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var resizeWidgetLinePaint: Paint
    private var resizeWidgetLineDotPaint: Paint
    private var actionDownCoords = PointF()
    private var actionDownMS = 0L
    private val lineDotRadius = context.resources.getDimension(R.dimen.resize_frame_dot_radius)
    private val MAX_TOUCH_LINE_DISTANCE = lineDotRadius * 5     // how close we have to be to the widgets side to drag it
    var onClickListener: (() -> Unit)? = null

    private val DRAGGING_NONE = 0
    private val DRAGGING_LEFT = 1
    private val DRAGGING_TOP = 2
    private val DRAGGING_RIGHT = 3
    private val DRAGGING_BOTTOM = 4
    private var dragDirection = DRAGGING_NONE

    init {
        background = ColorDrawable(Color.TRANSPARENT)

        resizeWidgetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = context.resources.getDimension(R.dimen.tiny_margin)
            style = Paint.Style.STROKE
        }

        resizeWidgetLineDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
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
                if (event.x < MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_LEFT
                } else if (event.y < MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_TOP
                } else if (event.x > width - MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_RIGHT
                } else if (event.y > height - MAX_TOUCH_LINE_DISTANCE) {
                    dragDirection = DRAGGING_BOTTOM
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragDirection == DRAGGING_NONE) {
                    onClickListener?.invoke()
                } else if (System.currentTimeMillis() - actionDownMS < MAX_CLICK_DURATION) {
                    onClickListener?.invoke()
                    dragDirection = DRAGGING_NONE
                }
            }
        }

        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (x != 0f || y != 0f) {
            canvas.drawRect(lineDotRadius, lineDotRadius, width.toFloat() - lineDotRadius, height.toFloat() - lineDotRadius, resizeWidgetLinePaint)
            canvas.drawCircle(lineDotRadius, height / 2f, lineDotRadius, resizeWidgetLineDotPaint)
            canvas.drawCircle(width / 2f, lineDotRadius, lineDotRadius, resizeWidgetLineDotPaint)
            canvas.drawCircle(width - lineDotRadius, height / 2f, lineDotRadius, resizeWidgetLineDotPaint)
            canvas.drawCircle(width / 2f, height - lineDotRadius, lineDotRadius, resizeWidgetLineDotPaint)
        }
    }
}
