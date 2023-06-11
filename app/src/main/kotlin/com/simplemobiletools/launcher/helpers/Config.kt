package com.simplemobiletools.launcher.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var wasHomeScreenInit: Boolean
        get() = prefs.getBoolean(WAS_HOME_SCREEN_INIT, false)
        set(wasHomeScreenInit) = prefs.edit().putBoolean(WAS_HOME_SCREEN_INIT, wasHomeScreenInit).apply()
    var columnCount: Int
        get() = prefs.getInt(COLUMN_COUNT_PREF, 5)
        set(count) = prefs.edit().putInt(COLUMN_COUNT_PREF, count).apply()
    var rowCount: Int
        get() = prefs.getInt(ROW_COUNT_PREF, 5)
        set(count) = prefs.edit().putInt(ROW_COUNT_PREF, count).apply()
    var homeGrid: Int
        get() = prefs.getInt(HOME_GRID_PREF, 5)
        set(count) = prefs.edit().putInt(HOME_GRID_PREF, count).apply()
}
