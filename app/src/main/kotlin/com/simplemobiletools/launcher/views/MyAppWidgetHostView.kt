package com.simplemobiletools.launcher.views

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.simplemobiletools.commons.extensions.performHapticFeedback

class MyAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var longPressHandler = Handler()
    private var hasLongPressed = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (hasLongPressed) {
            hasLongPressed = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> longPressHandler.removeCallbacksAndMessages(null)
        }

        return false
    }

    private val longPressRunnable = Runnable {
        longPressHandler.removeCallbacksAndMessages(null)
        hasLongPressed = true
        performHapticFeedback()
    }
}
