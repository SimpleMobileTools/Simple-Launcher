package com.simplemobiletools.launcher.views

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.simplemobiletools.commons.extensions.performHapticFeedback

class MyAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var MAX_ALLOWED_MOVE = 10
    private var longPressHandler = Handler()
    private var actionDownCoords = PointF()
    private var currentCoords = PointF()
    var hasLongPressed = false
    var longPressListener: ((x: Float, y: Float) -> Unit)? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
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
            }
            MotionEvent.ACTION_MOVE -> {
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> longPressHandler.removeCallbacksAndMessages(null)
        }

        return false
    }

    private val longPressRunnable = Runnable {
        if (Math.abs(actionDownCoords.x - currentCoords.x) < MAX_ALLOWED_MOVE && Math.abs(actionDownCoords.y - currentCoords.y) < MAX_ALLOWED_MOVE) {
            longPressHandler.removeCallbacksAndMessages(null)
            hasLongPressed = true
            longPressListener?.invoke(actionDownCoords.x, actionDownCoords.y)
            performHapticFeedback()
        }
    }
}
