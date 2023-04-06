package com.simplemobiletools.launcher.models

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import com.simplemobiletools.launcher.helpers.ITEM_TYPE_ICON

// grid cells are from 0-5 by default. Icons and shortcuts occupy 1 slot only, widgets can be bigger
@Entity(tableName = "home_screen_grid_items", indices = [(Index(value = ["id"], unique = true))])
data class HomeScreenGridItem(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "page_id") var pageId: Long,
    @ColumnInfo(name = "left") var left: Int,
    @ColumnInfo(name = "top") var top: Int,
    @ColumnInfo(name = "right") var right: Int,
    @ColumnInfo(name = "bottom") var bottom: Int,
    @ColumnInfo(name = "package_name") var packageName: String,
    @ColumnInfo(name = "activity_name") var activityName: String, // needed at apps that create multiple icons at install, not just the launcher
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "type") var type: Int,
    @ColumnInfo(name = "class_name") var className: String,
    @ColumnInfo(name = "widget_id") var widgetId: Int,
    @ColumnInfo(name = "intent") var intent: String, // used at static and dynamic shortcuts on click
    @ColumnInfo(name = "shortcut_id") var shortcutId: String, // used at pinned shortcuts at startLauncher call
    @ColumnInfo(name = "icon") var icon: Bitmap? = null, // store images of pinned shortcuts, those cannot be retrieved after creating

    @Ignore var drawable: Drawable? = null,
    @Ignore var providerInfo: AppWidgetProviderInfo? = null, // used at widgets
    @Ignore var activityInfo: ActivityInfo? = null, // used at shortcuts
    @Ignore var widthCells: Int = 1,
    @Ignore var heightCells: Int = 1
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readValue(Long::class.java.classLoader) as? Long,
        parcel.readLong(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readParcelable(Bitmap::class.java.classLoader),
        null,
        parcel.readParcelable(AppWidgetProviderInfo::class.java.classLoader),
        parcel.readParcelable(ActivityInfo::class.java.classLoader),
        parcel.readInt(),
        parcel.readInt()
    )

    constructor() : this(null, -1, -1, -1, -1, -1, "", "", "", ITEM_TYPE_ICON, "", -1, "", "", null, null, null, null, 1, 1)

    fun getWidthInCells() = if (right == -1 || left == -1) {
        widthCells
    } else {
        right - left + 1
    }

    fun getHeightInCells() = if (bottom == -1 || top == -1) {
        heightCells
    } else {
        bottom - top + 1
    }

    fun getItemIdentifier() = "$packageName/$activityName"

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeValue(id)
        dest.writeLong(pageId)
        dest.writeInt(left)
        dest.writeInt(top)
        dest.writeInt(right)
        dest.writeInt(bottom)
        dest.writeString(packageName)
        dest.writeString(activityName)
        dest.writeString(title)
        dest.writeInt(type)
        dest.writeString(className)
        dest.writeInt(widgetId)
        dest.writeString(intent)
        dest.writeString(shortcutId)
        dest.writeParcelable(icon, flags)
        dest.writeParcelable(providerInfo, flags)
        dest.writeParcelable(activityInfo, flags)
        dest.writeInt(widthCells)
        dest.writeInt(heightCells)
    }

    companion object CREATOR : Parcelable.Creator<HomeScreenGridItem> {
        override fun createFromParcel(parcel: Parcel): HomeScreenGridItem {
            return HomeScreenGridItem(parcel)
        }

        override fun newArray(size: Int): Array<HomeScreenGridItem?> {
            return arrayOfNulls(size)
        }
    }
}


