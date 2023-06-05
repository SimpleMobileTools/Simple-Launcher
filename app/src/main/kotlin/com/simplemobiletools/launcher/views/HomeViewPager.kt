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

    private var isPagingEnabled = true
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return this.isPagingEnabled && super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        return this.isPagingEnabled && super.onInterceptTouchEvent(event)
    }

    fun setPagingEnabled(enabled: Boolean) {
        this.isPagingEnabled = enabled
    }
}
