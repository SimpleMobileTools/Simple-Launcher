package com.simplemobiletools.launcher.interfaces

import com.simplemobiletools.launcher.models.AppLauncher

interface AllAppsListener {
    fun onAppLauncherLongPressed(x: Float, y: Float, appLauncher: AppLauncher)
}
