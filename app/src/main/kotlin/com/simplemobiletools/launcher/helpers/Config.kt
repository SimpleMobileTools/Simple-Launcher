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

    var portraitHomeColumnCount: Int
        get() = prefs.getInt(PORTRAIT_HOME_COLUMN_COUNT, COLUMN_COUNT)
        set(portraitHomeColumnCount) = prefs.edit().putInt(PORTRAIT_HOME_COLUMN_COUNT, portraitHomeColumnCount).apply()

    var landscapeHomeColumnCount: Int
        get() = prefs.getInt(LANDSCAPE_HOME_COLUMN_COUNT, COLUMN_COUNT)
        set(landscapeHomeColumnCount) = prefs.edit().putInt(LANDSCAPE_HOME_COLUMN_COUNT, landscapeHomeColumnCount).apply()

    var portraitHomeRowCount: Int
        get() = prefs.getInt(PORTRAIT_HOME_ROW_COUNT, ROW_COUNT)
        set(portraitHomeRowCount) = prefs.edit().putInt(PORTRAIT_HOME_ROW_COUNT, portraitHomeRowCount).apply()

    var landscapeHomeRowCount: Int
        get() = prefs.getInt(LANDSCAPE_HOME_ROW_COUNT, ROW_COUNT)
        set(landscapeHomeRowCount) = prefs.edit().putInt(LANDSCAPE_HOME_ROW_COUNT, landscapeHomeRowCount).apply()
}
