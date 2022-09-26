package com.simplemobiletools.launcher.models

import android.graphics.drawable.Drawable

data class AppWidget(
    var appPackageName: String,
    var appTitle: String,
    val appIcon: Drawable?,
    val widgetTitle: String,
    val widgetPreviewImage: Drawable?,
    var widthCells: Int,
    val heightCells: Int,
    val isShortcut: Boolean,
    val shortClassName: String  // identifier to know which app widget are we using
) : WidgetsListItem() {
    override fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getStringToCompare(): String {
        return copy(appIcon = null, widgetPreviewImage = null).toString()
    }
}
