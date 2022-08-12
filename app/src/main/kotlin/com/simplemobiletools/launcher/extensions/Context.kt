package com.simplemobiletools.launcher.extensions

import android.content.Context
import com.simplemobiletools.commons.extensions.portrait
import com.simplemobiletools.launcher.R
import com.simplemobiletools.launcher.helpers.Config

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getColumnCount(): Int {
    return if (portrait) {
        resources.getInteger(R.integer.portrait_column_count)
    } else {
        resources.getInteger(R.integer.landscape_column_count)
    }
}
