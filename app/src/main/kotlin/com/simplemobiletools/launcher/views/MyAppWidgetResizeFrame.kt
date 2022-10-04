package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.extensions.getTileCount
import com.simplemobiletools.launcher.helpers.COLUMN_COUNT
import com.simplemobiletools.launcher.helpers.MAX_CLICK_DURATION
import com.simplemobiletools.launcher.helpers.ROW_COUNT
import com.simplemobiletools.launcher.models.HomeScreenGridItem

@SuppressLint("ViewConstructor")
class MyAppWidgetResizeFrame(context: Context, attrs: AttributeSet, defStyle: Int) : RelativeLayout(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private var resizeWidgetLinePaint: Paint
    private var resizeWidgetLineDotPaint: Paint
    private var actionDownCoords = PointF()
    private var actionDownMS = 0L
    private var frameRect = Rect(0, 0, 0, 0)    // coords in pixels
    private var cellsRect = Rect(0, 0, 0, 0)    // cell IDs like 0, 1, 2..
    private var cellWidth = 0
    private var cellHeight = 0
    private var minResizeWidthCells = 1
    private var minResizeHeightCells = 1
    private val occupiedCells = ArrayList<Pair<Int, Int>>()
    private var resizedItem: HomeScreenGridItem? = null
    private var sideMargins = Rect()
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

    fun updateFrameCoords(
        coords: Rect,
        cellWidth: Int,
        cellHeight: Int,
        sideMargins: Rect,
        gridItem: HomeScreenGridItem,
        allGridItems: ArrayList<HomeScreenGridItem>
    ) {
        frameRect = coords
        cellsRect = Rect(gridItem.left, gridItem.top, gridItem.right, gridItem.bottom)
        this.cellWidth = cellWidth
        this.cellHeight = cellHeight
        this.sideMargins = sideMargins
        this.resizedItem = gridItem
        val providerInfo = gridItem.providerInfo ?: AppWidgetManager.getInstance(context)!!.installedProviders.firstOrNull {
            it.provider.className == gridItem.className
        } ?: return

        minResizeWidthCells = Math.min(COLUMN_COUNT, context.getTileCount(providerInfo.minResizeWidth))
        minResizeHeightCells = Math.min(ROW_COUNT, context.getTileCount(providerInfo.minResizeHeight))
        redrawFrame()

        occupiedCells.clear()
        allGridItems.forEach { item ->
            for (xCell in item.left until item.right) {
                for (yCell in item.top until item.bottom) {
                    occupiedCells.add(Pair(xCell, yCell))
                }
            }
        }
    }

    private fun redrawFrame() {
        layoutParams.width = frameRect.right - frameRect.left
        layoutParams.height = frameRect.bottom - frameRect.top
        x = frameRect.left.toFloat()
        y = frameRect.top.toFloat()
        requestLayout()
    }

    private fun cellChanged() {

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
                dragDirection = DRAGGING_NONE
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
            MotionEvent.ACTION_MOVE -> {
                val minWidth = minResizeWidthCells * cellWidth
                val minHeight = minResizeHeightCells * cellHeight
                when (dragDirection) {
                    DRAGGING_LEFT -> {
                        val newWidth = frameRect.right - event.rawX.toInt()
                        val wantedLeft = if (newWidth >= minWidth) {
                            event.rawX.toInt()
                        } else {
                            frameRect.right - minWidth
                        }

                        val closestCellX = roundToClosestMultiplyOfNumber(wantedLeft - sideMargins.left, cellWidth) / cellWidth
                        var areAllCellsFree = true
                        for (yCell in cellsRect.top until cellsRect.bottom) {
                            if (occupiedCells.contains(Pair(closestCellX, yCell))) {
                                areAllCellsFree = false
                            }
                        }

                        if (areAllCellsFree && cellsRect.left != closestCellX) {
                            cellsRect.left = closestCellX
                            cellChanged()
                        }
                        frameRect.left = wantedLeft
                    }
                    DRAGGING_TOP -> {
                        val newHeight = frameRect.bottom - event.rawY.toInt()
                        if (newHeight >= minHeight) {
                            frameRect.top = event.rawY.toInt()
                        } else {
                            frameRect.top = frameRect.bottom - minHeight
                        }
                    }
                    DRAGGING_RIGHT -> {
                        val newWidth = event.rawX.toInt() - frameRect.left
                        if (newWidth >= minWidth) {
                            frameRect.right = event.rawX.toInt()
                        } else {
                            frameRect.right = frameRect.left + minWidth
                        }
                    }
                    DRAGGING_BOTTOM -> {
                        val newHeight = event.rawY.toInt() - frameRect.top
                        if (newHeight >= minHeight) {
                            frameRect.bottom = event.rawY.toInt()
                        } else {
                            frameRect.bottom = frameRect.top + minHeight
                        }
                    }
                }

                if (dragDirection != DRAGGING_NONE) {
                    redrawFrame()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragDirection == DRAGGING_NONE) {
                    onClickListener?.invoke()
                } else if (System.currentTimeMillis() - actionDownMS < MAX_CLICK_DURATION) {
                    onClickListener?.invoke()
                    dragDirection = DRAGGING_NONE
                } else {
                    when (dragDirection) {
                        DRAGGING_LEFT -> {
                            val wantedLeft = roundToClosestMultiplyOfNumber(frameRect.left - sideMargins.left, cellWidth)
                            val wantedLeftCellX = wantedLeft / cellWidth
                            var areAllCellsFree = true
                            for (yCell in cellsRect.top until cellsRect.bottom) {
                                if (occupiedCells.contains(Pair(wantedLeftCellX, yCell))) {
                                    areAllCellsFree = false
                                }
                            }

                            if (areAllCellsFree) {
                                frameRect.left = wantedLeft + sideMargins.left
                                cellsRect.left = wantedLeftCellX
                            } else {
                                frameRect.left = cellsRect.left * cellWidth
                            }
                        }
                        DRAGGING_TOP -> frameRect.top = roundToClosestMultiplyOfNumber(frameRect.top - sideMargins.top, cellHeight) + sideMargins.top
                        DRAGGING_RIGHT -> frameRect.right = roundToClosestMultiplyOfNumber(frameRect.right - sideMargins.left, cellWidth) + sideMargins.left
                        DRAGGING_BOTTOM -> frameRect.bottom = roundToClosestMultiplyOfNumber(frameRect.bottom - sideMargins.top, cellHeight) + sideMargins.top
                    }
                    redrawFrame()
                }
            }
        }

        return true
    }

    private fun roundToClosestMultiplyOfNumber(value: Int, number: Int): Int {
        return number * (Math.round(Math.abs(value / number.toDouble()))).toInt()
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
