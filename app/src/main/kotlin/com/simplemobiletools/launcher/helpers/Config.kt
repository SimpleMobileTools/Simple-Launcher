package com.simplemobiletools.launcher.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig
import com.simplemobiletools.launcher.R

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var wasHomeScreenInit: Boolean
        get() = prefs.getBoolean(WAS_HOME_SCREEN_INIT, false)
        set(wasHomeScreenInit) = prefs.edit().putBoolean(WAS_HOME_SCREEN_INIT, wasHomeScreenInit).apply()

    var drawerColumnCount: Int
        get() = prefs.getInt(DRAWER_COLUMN_COUNT, context.resources.getInteger(R.integer.portrait_column_count))
        set(drawerColumnCount) = prefs.edit().putInt(DRAWER_COLUMN_COUNT, drawerColumnCount).apply()
}
