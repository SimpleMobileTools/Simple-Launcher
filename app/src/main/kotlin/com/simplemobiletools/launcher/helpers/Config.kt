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

    var wereDefaultAppLabelsFilled: Boolean
        get() = prefs.getBoolean(WERE_DEFAULT_APP_LABELS_FILLED, false)
        set(wereDefaultAppLabelsFilled) = prefs.edit().putBoolean(WERE_DEFAULT_APP_LABELS_FILLED, wereDefaultAppLabelsFilled).apply()
}
