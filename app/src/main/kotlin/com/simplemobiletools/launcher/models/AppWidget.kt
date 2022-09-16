package com.simplemobiletools.launcher.models

import android.graphics.drawable.Drawable

data class AppWidget(
    var appPackageName: String,
    var appTitle: String,
    val appIcon: Drawable,
    val widgetTitle: String,
    val widgetPreviewImage: Drawable?,
    var widthTiles: Int,
    val heightTiles: Int,
    val isShortcut: Boolean
) : WidgetsListItem()
