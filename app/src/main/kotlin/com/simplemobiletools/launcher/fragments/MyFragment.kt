package com.simplemobiletools.launcher.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.simplemobiletools.launcher.activities.MainActivity

abstract class MyFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    protected var activity: MainActivity? = null

    abstract fun setupFragment(activity: MainActivity)
}
