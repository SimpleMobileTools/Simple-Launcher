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

    var portraitDrawerColumnCount: Int
        get() = prefs.getInt(PORTRAIT_DRAWER_COLUMN_COUNT, context.resources.getInteger(R.integer.portrait_column_count))
        set(portraitDrawerColumnCount) = prefs.edit().putInt(PORTRAIT_DRAWER_COLUMN_COUNT, portraitDrawerColumnCount).apply()

    var landscapeDrawerColumnCount: Int
        get() = prefs.getInt(LANDSCAPE_DRAWER_COLUMN_COUNT, context.resources.getInteger(R.integer.landscape_column_count))
        set(landscapeDrawerColumnCount) = prefs.edit().putInt(LANDSCAPE_DRAWER_COLUMN_COUNT, landscapeDrawerColumnCount).apply()
}
