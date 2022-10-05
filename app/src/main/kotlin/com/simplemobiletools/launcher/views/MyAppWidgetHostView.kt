package com.simplemobiletools.launcher.views

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.simplemobiletools.commons.extensions.performHapticFeedback
import com.simplemobiletools.launcher.helpers.MAX_ALLOWED_MOVE_PX

class MyAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var longPressHandler = Handler()
    private var actionDownCoords = PointF()
    private var currentCoords = PointF()
    private var actionDownMS = 0L
    var hasLongPressed = false
    var ignoreTouches = false
    var longPressListener: ((x: Float, y: Float) -> Unit)? = null
    var onIgnoreInterceptedListener: (() -> Unit)? = null       // let the home grid react on swallowed clicks, for example by hiding the widget resize frame

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (ignoreTouches) {
            onIgnoreInterceptedListener?.invoke()
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (ignoreTouches || event == null) {
            return true
        }

        if (hasLongPressed) {
            hasLongPressed = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                actionDownMS = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacksAndMessages(null)
            }
        }

        return false
    }

    private val longPressRunnable = Runnable {
        if (Math.abs(actionDownCoords.x - currentCoords.x) < MAX_ALLOWED_MOVE_PX && Math.abs(actionDownCoords.y - currentCoords.y) < MAX_ALLOWED_MOVE_PX) {
            longPressHandler.removeCallbacksAndMessages(null)
            hasLongPressed = true
            longPressListener?.invoke(actionDownCoords.x, actionDownCoords.y)
        }
    }
}
