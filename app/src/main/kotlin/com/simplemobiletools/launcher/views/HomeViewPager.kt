package com.simplemobiletools.launcher.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.fragment.app.FragmentActivity
import androidx.viewpager.widget.ViewPager
import com.simplemobiletools.launcher.adapters.HomeScreenPagerAdapter

class HomeViewPager(context: Context, attrs: AttributeSet) : ViewPager(context, attrs) {
    init {
        adapter = HomeScreenPagerAdapter((context as FragmentActivity).supportFragmentManager)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        listener?.onVpTouchEvent(ev)
        return super.onTouchEvent(ev)
    }

    private var listener: OnPagerGestureListener? = null

    fun setOnVerticalSwipeListener(listener: OnPagerGestureListener) {
        this.listener = listener
    }

    interface OnPagerGestureListener {
        fun onVpTouchEvent(event: MotionEvent?): Boolean
    }
}
