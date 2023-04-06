package com.simplemobiletools.launcher.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import android.os.Parcel
import android.os.Parcelable

@Entity(tableName = "home_screen_pages", indices = [(Index(value = ["id"], unique = true))])
data class HomeScreenPage(
    @PrimaryKey(autoGenerate = true) var id: Long? = null,
    val position: Int = 0
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readValue(Long::class.java.classLoader) as? Long,
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(id)
        parcel.writeInt(position)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<HomeScreenPage> {
        override fun createFromParcel(parcel: Parcel): HomeScreenPage {
            return HomeScreenPage(parcel)
        }

        override fun newArray(size: Int): Array<HomeScreenPage?> {
            return arrayOfNulls(size)
        }
    }
}
