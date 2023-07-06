package com.simplemobiletools.launcher.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)

        // Config keys
        private const val ROW_COUNT = "row_count"
        private const val COLUMN_COUNT = "column_count"
    }

    var wasHomeScreenInit: Boolean
        get() = prefs.getBoolean(WAS_HOME_SCREEN_INIT, false)
        set(wasHomeScreenInit) = prefs.edit().putBoolean(WAS_HOME_SCREEN_INIT, wasHomeScreenInit).apply()

    var rowCount: Int
        get() = prefs.getInt(ROW_COUNT, DEFAULT_ROW_COUNT)
        set(rows) = prefs.edit().putInt(ROW_COUNT, rows).apply()

    var columnCount: Int
        get() = prefs.getInt(COLUMN_COUNT, DEFAULT_COLUMN_COUNT)
        set(rows) = prefs.edit().putInt(COLUMN_COUNT, rows).apply()
}
